package com.yuelei.appforge.core;

import com.yuelei.appforge.ai.AiCodeGeneratorService;
import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.core.parser.CodeParserExecutor;
import com.yuelei.appforge.core.saver.CodeFileSaverExecutor;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.ChatMemoryService;
import com.yuelei.appforge.workspace.CancellableCodeStream;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import reactor.core.publisher.*;

import java.io.File;
import java.util.List;

/** 模型生成入口。应用工作台使用原始可取消流，由版本服务统一保存成功结果。 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {
    @Resource private ChatMemoryService chatMemoryService;
    @Resource private CancellableCodeStream cancellableCodeStream;
    @Resource private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    public Flux<String> generateCodeStream(String message, CodeGenTypeEnum type) {
        return generateCodeStream(message, message, type, 0L);
    }

    public Flux<String> generateCodeStream(String message, CodeGenTypeEnum type, Long appId) {
        return generateCodeStream(message, message, type, appId);
    }

    /**
     * 生成代码流，并复用该应用在工厂中缓存的对话记忆。
     *
     * @param request       实际发给模型的请求（可能包含当前代码上下文）
     * @param memoryMessage 写入对话记忆的原始用户消息
     * @param type          生成类型
     * @param appId         应用 id
     * @return 代码流
     */
    public Flux<String> generateCodeStream(
            String request, String memoryMessage, CodeGenTypeEnum type, Long appId) {
        return createStream(request, memoryMessage, type, appId, 0L, true);
    }

    /** 工作台以当前用户消息 ID 限定历史，持久化成功后才更新工作记忆。 */
    public Flux<String> generateCodeStream(
            String request,
            String memoryMessage,
            CodeGenTypeEnum type,
            Long appId,
            long currentUserMessageId) {
        return createStream(request, memoryMessage, type, appId, currentUserMessageId, false);
    }

    private Flux<String> createStream(
            String request,
            String memoryMessage,
            CodeGenTypeEnum type,
            Long appId,
            long currentUserMessageId,
            boolean rememberOnComplete) {
        if (type == CodeGenTypeEnum.VUE_PROJECT)
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "工程模式请通过应用工作台生成"));
        if (type == null)
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码类型"));

        MessageWindowChatMemory memory = null;
        String prompt = request;
        if (appId != null && appId > 0) {
            String compact = null;
            try {
                compact = chatMemoryService.buildPrompt(appId, currentUserMessageId, request);
            } catch (RuntimeException unavailable) {
                log.warn("读取摘要失败，使用原有记忆上下文 appId={}", appId);
            }
            if (compact != null) prompt = compact;
            else
                try {
                    memory = aiCodeGeneratorServiceFactory.getChatMemory(appId);
                    prompt = withHistory(memory, request);
                } catch (RuntimeException error) {
                    log.warn("读取对话记忆失败，本次不带历史继续生成 appId={}", appId);
                }
        }

        final MessageWindowChatMemory memoryRef = memory;
        final StringBuilder output = new StringBuilder();
        return cancellableCodeStream.stream(prompt, type)
                .doOnNext(output::append)
                .doOnComplete(
                        () -> {
                            if (rememberOnComplete && memoryRef != null) {
                                remember(memoryRef, memoryMessage, output.toString());
                            }
                        });
    }

    public String contextForProject(String request, long appId, long currentMessageId) {
        try {
            String compact = chatMemoryService.buildPrompt(appId, currentMessageId, request);
            if (compact != null) return compact;
        } catch (RuntimeException unavailable) {
            log.warn("读取工程摘要暂不可用 appId={}", appId);
        }
        try {
            return withHistory(aiCodeGeneratorServiceFactory.getChatMemory(appId), request);
        } catch (RuntimeException unavailable) {
            return request;
        }
    }

    public void rememberSuccessfulConversation(long appId, String userMessage, String aiResponse) {
        try {
            aiCodeGeneratorServiceFactory.refreshChatMemory(appId);
        } catch (RuntimeException error) {
            log.warn("本轮已保存，Redis 记忆暂不可用 appId={}", appId);
        }
        chatMemoryService.afterConversationSaved(appId);
    }

    public File generateAndSaveCode(String message, CodeGenTypeEnum type, Long appId) {
        ThrowUtils.throwIf(
                type == null || type == CodeGenTypeEnum.VUE_PROJECT,
                ErrorCode.PARAMS_ERROR,
                "请通过应用工作台生成此类型");
        long id = appId == null ? 0L : appId;
        AiCodeGeneratorService service =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(id);
        Object result =
                type == CodeGenTypeEnum.HTML
                        ? service.generateHtmlCode(message)
                        : service.generateMultiFileCode(message);
        return CodeFileSaverExecutor.executeSaver(result, type, appId);
    }

    /** 兼容独立的生成/保存调用；保存失败必须传递为错误，不能假报完成。 */
    public Flux<String> generateAndSaveCodeStream(
            String message, CodeGenTypeEnum type, Long appId) {
        return Flux.defer(
                () -> {
                    StringBuilder content = new StringBuilder();
                    return generateCodeStream(message, message, type, appId)
                            .doOnNext(content::append)
                            .concatWith(
                                    Mono.fromRunnable(
                                                    () -> {
                                                        Object result =
                                                                CodeParserExecutor.executeParser(
                                                                        content.toString(), type);
                                                        CodeFileSaverExecutor.executeSaver(
                                                                result, type, appId);
                                                    })
                                            .then(Mono.empty()));
                });
    }

    /** 把历史对话拼进本轮用户消息，保持与现有可取消 HTTP 流兼容。 */
    private String withHistory(ChatMemory memory, String request) {
        List<ChatMessage> history = memory.messages();
        if (history.isEmpty()) return request;
        StringBuilder prompt = new StringBuilder("以下是本应用之前的对话记录，请保持上下文连续：\n");
        for (ChatMessage item : history) {
            if (item instanceof UserMessage user) {
                prompt.append("用户：").append(user.singleText()).append('\n');
            } else if (item instanceof AiMessage ai) {
                prompt.append("AI：").append(ai.text()).append('\n');
            }
        }
        return prompt.append("\n用户本轮要求：\n").append(request).toString();
    }

    private void remember(ChatMemory memory, String userMessage, String aiResponse) {
        try {
            memory.add(UserMessage.from(userMessage));
            memory.add(AiMessage.from(aiResponse));
        } catch (RuntimeException error) {
            // 代码已经生成成功，记忆写入失败不能反过来让用户看到生成失败。
            log.warn("保存对话记忆失败", error);
        }
    }
}


