package com.yuelei.appforge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.mapper.ChatHistoryMapper;
import com.yuelei.appforge.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.ChatHistory;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.enums.ChatHistoryMessageTypeEnum;
import com.yuelei.appforge.model.vo.ChatStatisticsVO;
import com.yuelei.appforge.service.AppService;
import com.yuelei.appforge.service.ChatHistoryService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 服务层实现。
 *
 * @author yuelei
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>
        implements ChatHistoryService {

    @Resource private JdbcTemplate jdbcTemplate;

    @Resource @Lazy private AppService appService;

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        return appendChatMessage(appId, message, messageType, userId) > 0;
    }

    @Override
    public long appendChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum =
                ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(
                messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        ChatHistory chatHistory =
                ChatHistory.builder()
                        .appId(appId)
                        .message(message)
                        .messageType(messageType)
                        .userId(userId)
                        .build();
        ThrowUtils.throwIf(!this.save(chatHistory), ErrorCode.OPERATION_ERROR, "保存消息失败");
        return chatHistory.getId();
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create().eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper
                .eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            ThrowUtils.throwIf(
                    !Set.of("id", "createTime", "updateTime").contains(sortField),
                    ErrorCode.PARAMS_ERROR,
                    "不支持的排序字段");
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder) || "asc".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        queryWrapper.orderBy("id", "ascend".equals(sortOrder) || "asc".equals(sortOrder));
        return queryWrapper;
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(
            Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser) {
        validateAccess(appId, pageSize, loginUser);
        return queryHistory(appId, pageSize, lastCreateTime, null);
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(
            Long appId, int pageSize, LocalDateTime lastCreateTime, Long lastId, User loginUser) {
        ThrowUtils.throwIf(
                lastId != null && (lastId <= 0 || lastCreateTime == null),
                ErrorCode.PARAMS_ERROR,
                "历史游标无效");
        // 复用原接口的权限和分页校验，避免游标入口绕过权限。
        validateAccess(appId, pageSize, loginUser);
        return queryHistory(appId, pageSize, lastCreateTime, lastId);
    }

    private void validateAccess(Long appId, int pageSize, User loginUser) {
        ThrowUtils.throwIf(
                appId == null || appId <= 0 || pageSize < 1 || pageSize > 50,
                ErrorCode.PARAMS_ERROR);
        requireChatAccess(appId, loginUser);
    }

    @Override
    public App requireChatAccess(Long appId, User user) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(
                !UserConstant.ADMIN_ROLE.equals(user.getUserRole())
                        && !Objects.equals(app.getUserId(), user.getId()),
                ErrorCode.NO_AUTH_ERROR,
                "无权查看该应用的对话历史");
        return app;
    }

    @Override
    public ChatStatisticsVO statistics(Long appId, User user) {
        requireChatAccess(appId, user);
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) messageCount,COALESCE(SUM(CASE WHEN messageType='user' THEN 1 ELSE"
                    + " 0 END),0) roundCount,COALESCE(SUM(CASE WHEN messageType='ai' THEN 1 ELSE 0"
                    + " END),0) aiMessageCount,MAX(createTime) lastMessageTime FROM chat_history"
                    + " WHERE appId=? AND isDelete=0",
                (rs, n) -> {
                    var result = new ChatStatisticsVO();
                    result.setRoundCount(rs.getLong("roundCount"));
                    result.setMessageCount(rs.getLong("messageCount"));
                    result.setAiMessageCount(rs.getLong("aiMessageCount"));
                    var last = rs.getTimestamp("lastMessageTime");
                    if (last != null) result.setLastMessageTime(last.toLocalDateTime());
                    return result;
                },
                appId);
    }

    @Override
    public void exportMarkdown(Long appId, User user, Writer writer) throws IOException {
        App app = requireChatAccess(appId, user);
        Long ceiling =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(id),0) FROM chat_history WHERE appId=? AND isDelete=0",
                        Long.class,
                        appId);
        writer.write(
                "# "
                        + Objects.toString(app.getAppName(), "未命名应用").replaceAll("[\\r\\n]", " ")
                        + " · 对话记录\n\n应用 ID：`"
                        + appId
                        + "`\n\n导出时间："
                        + LocalDateTime.now()
                        + "\n\n");
        LocalDateTime cursorTime = null;
        long cursorId = 0, index = 0;
        while (true) {
            var params = new ArrayList<Object>();
            params.add(appId);
            params.add(ceiling);
            String sql =
                    "SELECT id,message,messageType,appId,userId,createTime,updateTime FROM"
                        + " chat_history WHERE appId=? AND id<=? AND isDelete=0";
            if (cursorTime != null) {
                sql += " AND (createTime>? OR (createTime=? AND id>?))";
                params.add(cursorTime);
                params.add(cursorTime);
                params.add(cursorId);
            }
            var batch =
                    jdbcTemplate.query(
                            sql + " ORDER BY createTime ASC,id ASC LIMIT 20",
                            BeanPropertyRowMapper.newInstance(ChatHistory.class),
                            params.toArray());
            if (batch.isEmpty()) break;
            for (ChatHistory item : batch) {
                writer.write(
                        "## "
                                + (++index)
                                + ". "
                                + ("user".equals(item.getMessageType()) ? "用户" : "AI")
                                + " · "
                                + item.getCreateTime()
                                + "\n\n");
                writer.write(Objects.toString(item.getMessage(), ""));
                writer.write("\n\n---\n\n");
            }
            writer.flush();
            var last = batch.getLast();
            cursorTime = last.getCreateTime();
            cursorId = last.getId();
        }
        if (index == 0) writer.write("暂无对话记录。\n");
    }

    private Page<ChatHistory> queryHistory(
            Long appId, int pageSize, LocalDateTime lastCreateTime, Long lastId) {
        String sql =
                "SELECT id,message,messageType,appId,userId,createTime,updateTime FROM chat_history"
                        + " WHERE appId=? AND isDelete=0";
        List<Object> params = new ArrayList<>();
        params.add(appId);
        if (lastCreateTime != null) {
            if (lastId != null) {
                sql += " AND (createTime < ? OR (createTime = ? AND id < ?))";
                params.add(lastCreateTime);
                params.add(lastCreateTime);
                params.add(lastId);
            } else {
                sql += " AND createTime < ?";
                params.add(lastCreateTime);
            }
        }
        sql += " ORDER BY createTime DESC,id DESC LIMIT ?";
        params.add(pageSize);
        var records =
                jdbcTemplate.query(
                        sql,
                        BeanPropertyRowMapper.newInstance(ChatHistory.class),
                        params.toArray());
        Page<ChatHistory> page = Page.of(1, pageSize);
        page.setRecords(records);
        page.setTotalRow(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM chat_history WHERE appId=? AND isDelete=0",
                        Long.class,
                        appId));
        return page;
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        return loadChatHistoryToMemory(appId, chatMemory, maxCount, true);
    }

    @Override
    public int loadChatHistoryToMemory(
            Long appId, MessageWindowChatMemory chatMemory, int maxCount, boolean skipLatest) {
        try {
            // 生成开始时排除本轮用户消息；完整回复入库后从最新一条恢复。
            QueryWrapper queryWrapper =
                    QueryWrapper.create()
                            .eq(ChatHistory::getAppId, appId)
                            .orderBy(ChatHistory::getCreateTime, false)
                            .orderBy(ChatHistory::getId, false)
                            .limit(skipLatest ? 1 : 0, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                chatMemory.clear();
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）
            historyList = historyList.reversed();
            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

}

