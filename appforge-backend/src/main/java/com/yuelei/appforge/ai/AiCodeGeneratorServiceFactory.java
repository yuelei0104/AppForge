package com.yuelei.appforge.ai;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 代码生成服务工厂。
 *
 * <p>使用 ChatMemoryProvider 按 memoryId（即 appId）分配独立的对话记忆。</p>
 */
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private ChatMemoryProvider chatMemoryProvider;

    /**
     * 默认 AI 服务 Bean，供名称生成等场景使用。
     *
     * @return AI 服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return AiServices.builder(AiCodeGeneratorService.class)
            .chatModel(chatModel)
            .streamingChatModel(streamingChatModel)
            .chatMemoryProvider(chatMemoryProvider)
            .build();
    }
}
