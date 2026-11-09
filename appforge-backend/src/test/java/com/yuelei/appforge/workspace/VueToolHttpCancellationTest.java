package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.yuelei.appforge.workspace.vue.*;

import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

class VueToolHttpCancellationTest {
    @Test
    void officialPartialToolCallCallbackAllowsActualHttpConnectionToBeClosed() throws Exception {
        var closed = new CountDownLatch(1);
        var executor = Executors.newCachedThreadPool();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    String data =
                            "data:"
                                + " {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":\"function\",\"function\":{\"name\":\"writeFile\",\"arguments\":\""
                                + " \"}}]}}]}\n\n";
                    try (var out = exchange.getResponseBody()) {
                        for (int i = 0; i < 200; i++) {
                            out.write(data.getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            try {
                                Thread.sleep(25);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    } catch (IOException expected) {
                        closed.countDown();
                    }
                });
        server.start();
        try (var task = new ProjectTask(Map.of(), e -> {})) {
            var client =
                    OpenAiStreamingChatModel.builder()
                            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                            .apiKey("local-test-key")
                            .modelName("local-test")
                            .httpClientBuilder(SpringRestClient.builder())
                            .build();
            var fixture = new VueProjectTest();
            String first =
                    fixture.runner(client, fixture.successfulBuilder()).stream(1, 1, "创建", task)
                            .take(1)
                            .blockLast(Duration.ofSeconds(8));
            assertNotNull(first);
            assertTrue(first.contains("tool_request"));
            assertTrue(task.isCancelled());
            assertTrue(closed.await(5, TimeUnit.SECONDS), "停止必须关闭实际 SDK HTTP 流");
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
