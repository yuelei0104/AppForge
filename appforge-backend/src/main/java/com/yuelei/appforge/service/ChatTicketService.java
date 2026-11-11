package com.yuelei.appforge.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.model.dto.chat.ChatRequest;
import com.yuelei.appforge.model.entity.User;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 一次性聊天票据：EventSource 不能自定义 Header/携带 POST body，先用普通请求换 ticket。
 *
 * <p>票据绑定用户与应用，5 分钟过期，消费一次即删除，避免把长消息和附件 id 暴露在 URL 日志里。</p>
 */
@Service
public class ChatTicketService {

    private static final String PREFIX = "appforge:chat:ticket:";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    public String create(ChatRequest request, User user) {
        ThrowUtils.throwIf(request == null || request.appId() == null, ErrorCode.PARAMS_ERROR);
        String id = UUID.randomUUID().toString().replace("-", "");
        JSONObject value = new JSONObject();
        value.set("userId", user.getId());
        value.set("appId", request.appId());
        value.set("message", request.message());
        value.set("attachmentIds", request.attachmentIds());
        value.set("element", request.element());
        redisTemplate.opsForValue().set(PREFIX + id, value.toString(), TTL);
        return id;
    }

    public ChatRequest consume(String ticket, User user) {
        ThrowUtils.throwIf(ticket == null || ticket.isBlank(), ErrorCode.PARAMS_ERROR, "缺少聊天票据");
        String key = PREFIX + ticket;
        String value = redisTemplate.opsForValue().get(key);
        ThrowUtils.throwIf(value == null, ErrorCode.PARAMS_ERROR, "聊天票据已过期，请重新发送");
        JSONObject json = JSONUtil.parseObj(value);
        ThrowUtils.throwIf(!user.getId().equals(json.getLong("userId")), ErrorCode.NO_AUTH_ERROR,
                "聊天票据不属于当前用户");
        // 校验归属后再删除，避免他人用票据 id 消耗掉合法用户的请求。
        redisTemplate.delete(key);
        List<Long> ids =
                json.getJSONArray("attachmentIds") == null
                        ? List.of()
                        : json.getJSONArray("attachmentIds").toList(Long.class);
        return new ChatRequest(
                json.getLong("appId"),
                json.getStr("message"),
                ids,
                json.getStr("element"));
    }
}
