package com.yuelei.appforge.ai.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/** A separate stateless reviewer, with no tools/memory and no request/response logging. */
@Component
public final class LlmPromptReviewer implements PromptReviewer {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .FAIL_ON_TRAILING_TOKENS)
                    .enable(
                            com.fasterxml.jackson.core.JsonParser.Feature
                                    .STRICT_DUPLICATE_DETECTION);
    private final Supplier<ChatModel> factory;
    private volatile ChatModel model;
    private final String instruction;

    @Autowired
    public LlmPromptReviewer(GuardrailProperties properties, Environment environment)
            throws java.io.IOException {
        this(
                () -> {
                    String key = setting(properties.getApiKey(), environment, "api-key");
                    String name = setting(properties.getModelName(), environment, "model-name");
                    String url = setting(properties.getBaseUrl(), environment, "base-url");
                    if (key == null
                            || key.isBlank()
                            || name == null
                            || name.isBlank()
                            || url == null
                            || url.isBlank())
                        throw new IllegalStateException("Review model is not configured");
                    return OpenAiChatModel.builder()
                            .httpClientBuilder(
                                    JdkHttpClient.builder().connectTimeout(Duration.ofSeconds(5)))
                            .apiKey(key)
                            .baseUrl(url)
                            .modelName(name)
                            .reasoningEffort("none")
                            .responseFormat("json_object")
                            .maxTokens(2048)
                            .timeout(
                                    Duration.ofSeconds(
                                            Math.clamp(properties.getTimeoutSeconds(), 1, 30)))
                            .maxRetries(0)
                            .logRequests(false)
                            .logResponses(false)
                            .build();
                });
    }

    LlmPromptReviewer(Supplier<ChatModel> factory) throws java.io.IOException {
        this.factory = factory;
        instruction =
                new ClassPathResource("prompt/prompt-safety-review.txt")
                        .getContentAsString(StandardCharsets.UTF_8);
    }

    private static String setting(String override, Environment env, String name) {
        return override == null || override.isBlank()
                ? env.getProperty("langchain4j.open-ai.chat-model." + name)
                : override;
    }

    private ChatModel model() {
        if (model == null) {
            synchronized (this) {
                if (model == null) model = factory.get();
            }
        }
        return model;
    }

    @Override
    public Review review(String message, String context) {
        try {
            String data = JSON.writeValueAsString(Map.of("message", message, "context", context));
            String text =
                    model().chat(
                                    ChatRequest.builder()
                                            .messages(
                                                    SystemMessage.from(instruction),
                                                    UserMessage.from(data))
                                            .build())
                            .aiMessage()
                            .text();
            if (text == null || text.length() > 16_000) throw new IllegalArgumentException();
            JsonNode json = JSON.readTree(text);
            if (!json.isObject()
                    || !json.path("action").isTextual()
                    || !json.path("rules").isArray()
                    || !json.path("redactions").isArray()
                    || json.path("rules").size() > 12
                    || json.path("redactions").size() > 20) throw new IllegalArgumentException();
            Action action = Action.valueOf(json.get("action").textValue());
            List<Rule> rules = new ArrayList<>();
            for (var rule : json.get("rules")) {
                if (!rule.isTextual()) throw new IllegalArgumentException();
                Rule value = Rule.valueOf(rule.textValue());
                if (!Set.of(
                                Rule.PROMPT_INJECTION,
                                Rule.UNSAFE_CONTENT,
                                Rule.PERSONAL_DATA,
                                Rule.SECRET_REDACTION,
                                Rule.OUT_OF_SCOPE,
                                Rule.SEMANTIC_REWRITE)
                        .contains(value)) throw new IllegalArgumentException();
                rules.add(value);
            }
            List<Redaction> redactions = new ArrayList<>();
            for (var item : json.get("redactions")) {
                if (!item.path("field").isTextual()
                        || !item.path("text").isTextual()
                        || !item.path("kind").isTextual()) throw new IllegalArgumentException();
                redactions.add(
                        new Redaction(
                                item.get("field").textValue(),
                                item.get("text").textValue(),
                                Kind.valueOf(item.get("kind").textValue())));
            }
            if (action != Action.REWRITE && !redactions.isEmpty())
                throw new IllegalArgumentException();
            if (action == Action.ALLOW && !rules.isEmpty()) throw new IllegalArgumentException();
            return new Review(action, List.copyOf(rules), List.copyOf(redactions));
        } catch (Exception invalidOrUnavailable) {
            // Do not propagate provider responses, URLs or credentials into application logs/SSE.
            throw new IllegalStateException("Safety review unavailable");
        }
    }
}
