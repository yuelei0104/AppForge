package com.yuelei.appforge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatStatisticsVO {
    private long roundCount;
    private long messageCount;
    private long aiMessageCount;
    private LocalDateTime lastMessageTime;
}
