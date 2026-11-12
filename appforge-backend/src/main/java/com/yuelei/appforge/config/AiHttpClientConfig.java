package com.yuelei.appforge.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * AI HTTP 客户端配置。
 *
 * <p>LangChain4j 的 Spring Boot Starter 默认使用 SpringRestClient。部分 OpenAI 兼容服务
 * （例如 DeepSeek）返回 chunked 响应时，SpringRestClient 可能只读到首个分片，导致 JSON
 * 解析出现 {@code Unexpected end-of-input}。这里把同步 ChatModel 改用 JDK HttpClient。</p>
 */
@Configuration
public class AiHttpClientConfig {

    @Bean
    @Primary
    public ChatModel aiChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName) {
        return OpenAiChatModel.builder()
                .httpClientBuilder(
                        JdkHttpClient.builder().connectTimeout(Duration.ofSeconds(30)))
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .reasoningEffort("none")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(3)
                .build();
    }
}
