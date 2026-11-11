package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.model.openai.*;
import dev.langchain4j.openai.spring.AutoConfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LangchainConfigurationTest {
    @Test
    void existingModelPropertyNamesStillCreateBothModelsWithoutCallingTheProvider() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AutoConfig.class))
                .withUserConfiguration(com.yuelei.appforge.config.StreamingHttpClientConfig.class)
                .withPropertyValues(
                        "langchain4j.open-ai.chat-model.api-key=local-test-key",
                        "langchain4j.open-ai.chat-model.base-url=http://127.0.0.1:9",
                        "langchain4j.open-ai.chat-model.model-name=local-test",
                        "langchain4j.open-ai.chat-model.response-format=json_object",
                        "langchain4j.open-ai.chat-model.strict-json-schema=true",
                        "langchain4j.open-ai.streaming-chat-model.api-key=local-test-key",
                        "langchain4j.open-ai.streaming-chat-model.base-url=http://127.0.0.1:9",
                        "langchain4j.open-ai.streaming-chat-model.model-name=local-test",
                        "langchain4j.open-ai.streaming-chat-model.max-tokens=8192",
                        "langchain4j.open-ai.streaming-chat-model.reasoning-effort=none")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertInstanceOf(
                                    dev.langchain4j.http.client.jdk.JdkHttpClientBuilder.class,
                                    context.getBean("openAiStreamingChatModelHttpClientBuilder"));
                            assertEquals(1, context.getBeansOfType(OpenAiChatModel.class).size());
                            assertEquals(
                                    1,
                                    context.getBeansOfType(OpenAiStreamingChatModel.class).size());
                            assertEquals(
                                    8192,
                                    context.getBean(OpenAiStreamingChatModel.class)
                                            .defaultRequestParameters()
                                            .maxOutputTokens());
                        });
    }
}
