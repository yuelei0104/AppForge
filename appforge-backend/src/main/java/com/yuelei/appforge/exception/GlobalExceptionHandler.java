package com.yuelei.appforge.exception;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.guardrail.GuardrailErrors;
import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.common.ResultUtils;

import dev.langchain4j.guardrail.GuardrailException;

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
        var guardrail = GuardrailErrors.details(e);
        if (guardrail != null) return guardrailResponse(guardrail);
        log.error("BusinessException", e);
        if (handleSseError(Map.of("error", true, "code", e.getCode(), "message", e.getMessage()))) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        var guardrail = GuardrailErrors.details(e);
        if (guardrail != null) return guardrailResponse(guardrail);
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

    @ExceptionHandler(GuardrailException.class)
    public BaseResponse<?> guardrailExceptionHandler(GuardrailException error) {
        return guardrailResponse(GuardrailErrors.details(error));
    }

    private BaseResponse<?> guardrailResponse(Map<String, Object> details) {
        if (handleSseError(details)) return null;
        return new BaseResponse<>(
                (Integer) details.get("code"), details, (String) details.get("message"));
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
        // Never append a JSON response after SSE headers have already been committed.
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
