package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.model.message.*;
import com.yuelei.appforge.core.handler.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import java.util.*;

class ToolStreamHandlerTest {
    String json(Object value) {
        return JSONUtil.toJsonStr(value);
    }

    String joined(Flux<String> source) {
        return String.join("", source.collectList().block());
    }

    @Test
    void repeatedIncompleteRequestsShowOneSelectionAndOnlyCompletedCodeEntersHistory() {
        var history = new StringBuilder();
        var result =
                new ToolExecutedMessage(
                        "a",
                        "writeFile",
                        "{\"path\":\"src/App.vue\"}",
                        "<template>中文</template>",
                        false);
        String output =
                joined(
                        new JsonMessageStreamHandler()
                                .handle(
                                        Flux.just(
                                                json(new AiResponseMessage("开始创建。")),
                                                json(
                                                        new ToolRequestMessage(
                                                                "a", "writeFile", 0, " ")),
                                                json(
                                                        new ToolRequestMessage(
                                                                "a", "writeFile", 0, "{\"pa")),
                                                json(result),
                                                json(result)),
                                        history::append));
        assertEquals(1, output.split("\\[选择工具\\]", -1).length - 1);
        assertEquals(1, output.split("\\[工具调用\\]", -1).length - 1);
        assertTrue(output.contains("```vue\n<template>中文</template>\n```"));
        assertFalse(history.toString().contains("选择工具"));
        assertTrue(history.toString().contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(history.toString().startsWith("开始创建。"));
    }

    @Test
    void unknownTypesAndNullPayloadsDoNotBreakTheStream() {
        assertEquals(
                "有效",
                joined(
                        new JsonMessageStreamHandler()
                                .handle(
                                        Flux.just(
                                                "{\"type\":\"future_type\"}",
                                                "{}",
                                                json(new AiResponseMessage(null)),
                                                json(new AiResponseMessage("有效"))),
                                        value -> {})));
    }

    @Test
    void selectionStateIsScopedToEachSubscription() {
        var handled =
                new JsonMessageStreamHandler()
                        .handle(
                                Flux.just(
                                        json(new ToolRequestMessage("a", "readDirectory", 0, ""))),
                                value -> {});
        assertEquals(joined(handled), joined(handled));
        assertTrue(joined(handled).contains("读取目录"));
    }

    @Test
    void differentToolsHaveAccurateLabelsAndFailureDoesNotClaimAFileWasWritten() {
        String output =
                joined(
                        new JsonMessageStreamHandler()
                                .handle(
                                        Flux.just(
                                                json(
                                                        new ToolExecutedMessage(
                                                                "a",
                                                                "readDirectory",
                                                                "{}",
                                                                null,
                                                                false)),
                                                json(
                                                        new ToolExecutedMessage(
                                                                "b",
                                                                "modifyFile",
                                                                "{\"path\":\"src/App.vue\"}",
                                                                "new content",
                                                                false)),
                                                json(
                                                        new ToolExecutedMessage(
                                                                "c",
                                                                "writeFile",
                                                                "{\"path\":\"../secret\"}",
                                                                "must not appear",
                                                                true)),
                                                json(
                                                        new ToolExecutedMessage(
                                                                "d",
                                                                "deleteFile",
                                                                "{\"path\":\"src/old.js\"}",
                                                                null,
                                                                false))),
                                        value -> {}));
        assertTrue(output.contains("[工具调用] 读取目录"));
        assertTrue(output.contains("[工具调用] 修改文件 src/App.vue"));
        assertTrue(output.contains("[工具调用] 删除文件 src/old.js"));
        assertTrue(output.contains("[工具失败] 写入文件"));
        assertFalse(output.contains("must not appear"));
        assertFalse(output.contains("../secret"));
    }

    @Test
    void fileContentContainingCodeFencesRemainsInsideOneCodeBlock() {
        String content = "示例\n```javascript\nalert(1)\n```";
        String output =
                joined(
                        new JsonMessageStreamHandler()
                                .handle(
                                        Flux.just(
                                                json(
                                                        new ToolExecutedMessage(
                                                                "a",
                                                                "writeFile",
                                                                "{\"path\":\"src/fixture.js\"}",
                                                                content,
                                                                false))),
                                        value -> {}));
        assertTrue(output.contains("````javascript\n" + content + "\n````"));
    }

    @Test
    void nativeModesKeepTheirOriginalTextAndHistory() {
        for (var mode : List.of(CodeGenTypeEnum.HTML, CodeGenTypeEnum.MULTI_FILE)) {
            var history = new StringBuilder();
            String original = "```html\n<h1>中文</h1>\n```";
            assertEquals(
                    original,
                    joined(
                            new StreamHandlerExecutor()
                                    .doExecute(Flux.just(original), mode, history::append)));
            assertEquals(original, history.toString());
        }
    }
}
