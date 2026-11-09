package com.yuelei.appforge.core.adapter;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.model.message.*;
import com.yuelei.appforge.workspace.vue.ProjectTask;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.service.TokenStream;

import reactor.core.publisher.Flux;

import java.util.*;

/** 将官方 TokenStream 回调封装为完整 JSON 消息，分片不进入文件工具或数据库。 */
public final class TokenStreamFluxAdapter {
    private TokenStreamFluxAdapter() {}

    public static Flux<String> adapt(TokenStream stream, ProjectTask task) {
        return Flux.create(
                sink -> {
                    CallIds ids = new CallIds();
                    sink.onCancel(task::cancel);
                    java.util.function.Consumer<StreamMessage> send =
                            message -> {
                                if (!sink.isCancelled() && !task.isCancelled())
                                    sink.next(JSONUtil.toJsonStr(message));
                            };
                    try {
                        stream.onPartialResponse(text -> send.accept(new AiResponseMessage(text)))
                                .onPartialToolCall(
                                        call ->
                                                send.accept(
                                                        new ToolRequestMessage(
                                                                ids.partial(call),
                                                                call.name(),
                                                                call.index(),
                                                                call.partialArguments())))
                                .onIntermediateResponse(ids::intermediate)
                                .beforeToolExecution(
                                        before -> {
                                            task.check();
                                            var request = before.request();
                                            send.accept(
                                                    new ToolRequestMessage(
                                                            ids.execution(request, false),
                                                            request.name(),
                                                            -1,
                                                            request.arguments()));
                                        })
                                .onToolExecuted(
                                        tool -> {
                                            task.check();
                                            var request = tool.request();
                                            boolean failed =
                                                    tool.hasFailed()
                                                            || Objects.toString(tool.result(), "")
                                                                    .startsWith("操作未完成：");
                                            String content = null;
                                            if (!failed
                                                    && Set.of("writeFile", "modifyFile", "readFile")
                                                            .contains(request.name())) {
                                                try {
                                                    String path =
                                                            JSONUtil.parseObj(request.arguments())
                                                                    .getStr("path");
                                                    content = task.source(path);
                                                } catch (RuntimeException invalid) {
                                                    failed = true;
                                                }
                                            }
                                            var executed =
                                                    new ToolExecutedMessage(
                                                            ids.execution(request, true),
                                                            request.name(),
                                                            request.arguments(),
                                                            content,
                                                            failed);
                                            executed.setResult(tool.result());
                                            send.accept(executed);
                                        })
                                .onCompleteResponse(
                                        response -> {
                                            if (!sink.isCancelled() && !task.isCancelled())
                                                sink.complete();
                                        })
                                .onError(
                                        error -> {
                                            if (!sink.isCancelled()) sink.error(error);
                                        })
                                .start();
                    } catch (Throwable error) {
                        if (!sink.isCancelled()) sink.error(error);
                    }
                });
    }

    /** index 会在新一轮模型响应中复用，统一生成轮次内稳定 ID，兼容未提供 ID 的模型。 */
    private static final class CallIds {
        int round, fallback;
        final Map<Integer, String> partial = new HashMap<>();
        final List<Pending> pending = new ArrayList<>();

        record Pending(ToolExecutionRequest request, String id) {}

        synchronized String partial(PartialToolCall call) {
            return partial.computeIfAbsent(call.index(), index -> "call-" + round + "-" + index);
        }

        synchronized void intermediate(ChatResponse response) {
            var calls = response.aiMessage().toolExecutionRequests();
            if (calls == null || calls.isEmpty()) return;
            for (int index = 0; index < calls.size(); index++) {
                int current = index;
                pending.add(
                        new Pending(
                                calls.get(index),
                                partial.computeIfAbsent(
                                        index, i -> "call-" + round + "-" + current)));
            }
            partial.clear();
            round++;
        }

        synchronized String execution(ToolExecutionRequest request, boolean complete) {
            for (var item : List.copyOf(pending)) {
                boolean same =
                        request.id() != null
                                ? request.id().equals(item.request.id())
                                : request.name().equals(item.request.name())
                                        && Objects.equals(
                                                request.arguments(), item.request.arguments());
                if (same) {
                    if (complete) pending.remove(item);
                    return item.id;
                }
            }
            String id = "call-fallback-" + fallback++;
            if (!complete) pending.add(new Pending(request, id));
            return id;
        }
    }
}
