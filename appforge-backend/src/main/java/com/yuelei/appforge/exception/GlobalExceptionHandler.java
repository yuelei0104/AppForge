package com.yuelei.appforge.exception;

import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 未登录、数据不存在属于正常的业务分支，不打印完整堆栈，避免刷屏
        if (e.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode()
            || e.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()) {
            log.warn("BusinessException: {}", e.getMessage());
        } else {
            log.error("BusinessException", e);
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

