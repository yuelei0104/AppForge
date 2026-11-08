package com.yuelei.appforge.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMemoryVO {
    private boolean enabled;
    private boolean running;
    private int triggerMessages;
    private int recentMessages;
    private long coveredMessages;
    private String summary;
    private String lastError;
    private LocalDateTime updatedTime;
}
