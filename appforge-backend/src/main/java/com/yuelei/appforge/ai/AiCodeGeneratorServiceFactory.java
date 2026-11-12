package com.yuelei.appforge.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yuelei.appforge.ai.guardrail.PromptSafetyInputGuardrail;
import com.yuelei.appforge.ai.guardrail.RetryOutputGuardrail;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.ChatHistoryService;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.time.Duration;

/**
 * AI 代码生成服务工厂（方案 2：每个应用一个 AI Service 实例）。
 *
 * <p>使用 Caffeine 缓存每个 appId 对应的 AiService，避免重复构建；创建实例时把数据库中的
 * 历史对话加载到该应用独立的 Redis 记忆中。</p>
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;


    @Resource(name = "openAiStreamingChatModel")
    private StreamingChatModel streamingChatModel;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    /** 服务与记忆按同一条目缓存，淘汰回调不能影响后续创建的实例。 */
    private record CachedService(AiCodeGeneratorService service, MessageWindowChatMemory memory) {}

    /**
     * AI 服务实例缓存策略：
     *
     * <ul>
     *   <li>最多缓存 1000 个实例</li>
     *   <li>写入后 30 分钟过期</li>
     *   <li>访问后 10 分钟过期</li>
     * </ul>
     */
    private final Cache<Long, CachedService> serviceCache =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .removalListener(
                            (key, value, cause) -> {
                                log.debug("AI 服务实例被移除, appId: {}, 原因: {}", key, cause);
                            })
                    .build();

    /**
     * 为不同代码生成类型选择模型 Bean。
     *
     * <p>两类模型都声明为 prototype，每次生成取一个新实例，避免多个应用并发生成时
     * 共享同一个流式模型客户端。Vue 工程需要更强的推理能力，使用 reasoning 配置；
     * HTML / MULTI_FILE 使用普通流式配置。</p>
     *
     * @param codeGenType 代码生成类型
     * @return 本次生成使用的流式模型
     */
    public StreamingChatModel getStreamingChatModel(CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        // 单元测试直接 new 工厂时没有 Spring 上下文，退回到注入的 mock。
        if (applicationContext == null) {
            return streamingChatModel;
        }
        String beanName =
                switch (codeGenType) {
                    case VUE_PROJECT -> "reasoningStreamingChatModelPrototype";
                    case HTML, MULTI_FILE -> "streamingChatModelPrototype";
                };
        return applicationContext.getBean(beanName, StreamingChatModel.class);
    }

    /**
     * 根据 appId 获取对应的 AI 服务实例。
     *
     * @param appId 应用 id
     * @return AI 服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService).service();
    }

    /**
     * 获取某个应用正在使用的对话记忆，供可取消 HTTP 流复用同一份上下文。
     *
     * @param appId 应用 id
     * @return 该应用的对话记忆
     */
    public MessageWindowChatMemory getChatMemory(long appId) {
        return serviceCache.get(appId, this::createAiCodeGeneratorService).memory();
    }

    /** 文件工具持有调用级工作目录，因此工程服务按次构建，不能缓存到其他调用。 */
    public VueProjectAiService vueProjectService(Object tools, StreamingChatModel model) {
        return vueProjectService(new Object[] {tools}, model);
    }

    public VueProjectAiService vueProjectService(Object[] tools, StreamingChatModel model) {
        return AiServices.builder(VueProjectAiService.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(80))
                .tools(tools)
                .hallucinatedToolNameStrategy(
                        request ->
                                dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                                        request,
                                        "Error: there is no tool called " + request.name()))
                .maxSequentialToolsInvocations(60)
                .build();
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
    private CachedService createAiCodeGeneratorService(long appId) {
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
                        .streamingChatModel(getStreamingChatModel(CodeGenTypeEnum.HTML))
                        .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .outputGuardrails(new RetryOutputGuardrail())
                        .outputGuardrailsConfig(
                                OutputGuardrailsConfig.builder().maxRetries(3).build())
                        .build();
        return new CachedService(service, chatMemory);
    }
}


