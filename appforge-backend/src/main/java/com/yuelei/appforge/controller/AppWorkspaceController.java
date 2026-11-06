package com.yuelei.appforge.controller;

import com.yuelei.appforge.annotation.AuthCheck;
import com.yuelei.appforge.common.*;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.workspace.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/app")
public class AppWorkspaceController {
    @Resource private AppWorkspaceService workspace;
    @Resource private AppGenerationService generation;
    @Resource private UserService userService;

    private long id(WorkspaceRequest body) {
        ThrowUtils.throwIf(
                body == null || body.getAppId() == null || body.getAppId() <= 0,
                ErrorCode.PARAMS_ERROR,
                "应用 ID 无效");
        return body.getAppId();
    }

    @GetMapping("/version/list")
    public BaseResponse<List<AppVersionVO>> versions(
            @RequestParam long appId, HttpServletRequest request) {
        return ResultUtils.success(workspace.versions(appId, userService.getLoginUser(request)));
    }

    @GetMapping("/version/files")
    public BaseResponse<Map<String, String>> files(
            @RequestParam long appId, @RequestParam int version, HttpServletRequest request) {
        return ResultUtils.success(
                workspace.versionFiles(appId, version, userService.getLoginUser(request)));
    }

    @PostMapping("/version/rollback")
    public BaseResponse<Integer> rollback(
            @RequestBody WorkspaceRequest body, HttpServletRequest request) {
        long id = id(body);
        ThrowUtils.throwIf(
                body.getVersion() == null || body.getVersion() < 1,
                ErrorCode.PARAMS_ERROR,
                "版本号无效");
        return ResultUtils.success(
                workspace.rollback(id, body.getVersion(), userService.getLoginUser(request)));
    }

    @PostMapping("/generation/stop")
    public BaseResponse<Boolean> stop(
            @RequestBody WorkspaceRequest body, HttpServletRequest request) {
        return ResultUtils.success(generation.stop(id(body), userService.getLoginUser(request)));
    }

    @PostMapping("/undeploy")
    public BaseResponse<Boolean> offline(
            @RequestBody WorkspaceRequest body, HttpServletRequest request) {
        return ResultUtils.success(workspace.offline(id(body), userService.getLoginUser(request)));
    }

    @PostMapping("/feature/apply")
    public BaseResponse<Boolean> apply(
            @RequestBody WorkspaceRequest body, HttpServletRequest request) {
        return ResultUtils.success(
                workspace.feature(id(body), userService.getLoginUser(request), null, null));
    }

    @PostMapping("/admin/feature/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> review(
            @RequestBody WorkspaceRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(
                body == null || body.getApproved() == null, ErrorCode.PARAMS_ERROR, "请选择审核结果");
        return ResultUtils.success(
                workspace.feature(
                        id(body),
                        userService.getLoginUser(request),
                        body.getApproved(),
                        body.getReason()));
    }
}
