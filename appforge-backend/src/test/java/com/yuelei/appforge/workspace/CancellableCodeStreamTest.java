package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** 在本地 HTTP SSE 服务验证真实连接关闭，不调用付费模型。 */
class CancellableCodeStreamTest {
    HttpServer server;
    ExecutorService executor;
    CancellableCodeStream stream;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        stream = new CancellableCodeStream();
        ReflectionTestUtils.setField(
                stream, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(stream, "apiKey", "local-test-key");
        ReflectionTestUtils.setField(stream, "model", "local-test-model");
        ReflectionTestUtils.setField(stream, "maxTokens", 8192);
        ReflectionTestUtils.setField(stream, "reasoningEffort", "none");
    }

    @AfterEach
    void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void parsesUtf8ChunksAndRequiresNormalFinish() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    String data =
                            "data:"
                                + " {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}\n\n"
                                + "data:"
                                + " {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                                + "data: [DONE]\n\n";
                    try (var out = exchange.getResponseBody()) {
                        for (byte value : data.getBytes(StandardCharsets.UTF_8)) {
                            out.write(value);
                            out.flush();
                        }
                    }
                });
        server.start();
        assertEquals(
                "你好",
                String.join(
                        "",
                        stream.stream("创建", CodeGenTypeEnum.HTML)
                                .collectList()
                                .block(Duration.ofSeconds(5))));
    }

    @Test
    void cancellationClosesTheActualProviderConnection() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    byte[] data =
                            "data: {\"choices\":[{\"delta\":{\"content\":\"分片\"}}]}\n\n"
                                    .getBytes(StandardCharsets.UTF_8);
                    try (var out = exchange.getResponseBody()) {
                        for (int i = 0; i < 100; i++) {
                            out.write(data);
                            out.flush();
                            try {
                                Thread.sleep(30);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    } catch (IOException expected) {
                        closed.countDown();
                    }
                });
        server.start();
        stream.stream("创建", CodeGenTypeEnum.HTML).take(1).blockLast(Duration.ofSeconds(5));
        assertTrue(closed.await(5, TimeUnit.SECONDS), "取消订阅必须关闭模型 HTTP 流");
    }

    @Test
    void truncatedGenerationIsAnErrorEvenWhenProviderSendsDone() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        out.write(
                                "data: {\"choices\":[{\"delta\":{\"content\":\"半截\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n"
                                        .getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        assertThrows(
                BusinessException.class,
                () -> stream.stream("创建", CodeGenTypeEnum.HTML).blockLast(Duration.ofSeconds(5)));
    }
}
