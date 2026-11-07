package com.yuelei.appforge.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {

    private String host;

    private int port;

    private String password;

    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
            .host(host)
            .port(port)
            .password(password)
            .ttl(ttl)
            .build();
    }

    /**
     * 按 memoryId（appId）创建独立的会话记忆，同一应用共享上下文。
     *
     * @param redisChatMemoryStore Redis 记忆存储
     * @return 记忆提供器
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .chatMemoryStore(redisChatMemoryStore)
            .maxMessages(20)
            .build();
    }
}
