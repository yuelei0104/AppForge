package com.yuelei.appforge.ai.tools;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONObject;

import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.workspace.vue.ProjectTask;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class VisualEditToolsTest {
    @Test
    void springRegistersFiveStatelessToolTemplatesAndInjectsStreamFormatter() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(
                        ToolManager.class,
                        FileWriteTool.class,
                        FileReadTool.class,
                        FileModifyTool.class,
                        FileDeleteTool.class,
                        FileDirReadTool.class,
                        com.yuelei.appforge.core.handler.StreamHandlerExecutor.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    5, context.getBean(ToolManager.class).getAllTools().length);
                            assertNotNull(
                                    context.getBean(
                                            com.yuelei.appforge.core.handler.StreamHandlerExecutor
                                                    .class));
                        });
    }

    @Test
    void boundToolsReadAndModifyOnlyTheirOwnTaskAndProtectEntryFiles() {
        ToolManager manager = ToolManager.defaults();
        assertEquals(5, manager.getAllTools().length);
        try (var a = new ProjectTask(Map.of("src/pages/Resume.vue", "<p>旧学校</p>"), e -> {});
                var b = new ProjectTask(Map.of("src/pages/Resume.vue", "<p>其他应用</p>"), e -> {})) {
            Map<String, BaseTool> tools = new HashMap<>();
            for (var t : manager.getAllTools(a)) tools.put(t.getToolName(), t);
            var read = (FileReadTool) tools.get("readFile");
            var modify = (FileModifyTool) tools.get("modifyFile");
            var directory = (FileDirReadTool) tools.get("readDirectory");
            var delete = (FileDeleteTool) tools.get("deleteFile");
            var write = (FileWriteTool) tools.get("writeFile");
            assertTrue(modify.modifyFile("src/pages/Resume.vue", "旧学校", "新学校").startsWith("操作未完成"));
            directory.readDirectory();
            assertEquals("<p>旧学校</p>", read.readFile("src/pages/Resume.vue"));
            assertEquals("文件已保存", modify.modifyFile("src/pages/Resume.vue", "旧学校", "新学校"));
            assertEquals("<p>新学校</p>", a.source("src/pages/Resume.vue"));
            assertEquals("<p>其他应用</p>", b.source("src/pages/Resume.vue"));
            assertTrue(read.readFile("../../secret").startsWith("操作未完成"));
            assertTrue(read.readFile("/etc/passwd").startsWith("操作未完成"));
            write.writeFile("src/App.vue", "<template>保留</template>");
            read.readFile("src/App.vue");
            assertTrue(delete.deleteFile("src/App.vue").contains("重要文件"));
            assertNotNull(a.source("src/App.vue"));
            read.readFile("src/pages/Resume.vue");
            assertEquals("文件已删除", delete.deleteFile("src/pages/Resume.vue"));
            assertNull(a.source("src/pages/Resume.vue"));
        }
    }

    @Test
    void toolFormattingShowsChangesAndKeepsCodeFencesBalanced() {
        var tools = ToolManager.defaults();
        var args =
                new JSONObject()
                        .set("path", "src/pages/Resume.vue")
                        .set("before", "<p>旧学校</p>")
                        .set("after", "<p>新学校</p>");
        String result = tools.getTool("modifyFile").generateToolExecutedResult(args);
        assertTrue(result.contains("替换前："));
        assertTrue(result.contains("替换后："));
        assertTrue(result.contains("<p>旧学校</p>"));
        assertTrue(result.contains("<p>新学校</p>"));
        assertEquals(
                "[工具调用] 读取目录 根目录",
                tools.getTool("readDirectory").generateToolExecutedResult(new JSONObject()));
        String nested = "```js\nexample\n```";
        assertTrue(
                tools.getTool("writeFile")
                        .generateToolExecutedResult(
                                new JSONObject().set("path", "src/test.js").set("content", nested))
                        .contains("````javascript"));
    }

    @Test
    void officialAiServiceReceivesArrayOfFiveBoundToolsAndModifiesExistingSource()
            throws Exception {
        try (var task = new ProjectTask(Map.of("src/pages/Resume.vue", "<p>旧学校</p>"), e -> {})) {
            var complete = new CompletableFuture<String>();
            var calls = new ArrayList<String>();
            StreamingChatModel model =
                    new StreamingChatModel() {
                        int round;

                        public void doChat(
                                ChatRequest request, StreamingChatResponseHandler handler) {
                            assertEquals(5, request.toolSpecifications().size());
                            String name;
                            String arguments;
                            switch (round++) {
                                case 0 -> {
                                    name = "readDirectory";
                                    arguments = "{}";
                                }
                                case 1 -> {
                                    name = "readFile";
                                    arguments = "{\"path\":\"src/pages/Resume.vue\"}";
                                }
                                case 2 -> {
                                    name = "modifyFile";
                                    arguments =
                                            "{\"path\":\"src/pages/Resume.vue\",\"before\":\"旧学校\",\"after\":\"新学校\"}";
                                }
                                default -> {
                                    handler.onCompleteResponse(
                                            ChatResponse.builder()
                                                    .aiMessage(AiMessage.from("修改完成"))
                                                    .build());
                                    return;
                                }
                            }
                            calls.add(name);
                            handler.onCompleteResponse(
                                    ChatResponse.builder()
                                            .aiMessage(
                                                    AiMessage.from(
                                                            ToolExecutionRequest.builder()
                                                                    .id("call-" + round)
                                                                    .name(name)
                                                                    .arguments(arguments)
                                                                    .build()))
                                            .build());
                        }
                    };
            new AiCodeGeneratorServiceFactory()
                    .vueProjectService(ToolManager.defaults().getAllTools(task), model)
                    .edit("修改学校")
                    .onPartialResponse(s -> {})
                    .onCompleteResponse(r -> complete.complete(r.aiMessage().text()))
                    .onError(complete::completeExceptionally)
                    .start();
            assertEquals("修改完成", complete.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("readDirectory", "readFile", "modifyFile"), calls);
            assertEquals("<p>新学校</p>", task.source("src/pages/Resume.vue"));
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
            named = "APPFORGE_VISUAL_PROJECT_DIST",
            matches = ".+")
    void materializesActualBuiltVuePreviewWithoutChangingSource() throws Exception {
        java.nio.file.Path dist =
                java.nio.file.Path.of(System.getenv("APPFORGE_VISUAL_PROJECT_DIST"));
        Map<String, String> runtime = new LinkedHashMap<>();
        try (var paths = java.nio.file.Files.walk(dist)) {
            for (var path : paths.filter(java.nio.file.Files::isRegularFile).toList())
                runtime.put(
                        dist.relativize(path).toString().replace('\\', '/'),
                        java.nio.file.Files.readString(path));
        }
        String original =
                com.yuelei.appforge.workspace.AppPageRenderer.inlinePrivateAssets(runtime);
        String preview =
                com.yuelei.appforge.workspace.AppPageRenderer.withVisualEditorBridge(original);
        assertFalse(original.contains("appforge-visual-editor-bridge"));
        java.nio.file.Path evidence =
                java.nio.file.Path.of("target/visual-edit-fixture/vue-owner-preview.html");
        java.nio.file.Files.createDirectories(evidence.getParent());
        java.nio.file.Files.writeString(evidence, preview);
    }
}
