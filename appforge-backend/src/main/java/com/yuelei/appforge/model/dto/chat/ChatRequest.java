package com.yuelei.appforge.model.dto.chat;

import java.util.List;

/** 创建一次性 SSE 聊天票据；业务参数不放在 EventSource URL 上。 */
public record ChatRequest(
        Long appId, String message, List<Long> attachmentIds, String element) {}
