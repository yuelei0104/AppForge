package com.yuelei.exception;

import cn.hutool.json.JSONUtil;
import com.yuelei.common.BaseResponse;
import com.yuelei.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        if (handleSseError(
                Map.of("error", true, "code", e.getCode(), "message", e.getMessage()))) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(
                Map.of(
                        "error",
                        true,
                        "code",
                        ErrorCode.SYSTEM_ERROR.getCode(),
                        "message",
                        "系统错误"))) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /** Before emitter creation, errors still use the same named SSE error contract. */
    private boolean handleSseError(Map<String, Object> details) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return false;
        HttpServletRequest request = servlet.getRequest();
        HttpServletResponse response = servlet.getResponse();
        String accept = request.getHeader("Accept");
        boolean stream =
                (accept != null && accept.contains("text/event-stream"))
                        || request.getRequestURI().endsWith("/chat/gen/code");
        if (!stream || response == null) return false;
        if (response.isCommitted()) return true;
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter()
                    .write("event: business-error\ndata: " + JSONUtil.toJsonStr(details) + "\n\n");
            response.getWriter().write("event: done\ndata: {}\n\n");
            response.getWriter().flush();
        } catch (IOException closed) {
            log.debug("SSE error receiver disconnected");
        }
        return true;
    }
}
