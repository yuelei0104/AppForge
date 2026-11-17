package com.yuelei.service;

import com.yuelei.entity.User;
import com.yuelei.vo.ChatMemoryVO;

public interface ChatMemoryService {
    ChatMemoryVO getStatus(long appId, User user);

    ChatMemoryVO compress(long appId, User user);

    void afterConversationSaved(long appId);

    void invalidate(long appId);

    String buildPrompt(long appId, long beforeMessageId, String request);
}
