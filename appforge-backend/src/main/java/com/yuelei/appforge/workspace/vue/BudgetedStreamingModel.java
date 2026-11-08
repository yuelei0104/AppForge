package com.yuelei.appforge.workspace.vue;

import com.yuelei.appforge.exception.*;

import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.FinishReason;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** 为完整的多轮工具调用分配小于 20000 的输出 Token 总预算。 */
final class BudgetedStreamingModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    private final ProjectTask task;
    private int remaining = 19900;

    BudgetedStreamingModel(StreamingChatModel delegate, ProjectTask task) {
        this.delegate = delegate;
        this.task = task;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public java.util.Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        task.check();
        ThrowUtils.throwIf(remaining < 1, ErrorCode.OPERATION_ERROR, "工程生成已达到输出预算");
        var parameters =
                request.parameters()
                        .overrideWith(
                                ChatRequestParameters.builder()
                                        .maxOutputTokens(Math.min(8192, remaining))
                                        .build());
        AtomicBoolean ended = new AtomicBoolean();
        delegate.chat(
                request.toBuilder().parameters(parameters).build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String text) {
                        if (!ended.get() && !task.cancelled.get()) handler.onPartialResponse(text);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (ended.compareAndSet(false, true)) handler.onError(error);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        if (!ended.compareAndSet(false, true)) return;
                        try {
                            task.check();
                            ThrowUtils.throwIf(
                                    response.finishReason() == FinishReason.LENGTH,
                                    ErrorCode.OPERATION_ERROR,
                                    "模型输出不完整，本次未保存");
                            Integer used =
                                    response.tokenUsage() == null
                                            ? null
                                            : response.tokenUsage().outputTokenCount();
                            if (used == null) {
                                String text =
                                        java.util.Objects.toString(response.aiMessage().text(), "");
                                if (response.aiMessage().hasToolExecutionRequests())
                                    for (var call : response.aiMessage().toolExecutionRequests())
                                        text += call.name() + call.arguments();
                                used =
                                        text.getBytes(StandardCharsets.UTF_8).length
                                                + 32
                                                + (response.aiMessage().hasToolExecutionRequests()
                                                        ? response.aiMessage()
                                                                        .toolExecutionRequests()
                                                                        .size()
                                                                * 64
                                                        : 0);
                            }
                            ThrowUtils.throwIf(
                                    used < 0 || used > remaining,
                                    ErrorCode.OPERATION_ERROR,
                                    "工程生成已达到输出预算");
                            remaining -= used;
                            handler.onCompleteResponse(response);
                        } catch (Throwable failure) {
                            handler.onError(failure);
                        }
                    }
                });
    }
}
