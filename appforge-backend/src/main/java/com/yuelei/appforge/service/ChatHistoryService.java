package com.yuelei.appforge.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yuelei.appforge.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yuelei.appforge.model.entity.ChatHistory;
import com.yuelei.appforge.model.entity.User;

import java.time.LocalDateTime;

/**
 *  服务层。
 *
 * @author yuelei
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    boolean deleteByAppId(Long appId);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser)
}
