package com.yuelei.appforge.config;

import jakarta.annotation.PostConstruct;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 摘要配置独立于 Redis 登录 Session 和 Redis 记忆 TTL。 */
@Data
@Component
@ConfigurationProperties(prefix = "appforge.chat-memory")
public class ChatMemoryProperties {
    private boolean enabled = true;
    private int triggerMessages = 20;
    private int recentMessages = 8;
    private int maxInputCharacters = 60000;
    private int maxSummaryCharacters = 6000;

    @PostConstruct
    public void validate() {
        if (recentMessages < 2
                || recentMessages > 20
                || triggerMessages <= recentMessages
                || triggerMessages > 200
                || maxInputCharacters < 10000
                || maxInputCharacters > 200000
                || maxSummaryCharacters < 200
                || maxSummaryCharacters > 12000
                || maxSummaryCharacters >= maxInputCharacters / 2)
            throw new IllegalArgumentException(
                    "appforge.chat-memory 配置无效：近期消息 2-20 条，触发阈值大于近期条数且不超过 200，输入 10000-200000 字，摘要"
                        + " 200-12000 字且小于输入预算的一半");
    }
}
