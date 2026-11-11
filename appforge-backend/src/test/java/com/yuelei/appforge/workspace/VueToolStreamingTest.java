package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.core.adapter.TokenStreamFluxAdapter;
import com.yuelei.appforge.core.handler.StreamHandlerExecutor;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.ChatHistoryService;
import com.yuelei.appforge.service.impl.AppServiceImpl;
import com.yuelei.appforge.workspace.vue.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

class VueToolStreamingTest {
    @TempDir Path root;

    @Test
    void
            actualAiServiceStreamsWhitespaceFragmentsBeforeExecutionAndSeparatesReusedIndexesAcrossRounds() {
        var selected = new AtomicInteger();
        try (var task = new ProjectTask(Map.of(), event -> {})) {
            var count = new AtomicInteger();
            StreamingChatModel model =
                    new StreamingChatModel() {
                        @Override
                        public void doChat(
                                ChatRequest request, StreamingChatResponseHandler handler) {
                            int round = count.getAndIncrement();
                            if (round == 2) {
                                handler.onPartialResponse("完成。");
                                handler.onCompleteResponse(response(AiMessage.from("完成。")));
                                return;
                            }
                            String path =
                                    "src/components/" + (round == 0 ? "First" : "Second") + ".vue";
                            String arguments =
                                    JSONUtil.toJsonStr(
                                            Map.of(
                                                    "path",
                                                    path,
                                                    "content",
                                                    "<template>中文</template>"));
                            handler.onPartialToolCall(
                                    PartialToolCall.builder()
                                            .index(0)
                                            .name("writeFile")
                                            .partialArguments(" ")
                                            .build());
                            assertEquals(round + 1, selected.get(), "执行前必须显示选择工具");
                            assertNull(task.source(path), "参数还在生成时不得执行写入");
                            handler.onPartialToolCall(
                                    PartialToolCall.builder()
                                            .index(0)
                                            .id("real-" + round)
                                            .name("writeFile")
                                            .partialArguments(arguments)
                                            .build());
                            handler.onCompleteResponse(
                                    response(
                                            AiMessage.from(
                                                    ToolExecutionRequest.builder()
                                                            .id("real-" + round)
                                                            .name("writeFile")
                                                            .arguments(arguments)
                                                            .build())));
                        }
                    };
            var service =
                    new AiCodeGeneratorServiceFactory()
                            .vueProjectService(new VueProjectTools(task), model);
            var history = new StringBuilder();
            String text =
                    String.join(
                            "",
                            new StreamHandlerExecutor()
                                    .doExecute(
                                            TokenStreamFluxAdapter.adapt(
                                                    service.create("写两个组件"), task),
                                            CodeGenTypeEnum.VUE_PROJECT,
                                            history::append)
                                    .doOnNext(
                                            chunk -> {
                                                if (chunk.contains("[选择工具]"))
                                                    selected.incrementAndGet();
                                            })
                                    .collectList()
                                    .block(Duration.ofSeconds(5)));
            assertEquals(2, selected.get());
            assertTrue(text.contains("[工具调用] 写入文件 src/components/First.vue"));
            assertTrue(text.contains("[工具调用] 写入文件 src/components/Second.vue"));
            assertFalse(history.toString().contains("选择工具"));
            assertTrue(history.toString().endsWith("完成。"));
        }
    }

    ChatResponse response(AiMessage message) {
        return ChatResponse.builder()
                .aiMessage(message)
                .finishReason(
                        message.hasToolExecutionRequests()
                                ? FinishReason.TOOL_EXECUTION
                                : FinishReason.STOP)
                .tokenUsage(new TokenUsage(10, 100))
                .build();
    }

    @Test
    void successfulGenerationPersistsOneCompletedHistoryAfterTheVersionAndOmitsTransientSelection()
            throws Exception {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        f.jdbc.update("UPDATE app SET codeGenType='vue_project' WHERE id=?", f.id);
        var fixture = new VueProjectTest();
        var generation = f.generation(Flux.never());
        ReflectionTestUtils.setField(
                generation,
                "projectRunner",
                fixture.runner(new VueProjectTest.ScriptModel(), fixture.successfulBuilder()));
        var history = mock(ChatHistoryService.class);
        when(history.appendChatMessage(anyLong(), anyString(), eq("user"), anyLong()))
                .thenReturn(5L);
        when(history.addChatMessage(anyLong(), anyString(), eq("ai"), anyLong())).thenReturn(true);
        var service =
                new AppServiceImpl() {
                    @Override
                    public App getById(Serializable id) {
                        return f.workspace.requireApp(((Number) id).longValue());
                    }
                };
        ReflectionTestUtils.setField(service, "generation", generation);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "streamHandlerExecutor", new StreamHandlerExecutor());
        var users = mock(com.yuelei.appforge.service.UserService.class);
        when(users.getLoginUser(any())).thenReturn(f.owner);
        var controller = new com.yuelei.appforge.controller.AppController();
        ReflectionTestUtils.setField(controller, "appService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                controller)
                        .build();
        var result =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/app/chat/gen/code")
                                        .param("appId", String.valueOf(f.id))
                                        .param("message", "创建阅读馆"))
                        .andReturn();
        result.getAsyncResult(10000);
        String sse =
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(sse.contains("event:done"));
        var chunks =
                sse.lines()
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> JSONUtil.parseObj(line.substring(5)))
                        .filter(value -> value.containsKey("d"))
                        .map(value -> value.getStr("d"))
                        .toList();
        String output = String.join("", chunks);
        assertEquals(1, f.workspace.requireApp(f.id).getCurrentVersion());
        assertTrue(output.contains("[选择工具]"));
        var saved = ArgumentCaptor.forClass(String.class);
        verify(history, times(1))
                .addChatMessage(eq(f.id), saved.capture(), eq("ai"), eq(f.owner.getId()));
        assertTrue(saved.getValue().contains("[工具调用] 写入文件 src/App.vue"));
        assertFalse(saved.getValue().contains("选择工具"));
        assertTrue(saved.getValue().contains("```vue"));
        java.nio.file.Files.writeString(
                Path.of("target/tool-stream-browser-fixture.json"),
                JSONUtil.toJsonStr(Map.of("chunks", chunks, "history", saved.getValue())));
    }

    @Test
    void cancellingTheRunnerCancelsTheSdkStreamingHandle() {
        StreamingHandle handle = mock(StreamingHandle.class);
        var fixture = new VueProjectTest();
        var model =
                new StreamingChatModel() {
                    @Override
                    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                        handler.onPartialToolCall(
                                PartialToolCall.builder()
                                        .index(0)
                                        .name("writeFile")
                                        .partialArguments(" ")
                                        .build(),
                                new PartialToolCallContext(handle));
                    }
                };
        try (var task = new ProjectTask(Map.of(), event -> {})) {
            var subscription =
                    fixture.runner(model, fixture.successfulBuilder()).stream(1, 1, "创建", task)
                            .subscribe();
            subscription.dispose();
            verify(handle, atLeastOnce()).cancel();
            assertTrue(task.isCancelled());
        }
    }

    @Test
    void failedBuildDoesNotPersistPartialToolCodeOrCreateAVersion() {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        f.jdbc.update("UPDATE app SET codeGenType='vue_project' WHERE id=?", f.id);
        var fixture = new VueProjectTest();
        var generation = f.generation(Flux.never());
        var builder =
                new VueProjectBuilder() {
                    @Override
                    public Map<String, String> build(ProjectTask task) {
                        task.seal();
                        throw new com.yuelei.appforge.exception.BusinessException(
                                com.yuelei.appforge.exception.ErrorCode.OPERATION_ERROR, "构建失败");
                    }
                };
        ReflectionTestUtils.setField(
                generation,
                "projectRunner",
                fixture.runner(new VueProjectTest.ScriptModel(), builder));
        var history = mock(ChatHistoryService.class);
        when(history.appendChatMessage(anyLong(), anyString(), eq("user"), anyLong()))
                .thenReturn(5L);
        when(history.addChatMessage(anyLong(), anyString(), eq("ai"), anyLong())).thenReturn(true);
        var service =
                new AppServiceImpl() {
                    @Override
                    public App getById(Serializable id) {
                        return f.workspace.requireApp(((Number) id).longValue());
                    }
                };
        ReflectionTestUtils.setField(service, "generation", generation);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "streamHandlerExecutor", new StreamHandlerExecutor());
        assertThrows(
                RuntimeException.class,
                () -> service.chatToGenCode(f.id, "创建", f.owner).blockLast(Duration.ofSeconds(10)));
        var saved = ArgumentCaptor.forClass(String.class);
        verify(history, times(1))
                .addChatMessage(eq(f.id), saved.capture(), eq("ai"), eq(f.owner.getId()));
        assertTrue(saved.getValue().startsWith("AI 回复失败："));
        assertFalse(saved.getValue().contains("工具调用"));
        assertEquals(0, f.workspace.requireApp(f.id).getCurrentVersion());
    }

    @Test
    void stoppingGenerationPersistsOnlyOneStopMessage() throws Exception {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        f.jdbc.update("UPDATE app SET codeGenType='vue_project' WHERE id=?", f.id);
        var generation = f.generation(Flux.never());
        var entered = new java.util.concurrent.CountDownLatch(1);
        ReflectionTestUtils.setField(
                generation,
                "projectRunner",
                new VueProjectRunner() {
                    @Override
                    public Flux<String> stream(
                            long id,
                            long before,
                            String request,
                            ProjectTask task,
                            GenerationMode mode) {
                        entered.countDown();
                        return Flux.never();
                    }

                    @Override
                    public void close(ProjectTask task) {
                        task.close();
                    }
                });
        var history = mock(ChatHistoryService.class);
        when(history.appendChatMessage(anyLong(), anyString(), eq("user"), anyLong()))
                .thenReturn(5L);
        when(history.addChatMessage(anyLong(), anyString(), eq("ai"), anyLong())).thenReturn(true);
        var service =
                new AppServiceImpl() {
                    @Override
                    public App getById(Serializable id) {
                        return f.workspace.requireApp(((Number) id).longValue());
                    }
                };
        ReflectionTestUtils.setField(service, "generation", generation);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "streamHandlerExecutor", new StreamHandlerExecutor());
        var done = new java.util.concurrent.CountDownLatch(1);
        var subscription =
                service.chatToGenCode(f.id, "创建", f.owner)
                        .subscribe(v -> {}, e -> done.countDown());
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            generation.stop(f.id, f.owner);
            assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var saved = ArgumentCaptor.forClass(String.class);
            verify(history, times(1))
                    .addChatMessage(eq(f.id), saved.capture(), eq("ai"), eq(f.owner.getId()));
            assertEquals("生成已停止，未保存不完整代码", saved.getValue());
            assertEquals(0, f.workspace.requireApp(f.id).getCurrentVersion());
        } finally {
            subscription.dispose();
        }
    }
}
