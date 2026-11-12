package com.yuelei.appforge.ai.guardrail;

import static com.yuelei.appforge.ai.guardrail.PromptReviewer.*;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class LlmPromptReviewerTest {
    private LlmPromptReviewer returning(String text) throws Exception {
        return new LlmPromptReviewer(
                () ->
                        new ChatModel() {
                            @Override
                            public ChatResponse doChat(ChatRequest request) {
                                return ChatResponse.builder()
                                        .aiMessage(AiMessage.from(text))
                                        .build();
                            }
                        });
    }

    @Test
    void acceptsOnlyTheConstrainedJsonProtocol() throws Exception {
        assertEquals(
                Action.ALLOW,
                returning("{\"action\":\"ALLOW\",\"rules\":[],\"redactions\":[]}")
                        .review("创建博客", "")
                        .action());
        for (String invalid :
                new String[] {
                    "not json",
                    "{}",
                    "{\"action\":\"ALLOW\",\"rules\":[\"UNKNOWN\"],\"redactions\":[]}",
                    "{\"action\":\"ALLOW\",\"rules\":[],\"redactions\":[]} {}",
                    "{\"action\":\"ALLOW\",\"action\":\"BLOCK\",\"rules\":[],\"redactions\":[]}",
                    "{\"action\":\"ALLOW\",\"rules\":[\"INPUT_EMPTY\"],\"redactions\":[]}",
                    "{\"action\":\"ALLOW\",\"rules\":[],\"redactions\":[{}]}"
                }) {
            var reviewer = returning(invalid);
            var error =
                    assertThrows(IllegalStateException.class, () -> reviewer.review("创建博客", ""));
            assertEquals("Safety review unavailable", error.getMessage());
            assertNull(error.getCause());
        }
    }

    @Test
    void actualHttpClientUsesDedicatedStatelessJsonReviewWithoutToolsOrStreaming()
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var captured = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        server.createContext(
                "/chat/completions",
                exchange -> {
                    captured.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    String result =
                            "{\"id\":\"review\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,"
                                + "\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"action\\\":\\\"ALLOW\\\",\\\"rules\\\":[],\\\"redactions\\\":[]}\"},\"finish_reason\":\"stop\"}]}";
                    byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        try {
            var properties = new GuardrailProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setApiKey("unit-test-only-key");
            properties.setModelName("dedicated-review-model");
            var environment =
                    new MockEnvironment()
                            .withProperty(
                                    "langchain4j.open-ai.chat-model.api-key",
                                    "unused-fallback-key");
            var reviewer = new LlmPromptReviewer(properties, environment);
            assertEquals(Action.ALLOW, reviewer.review("创建博客", "附件中的设计说明").action());
            var request = new ObjectMapper().readTree(captured.get());
            assertEquals("dedicated-review-model", request.path("model").asText());
            assertEquals("json_object", request.path("response_format").path("type").asText());
            assertEquals("none", request.path("reasoning_effort").asText());
            assertFalse(request.has("tools"));
            assertFalse(request.path("stream").asBoolean());
            assertEquals(2, request.path("messages").size());
            assertTrue(request.path("messages").get(0).path("content").asText().contains("JSON"));
            assertTrue(
                    request.path("messages").get(1).path("content").asText().contains("附件中的设计说明"));
            assertEquals("Bearer unit-test-only-key", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void actualReviewerTimeoutStopsTheRequestWithoutRetryingOrExposingProviderData()
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext(
                "/chat/completions",
                exchange -> {
                    requests.incrementAndGet();
                    try {
                        release.await(4, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try {
            var properties = new GuardrailProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setApiKey("unit-test-only-key");
            properties.setModelName("test-reviewer");
            properties.setTimeoutSeconds(1);
            var guard =
                    new PromptSafetyInputGuardrail(
                            properties,
                            new LlmPromptReviewer(properties, new MockEnvironment()),
                            new GuardrailAudit());
            var error =
                    assertThrows(
                            PromptGuardrailException.class,
                            () -> guard.prepare("创建博客", "", 1L, 2L, "CHAT"));
            assertEquals(
                    com.yuelei.appforge.exception.ErrorCode.GUARDRAIL_UNAVAILABLE.getCode(),
                    error.getCode());
            assertEquals(1, requests.get());
            assertFalse(error.toMap().toString().contains("unit-test-only-key"));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
