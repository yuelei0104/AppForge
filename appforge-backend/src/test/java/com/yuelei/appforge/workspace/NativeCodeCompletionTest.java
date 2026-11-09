package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.*;

import com.sun.net.httpserver.HttpServer;
import com.yuelei.appforge.core.parser.MultiFileCodeParser;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 真实本地 HTTP SSE + H2 版本保存，复现模型只返回 HTML 的结果。 */
class NativeCodeCompletionTest {
    static final String HTML =
            "<!DOCTYPE html><html><head><link rel='stylesheet'"
                    + " href='style.css'></head><body><h1>晓风博客</h1><input id='searchInput'><div"
                    + " id='articleList'></div><script src='script.js'></script></body></html>";
    static final String FIRST = "```html\n" + HTML + "\n```";
    static final String CSS = "body{color:#123456} #articleList{display:grid}";
    static final String JS = "document.getElementById('articleList').textContent='示例文章';";
    static final String REPAIR = "```css\n" + CSS + "\n```\n```javascript\n" + JS + "\n```";
    @TempDir Path root;
    HttpServer server;
    ExecutorService executor;
    CancellableCodeStream stream;
    AtomicInteger calls;
    List<JSONObject> requests;

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
        calls = new AtomicInteger();
        requests = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    String event(String content, String finish) {
        return "data: "
                + JSONUtil.toJsonStr(
                        Map.of(
                                "choices",
                                List.of(
                                        Map.of(
                                                "delta",
                                                Map.of("content", content),
                                                "finish_reason",
                                                finish))))
                + "\n\n";
    }

    void serve(String... responses) {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    requests.add(
                            JSONUtil.parseObj(
                                    new String(
                                            exchange.getRequestBody().readAllBytes(),
                                            StandardCharsets.UTF_8)));
                    int index = calls.getAndIncrement();
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        String response = responses[Math.min(index, responses.length - 1)];
                        out.write(
                                (event(response, "stop") + "data: [DONE]\n\n")
                                        .getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
    }

    String generated(Flux<String> source) {
        return String.join("", source.collectList().block(Duration.ofSeconds(8)));
    }

    AppWorkspaceTest workspace() {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        return f;
    }

    @Test
    void htmlOnlyReplyIsCompletedBeforeOneSuccessfulVersionIsSaved() {
        serve(FIRST, REPAIR);
        var f = workspace();
        var source = stream.stream("个人博客，支持搜索与示例文章", CodeGenTypeEnum.MULTI_FILE);
        f.generation(source)
                .generate(f.workspace.requireApp(f.id), "创建博客")
                .blockLast(Duration.ofSeconds(8));
        var saved = f.workspace.files(f.id, 1);
        assertEquals(CSS, saved.get("style.css"));
        assertEquals(JS, saved.get("script.js"));
        assertTrue(saved.get("index.html").contains("searchInput"));
        assertEquals(1, f.workspace.versions(f.id, f.owner).size());
        assertEquals("SUCCEEDED", f.workspace.requireApp(f.id).getGenerationStatus());
        assertEquals(2, calls.get());
        assertTrue(
                requests.get(0)
                        .getJSONArray("messages")
                        .getJSONObject(0)
                        .getStr("content")
                        .contains("三个核心文件"));
        var repairMessages = requests.get(1).getJSONArray("messages");
        assertTrue(repairMessages.getJSONObject(0).getStr("content").contains("不返回 HTML"));
        assertTrue(repairMessages.getJSONObject(1).getStr("content").contains(HTML));
        assertTrue(repairMessages.getJSONObject(1).getStr("content").contains("个人博客，支持搜索与示例文章"));
    }

    @Test
    void completeReplyDoesNotMakeAnotherModelRequest() {
        serve(FIRST + "\n" + REPAIR);
        generated(stream.stream("博客", CodeGenTypeEnum.MULTI_FILE));
        assertEquals(1, calls.get());
    }

    @Test
    void emptyInitialCodeBlocksDoNotHideTheCompletedFiles() {
        serve(FIRST + "\n```css\n \n```\n```javascript\n\n```", REPAIR);
        var files =
                AppGenerationService.parse(
                        generated(stream.stream("博客", CodeGenTypeEnum.MULTI_FILE)), "multi_file");
        assertEquals(CSS, files.get("style.css"));
        assertEquals(JS, files.get("script.js"));
        assertEquals(2, calls.get());
    }

    @Test
    void eachSubscriptionHasItsOwnCompletionBuffer() {
        serve(FIRST, REPAIR, FIRST, REPAIR);
        var source = stream.stream("博客", CodeGenTypeEnum.MULTI_FILE);
        assertEquals(generated(source), generated(source));
        assertEquals(4, calls.get());
    }

    @Test
    void truncatedRepairFailsWithoutTryingAgainOrSavingAVersion() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    int index = calls.getAndIncrement();
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        out.write(
                                (event(index == 0 ? FIRST : REPAIR, index == 0 ? "stop" : "length")
                                                + "data: [DONE]\n\n")
                                        .getBytes(StandardCharsets.UTF_8));
                    }
                });
        server.start();
        var f = workspace();
        var error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                f.generation(stream.stream("博客", CodeGenTypeEnum.MULTI_FILE))
                                        .generate(f.workspace.requireApp(f.id), "创建")
                                        .blockLast(Duration.ofSeconds(8)));
        assertTrue(error.getMessage().contains("长度限制"));
        assertEquals(2, calls.get());
        assertEquals(0, f.workspace.requireApp(f.id).getCurrentVersion());
        assertEquals(0, f.workspace.versions(f.id, f.owner).size());
    }

    @Test
    void onlyMissingFilesAreAdoptedEvenIfRepairAlsoReturnsExistingFiles() {
        serve(
                FIRST + "\n```css\n" + CSS + "\n```",
                "```html\n<h1>错误替换</h1>\n```\n```css\nbody{color:red}\n```\n```js\n"
                        + JS
                        + "\n```");
        String result = generated(stream.stream("博客", CodeGenTypeEnum.MULTI_FILE));
        var parsed = new MultiFileCodeParser().parseCode(result);
        assertEquals(HTML, parsed.getHtmlCode());
        assertEquals(CSS, parsed.getCssCode());
        assertEquals(JS, parsed.getJsCode());
        assertFalse(result.contains("错误替换"));
        assertFalse(
                requests.get(1)
                        .getJSONArray("messages")
                        .getJSONObject(1)
                        .getStr("content")
                        .substring(
                                requests.get(1)
                                        .getJSONArray("messages")
                                        .getJSONObject(1)
                                        .getStr("content")
                                        .lastIndexOf("需要补齐的文件："))
                        .contains("style.css"));
        assertEquals(2, calls.get());
    }

    @Test
    void unsuccessfulRepairStopsAfterOneAttemptAndKeepsThePreviousVersion() {
        serve(FIRST, FIRST);
        var f = workspace();
        f.snapshot("before", "<h1>原来的成功版本</h1>");
        var before = f.workspace.files(f.id, 1);
        var error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                f.generation(stream.stream("更新", CodeGenTypeEnum.MULTI_FILE))
                                        .generate(f.workspace.requireApp(f.id), "更新")
                                        .blockLast(Duration.ofSeconds(8)));
        assertTrue(error.getMessage().contains("模型补齐后仍缺少"));
        assertEquals(2, calls.get());
        assertEquals(before, f.workspace.files(f.id, 1));
        assertEquals(1, f.workspace.requireApp(f.id).getCurrentVersion());
        assertEquals("FAILED", f.workspace.requireApp(f.id).getGenerationStatus());
    }

    @Test
    void htmlModeDoesNotTriggerMultiFileCompletion() {
        serve(FIRST);
        assertEquals(FIRST, generated(stream.stream("单页", CodeGenTypeEnum.HTML)));
        assertEquals(1, calls.get());
    }

    @Test
    void missingHtmlIsNotReplacedByARepairAttempt() {
        serve("```css\n" + CSS + "\n```");
        String result = generated(stream.stream("博客", CodeGenTypeEnum.MULTI_FILE));
        assertThrows(
                BusinessException.class, () -> AppGenerationService.parse(result, "multi_file"));
        assertEquals(1, calls.get());
    }

    @Test
    void cancellationBeforeCompletionDoesNotStartRepair() {
        serve(FIRST, REPAIR);
        stream.stream("博客", CodeGenTypeEnum.MULTI_FILE).take(1).blockLast(Duration.ofSeconds(8));
        assertEquals(1, calls.get());
    }

    @Test
    void cancellationDuringRepairClosesTheProviderConnection() throws Exception {
        CountDownLatch started = new CountDownLatch(1), closed = new CountDownLatch(1);
        server.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    int index = calls.getAndIncrement();
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        if (index == 0) {
                            out.write(
                                    (event(FIRST, "stop") + "data: [DONE]\n\n")
                                            .getBytes(StandardCharsets.UTF_8));
                        } else {
                            started.countDown();
                            for (int i = 0; i < 200; i++) {
                                out.write(event("样式分片", "").getBytes(StandardCharsets.UTF_8));
                                out.flush();
                                try {
                                    Thread.sleep(25);
                                } catch (InterruptedException interrupted) {
                                    break;
                                }
                            }
                        }
                    } catch (IOException expected) {
                        closed.countDown();
                    }
                });
        server.start();
        var subscription = stream.stream("博客", CodeGenTypeEnum.MULTI_FILE).subscribe();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            subscription.dispose();
            assertTrue(closed.await(5, TimeUnit.SECONDS), "补齐请求必须随停止关闭实际 HTTP 流");
            assertEquals(2, calls.get());
        } finally {
            subscription.dispose();
        }
    }
}
