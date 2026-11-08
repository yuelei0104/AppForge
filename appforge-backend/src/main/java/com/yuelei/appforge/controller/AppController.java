package com.yuelei.appforge.controller;

import cn.hutool.json.JSONUtil;

import com.mybatisflex.core.paginate.Page;
import com.yuelei.appforge.annotation.AuthCheck;
import com.yuelei.appforge.common.*;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.dto.app.*;
import com.yuelei.appforge.model.vo.AppVO;
import com.yuelei.appforge.service.*;
import com.yuelei.appforge.workspace.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import reactor.core.Disposable;
import reactor.core.publisher.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/app")
public class AppController {
    @Resource private AppService appService;
    @Resource private UserService userService;
    @Resource private AppWorkspaceService workspace;

    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest body, HttpServletRequest request) {
        return ResultUtils.success(appService.addApp(body, userService.getLoginUser(request)));
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(
            @RequestBody AppUpdateRequest body, HttpServletRequest request) {
        return ResultUtils.success(appService.updateApp(body, userService.getLoginUser(request)));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(
            @RequestBody DeleteRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        var user = userService.getLoginUser(request);
        return ResultUtils.success(
                AppPolicy.isAdmin(user)
                        ? appService.adminDeleteApp(body.getId())
                        : appService.deleteApp(body.getId(), user));
    }

    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id, HttpServletRequest request) {
        return ResultUtils.success(appService.getAppVOById(id, userService.getLoginUser(request)));
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(
            @RequestBody AppQueryRequest body, HttpServletRequest request) {
        return ResultUtils.success(
                appService.listMyAppByPage(body, userService.getLoginUser(request)));
    }

    @PostMapping("/good/list/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest body) {
        return ResultUtils.success(appService.listFeaturedAppByPage(body));
    }

    @PostMapping("/public/list/page/vo")
    public BaseResponse<Page<AppVO>> listPublicAppVOByPage(@RequestBody AppQueryRequest body) {
        return ResultUtils.success(appService.listPublicAppByPage(body));
    }

    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest body) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.adminDeleteApp(body.getId()));
    }

    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest body) {
        return ResultUtils.success(appService.adminUpdateApp(body));
    }

    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest body) {
        return ResultUtils.success(appService.listAppByPage(body));
    }

    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        return ResultUtils.success(appService.adminGetAppVOById(id));
    }

    @GetMapping("/source/download")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource>
            downloadSource(@RequestParam long appId, HttpServletRequest request)
                    throws java.io.IOException {
        var app = workspace.requireApp(appId);
        AppPolicy.manage(app, userService.getLoginUser(request));
        ThrowUtils.throwIf(
                !"vue_project".equals(app.getCodeGenType())
                        || app.getCurrentVersion() == null
                        || app.getCurrentVersion() < 1,
                ErrorCode.OPERATION_ERROR,
                "暂无可下载的工程");
        var files =
                workspace.versionFiles(
                        appId, app.getCurrentVersion(), userService.getLoginUser(request));
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip =
                new java.util.zip.ZipOutputStream(bytes, java.nio.charset.StandardCharsets.UTF_8)) {
            for (var entry : files.entrySet()) {
                String path =
                        com.yuelei.appforge.workspace.vue.VueProjectFiles.sourcePath(
                                entry.getKey());
                zip.putNextEntry(new java.util.zip.ZipEntry(path));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=app-" + appId + "-source.zip")
                .header("Cache-Control", "no-store")
                .body(new org.springframework.core.io.ByteArrayResource(bytes.toByteArray()));
    }

    /** SSE 错误始终以事件返回，避免异常处理器将 JSON 写入 text/event-stream。 */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatToGenCode(
            @RequestParam Long appId, @RequestParam String message, HttpServletRequest request) {
        SseEmitter emitter =
                new SseEmitter(0L) {
                    @Override
                    protected void extendResponse(
                            org.springframework.http.server.ServerHttpResponse response) {
                        super.extendResponse(response);
                        response.getHeaders()
                                .setContentType(
                                        new MediaType(
                                                MediaType.TEXT_EVENT_STREAM,
                                                java.nio.charset.StandardCharsets.UTF_8));
                        response.getHeaders().setCacheControl("no-store");
                    }
                };
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        Runnable cancel =
                () -> {
                    var current = subscription.get();
                    if (current != null) current.dispose();
                };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());
        var flux =
                Flux.defer(
                        () ->
                                appService.chatToGenCode(
                                        appId,
                                        message,
                                        userService.getLoginUser(request),
                                        event -> {
                                            try {
                                                emitter.send(
                                                        SseEmitter.event()
                                                                .name("progress")
                                                                .data(
                                                                        JSONUtil.toJsonStr(event.toMap()),
                                                                        MediaType
                                                                                .APPLICATION_JSON));
                                            } catch (Exception closed) {
                                                throw new BusinessException(
                                                        ErrorCode.OPERATION_ERROR, "连接已关闭");
                                            }
                                        }));
        subscription.set(
                flux.subscribe(
                        chunk -> {
                            try {
                                emitter.send(
                                        SseEmitter.event()
                                                .data(
                                                        JSONUtil.toJsonStr(Map.of("d", chunk)),
                                                        MediaType.APPLICATION_JSON));
                            } catch (Exception closed) {
                                cancel.run();
                                emitter.complete();
                            }
                        },
                        error -> {
                            boolean stopped =
                                    error
                                            instanceof
                                            AppGenerationService.GenerationStoppedException;
                            int code =
                                    error instanceof BusinessException business
                                            ? business.getCode()
                                            : ErrorCode.SYSTEM_ERROR.getCode();
                            String text = stopped ? "生成已停止" : AppGenerationService.safeError(error);
                            try {
                                emitter.send(
                                        SseEmitter.event()
                                                .name(stopped ? "stopped" : "generation-error")
                                                .data(
                                                        JSONUtil.toJsonStr(
                                                                Map.of(
                                                                        "code", code, "message",
                                                                        text)),
                                                        MediaType.APPLICATION_JSON));
                            } catch (Exception closed) {
                                /* 客户端已经断开。 */
                            }
                            emitter.complete();
                        },
                        () -> {
                            try {
                                emitter.send(
                                        SseEmitter.event()
                                                .name("done")
                                                .data("{}", MediaType.APPLICATION_JSON));
                            } catch (Exception closed) {
                                /* 客户端已经断开。 */
                            }
                            emitter.complete();
                        }));
        return emitter;
    }

    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(
            @RequestBody AppDeployRequest body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        String key = appService.deployApp(body.getAppId(), userService.getLoginUser(request));
        return ResultUtils.success(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/deploy/")
                        .path(key)
                        .path("/")
                        .toUriString());
    }
}
