package com.yuelei.controller;

import com.yuelei.annotation.AuthCheck;
import com.yuelei.common.BaseResponse;
import com.yuelei.common.ResultUtils;
import com.yuelei.constant.UserConstant;
import com.yuelei.dto.user.PasswordResetCodeRequest;
import com.yuelei.dto.user.PasswordResetRequest;
import com.yuelei.vo.PasswordResetCodeVO;
import com.yuelei.service.PasswordRecoveryService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/password")
public class PasswordRecoveryController {
    @Resource private PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/reset-code")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PasswordResetCodeVO> issue(
            @RequestBody PasswordResetCodeRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return ResultUtils.success(passwordRecoveryService.issue(body, request));
    }

    @PostMapping("/reset")
    public BaseResponse<Boolean> reset(
            @RequestBody PasswordResetRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        passwordRecoveryService.reset(body, request);
        return ResultUtils.success(true);
    }
}
