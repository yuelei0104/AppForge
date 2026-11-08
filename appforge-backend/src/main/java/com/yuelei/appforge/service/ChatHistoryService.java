package com.yuelei.appforge.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yuelei.appforge.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.ChatHistory;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.ChatStatisticsVO;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;

/**
 * 服务层。
 *
 * @author yuelei
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    App requireChatAccess(Long appId, User user);

    ChatStatisticsVO statistics(Long appId, User user);

    void exportMarkdown(Long appId, User user, Writer writer) throws IOException;

    long appendChatMessage(Long appId, String message, String messageType, Long userId);

    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    boolean deleteByAppId(Long appId);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(
            Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser);

    Page<ChatHistory> listAppChatHistoryByPage(
            Long appId, int pageSize, LocalDateTime lastCreateTime, Long lastId, User loginUser);

    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    int loadChatHistoryToMemory(
            Long appId, MessageWindowChatMemory chatMemory, int maxCount, boolean skipLatest);
}

