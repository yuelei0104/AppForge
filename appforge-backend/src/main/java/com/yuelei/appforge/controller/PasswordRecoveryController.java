package com.yuelei.appforge.controller;

import com.yuelei.appforge.annotation.AuthCheck;
import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.common.ResultUtils;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.model.dto.user.PasswordResetCodeRequest;
import com.yuelei.appforge.model.dto.user.PasswordResetRequest;
import com.yuelei.appforge.model.vo.PasswordResetCodeVO;
import com.yuelei.appforge.service.PasswordRecoveryService;

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
