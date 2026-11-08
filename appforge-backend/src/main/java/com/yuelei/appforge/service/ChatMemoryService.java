package com.yuelei.appforge.service;

import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.ChatMemoryVO;

public interface ChatMemoryService {
    ChatMemoryVO getStatus(long appId, User user);

    ChatMemoryVO compress(long appId, User user);

    void afterConversationSaved(long appId);

    void invalidate(long appId);

    String buildPrompt(long appId, long beforeMessageId, String request);
}
