package com.yuelei.appforge.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yuelei.appforge.service.ChatHistoryService;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 代码生成服务工厂（方案 2：每个应用一个 AI Service 实例）。
 *
 * <p>使用 Caffeine 缓存每个 appId 对应的 AiService，避免重复构建；创建实例时把数据库中的
 * 历史对话加载到该应用独立的 Redis 记忆中。</p>
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    /** 缓存中的 AiService 与它的对话记忆共用同一个 memory 对象。 */
    private final Map<Long, MessageWindowChatMemory> serviceMemories = new ConcurrentHashMap<>();

    /**
     * AI 服务实例缓存策略：
     *
     * <ul>
     *   <li>最多缓存 1000 个实例</li>
     *   <li>写入后 30 分钟过期</li>
     *   <li>访问后 10 分钟过期</li>
     * </ul>
     */
    private final Cache<Long, AiCodeGeneratorService> serviceCache =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .removalListener(
                            (key, value, cause) -> {
                                serviceMemories.remove(key);
                                log.debug("AI 服务实例被移除, appId: {}, 原因: {}", key, cause);
                            })
                    .build();


    /**
     * 根据 appId 获取对应的 AI 服务实例。
     *
     * @param appId 应用 id
     * @return AI 服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }

    /**
     * 获取某个应用正在使用的对话记忆，供可取消 HTTP 流复用同一份上下文。
     *
     * @param appId 应用 id
     * @return 该应用的对话记忆
     */
    public MessageWindowChatMemory getChatMemory(long appId) {
        getAiCodeGeneratorService(appId);
        MessageWindowChatMemory memory = serviceMemories.get(appId);
        if (memory == null) {
            // 极端情况下缓存实例还在但 memory 已因过期被移除，重新创建一次。
            serviceCache.invalidate(appId);
            getAiCodeGeneratorService(appId);
            memory = serviceMemories.get(appId);
        }
        return memory;
    }

    /** 完整回复持久化后，以数据库的最新窗口重建 Redis，避免重复追加。 */
    public void refreshChatMemory(long appId) {
        chatHistoryService.loadChatHistoryToMemory(appId, getChatMemory(appId), 20, false);
    }

    /** 独立、无会话记忆的摘要服务。 */
    @Bean
    public AiChatSummaryService aiChatSummaryService() {
        return AiServices.builder(AiChatSummaryService.class).chatModel(chatModel).build();
    }

    /**
     * 默认提供一个 AI Service Bean，兼容应用名称生成等旧代码。
     *
     * @return AI 服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 创建新的 AI 服务实例，并加载该应用的历史对话。
     *
     * @param appId 应用 id
     * @return AI 服务
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        MessageWindowChatMemory chatMemory =
                MessageWindowChatMemory.builder()
                        .id(appId)
                        .chatMemoryStore(redisChatMemoryStore)
                        .maxMessages(20)
                        .build();
        // 懒加载：第一次真正生成时才从数据库恢复历史，避免启动时全量加载。
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        AiCodeGeneratorService service =
                AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(streamingChatModel)
                        .chatMemory(chatMemory)
                        .build();
        serviceMemories.put(appId, chatMemory);
        return service;
    }
}

