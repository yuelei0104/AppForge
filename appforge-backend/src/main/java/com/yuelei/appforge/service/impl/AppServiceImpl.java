package com.yuelei.appforge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuelei.appforge.ai.AiCodeGenTypeRoutingService;
import com.yuelei.appforge.constant.AppConstant;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.mapper.AppMapper;
import com.yuelei.appforge.model.dto.app.AppAddRequest;
import com.yuelei.appforge.model.dto.app.AppAdminUpdateRequest;
import com.yuelei.appforge.model.dto.app.AppQueryRequest;
import com.yuelei.appforge.model.dto.app.AppUpdateRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.enums.ChatHistoryMessageTypeEnum;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.model.vo.AppVO;
import com.yuelei.appforge.model.vo.UserVO;
import com.yuelei.appforge.service.AppService;
import com.yuelei.appforge.service.AttachmentService;
import com.yuelei.appforge.service.ChatHistoryService;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.workspace.*;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 应用服务层实现。
 *
 * @author yuelei
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource private com.yuelei.appforge.core.handler.StreamHandlerExecutor streamHandlerExecutor;

    @Resource private AppWorkspaceService workspace;

    @Resource private AppGenerationService generation;

    @Resource private AppCatalogSearch catalog;

    @Resource private AppNameService names;

    @Resource private UserService userService;

    @Resource private ChatHistoryService chatHistoryService;

    @Resource private AttachmentService attachmentService;

    @Resource private com.yuelei.appforge.cover.AppCoverTasks coverTasks;
    @Resource private org.springframework.transaction.support.TransactionTemplate coverTransaction;

    @Override
    public long addApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(
                loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验初始化 prompt
        String initPrompt = StrUtil.trim(appAddRequest.getInitPrompt());
        ThrowUtils.throwIf(
                StrUtil.isBlank(initPrompt) || initPrompt.length() > 2000,
                ErrorCode.PARAMS_ERROR,
                "初始提示词为 1 至 2000 字");

        // 应用名称不填时使用默认名称
        String appName = StrUtil.trim(appAddRequest.getAppName());
        if (StrUtil.isBlank(appName)) {
            appName = AppNameService.fallback(initPrompt);
        }
        ThrowUtils.throwIf(
                appName.length() > AppConstant.APP_NAME_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "应用名称不能超过 " + AppConstant.APP_NAME_MAX_LENGTH + " 个字符");

        // 封面可为空
        String cover = StrUtil.trim(appAddRequest.getCover());
        if (StrUtil.isBlank(cover)) {
            cover = null;
        }
        ThrowUtils.throwIf(
                cover != null && cover.length() > AppConstant.COVER_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "应用封面地址不能超过 " + AppConstant.COVER_MAX_LENGTH + " 个字符");

        // 兼容未指定模式的旧客户端；新首页显式传入用户所选模式。
        String codeGenType = StrUtil.trim(appAddRequest.getCodeGenType());
        if (StrUtil.isBlank(codeGenType)) {
            codeGenType = CodeGenTypeEnum.MULTI_FILE.getValue();
        }
        ThrowUtils.throwIf(
                CodeGenTypeEnum.getEnumByValue(codeGenType) == null,
                ErrorCode.PARAMS_ERROR,
                "不支持的代码生成类型");

        App app = new App();
        app.setAppName(appName);
        app.setCover(cover);
        app.setInitPrompt(initPrompt);
        app.setCodeGenType(codeGenType);
        app.setPriority(0);
        app.setDescription(AppPolicy.description(appAddRequest.getDescription()));
        app.setTags(AppPolicy.encodeTags(appAddRequest.getTags()));
        app.setVisibility(
                appAddRequest.getVisibility() == null
                        ? "PRIVATE"
                        : AppPolicy.visibility(appAddRequest.getVisibility()));
        app.setGenerationStatus("IDLE");
        app.setGenerationError("");
        app.setGenerationToken("");
        app.setCurrentVersion(0);
        app.setDeployedVersion(0);
        app.setDeploymentStatus("OFFLINE");
        app.setFeatureStatus("NONE");
        app.setFeatureReason("");
        app.setUserId(loginUser.getId());
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建应用失败");
        if (coverTasks != null && cover != null) coverTasks.manualChanged(app.getId());
        log.info("创建应用成功 appId={}, codeGenType={}", app.getId(), codeGenType);
        if (StrUtil.isBlank(appAddRequest.getAppName()))
            names.suggest(app.getId(), initPrompt, appName);
        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(
                appUpdateRequest == null || appUpdateRequest.getId() == null,
                ErrorCode.PARAMS_ERROR,
                "应用 id 不能为空");
        ThrowUtils.throwIf(appUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不合法");

        String appName = StrUtil.trim(appUpdateRequest.getAppName());
        ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        validateAppNameLength(appName);

        App app = getAppById(appUpdateRequest.getId());
        AppPolicy.manage(app, loginUser);

        // 普通用户仅能更新白名单字段，不能修改优先级或应用归属。
        App updateApp = new App();
        updateApp.setId(app.getId());
        updateApp.setAppName(appName);
        if (appUpdateRequest.getCover() != null
                && (coverTasks == null
                        || !coverTasks.isCurrentImageUrl(
                                app.getId(), appUpdateRequest.getCover()))) {
            updateApp.setCover(validateCover(appUpdateRequest.getCover()));
        }
        applyMetadata(
                updateApp,
                appUpdateRequest.getDescription(),
                appUpdateRequest.getTags(),
                appUpdateRequest.getVisibility());
        updateApp.setEditTime(LocalDateTime.now());
        boolean result = updateWithCover(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用失败");
        return true;
    }

    @Override
    public boolean deleteApp(long id, User loginUser) {
        App app = getAppById(id);
        checkOwnership(app, loginUser);
        return workspace.delete(id, loginUser);
    }

    @Override
    public AppVO getAppVOById(long id, User loginUser) {
        App app = getAppById(id);
        ThrowUtils.throwIf(
                loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 精选应用允许其他登录用户查看，写操作仍单独校验归属。
        ThrowUtils.throwIf(!AppPolicy.canRead(app, loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看该应用");
        if (AppPolicy.isOwner(app, loginUser)
                && Integer.valueOf(0).equals(app.getCurrentVersion())
                && "IDLE".equals(app.getGenerationStatus())) {
            int baseline = workspace.ensureBaseline(id);
            if (baseline > 0) {
                app.setCurrentVersion(baseline);
                app.setGenerationStatus("SUCCEEDED");
            }
        }
        AppVO vo = getAppVO(app);
        if (!AppPolicy.isOwner(app, loginUser) && !AppPolicy.isAdmin(loginUser)) {
            vo.setInitPrompt(null);
            vo.setGenerationError(null);
        }
        return vo;
    }

    @Override
    public Page<AppVO> listMyAppByPage(AppQueryRequest appQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(
                loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        validatePage(appQueryRequest, AppConstant.USER_PAGE_SIZE_LIMIT);

        return buildAppVOPage(
                catalog.search(appQueryRequest, loginUser.getId(), false, false),
                appQueryRequest.getPageNum(),
                appQueryRequest.getPageSize());
    }

    @Override
    public Page<AppVO> listFeaturedAppByPage(AppQueryRequest request) {
        return buildAppVOPage(
                catalog.search(request, null, true, true),
                request.getPageNum(),
                request.getPageSize());
    }

    @Override
    public Page<AppVO> listPublicAppByPage(AppQueryRequest request) {
        return buildAppVOPage(
                catalog.search(request, null, true, false),
                request.getPageNum(),
                request.getPageSize());
    }

    @Override
    public boolean adminDeleteApp(long id) {
        User admin = new User();
        admin.setId(1L);
        admin.setUserRole("admin");
        return workspace.delete(id, admin);
    }

    @Override
    public boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(
                appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null,
                ErrorCode.PARAMS_ERROR,
                "应用 id 不能为空");
        ThrowUtils.throwIf(appAdminUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不合法");

        App app = getAppById(appAdminUpdateRequest.getId());

        App updateApp = new App();
        updateApp.setId(app.getId());
        boolean hasUpdate = false;

        if (appAdminUpdateRequest.getAppName() != null) {
            String appName = StrUtil.trim(appAdminUpdateRequest.getAppName());
            ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
            validateAppNameLength(appName);
            updateApp.setAppName(appName);
            hasUpdate = true;
        }
        if (appAdminUpdateRequest.getCover() != null
                && (coverTasks == null
                        || !coverTasks.isCurrentImageUrl(
                                app.getId(), appAdminUpdateRequest.getCover()))) {
            updateApp.setCover(validateCover(appAdminUpdateRequest.getCover()));
            hasUpdate = true;
        }
        if (appAdminUpdateRequest.getPriority() != null) {
            ThrowUtils.throwIf(
                    appAdminUpdateRequest.getPriority() < 0, ErrorCode.PARAMS_ERROR, "优先级不能小于 0");
            updateApp.setPriority(appAdminUpdateRequest.getPriority());
            hasUpdate = true;
        }

        if (appAdminUpdateRequest.getDescription() != null
                || appAdminUpdateRequest.getTags() != null
                || appAdminUpdateRequest.getVisibility() != null) {
            applyMetadata(
                    updateApp,
                    appAdminUpdateRequest.getDescription(),
                    appAdminUpdateRequest.getTags(),
                    appAdminUpdateRequest.getVisibility());
            hasUpdate = true;
        }
        if (updateApp.getPriority() != null && updateApp.getPriority() >= 99) {
            ThrowUtils.throwIf(
                    !"PUBLIC".equals(updateApp.getVisibility()) && !AppPolicy.isPublic(app),
                    ErrorCode.PARAMS_ERROR,
                    "私有应用不能加入精选或置顶");
            updateApp.setFeatureStatus("APPROVED");
        } else if (updateApp.getPriority() != null) {
            updateApp.setFeatureStatus("NONE");
        }
        // 没有需要更新的字段，直接返回成功
        if (!hasUpdate) {
            return true;
        }

        updateApp.setEditTime(LocalDateTime.now());
        boolean result = updateWithCover(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用失败");
        return true;
    }

    @Override
    public Page<AppVO> listAppByPage(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        validatePage(appQueryRequest, 0);

        Page<App> page = catalog.search(appQueryRequest, null, false, false);
        Page<AppVO> result =
                buildAppVOPage(page, appQueryRequest.getPageNum(), appQueryRequest.getPageSize());
        Map<Long, App> originals =
                page.getRecords().stream().collect(Collectors.toMap(App::getId, value -> value));
        result.getRecords()
                .forEach(vo -> vo.setInitPrompt(originals.get(vo.getId()).getInitPrompt()));
        return result;
    }

    @Override
    public AppVO adminGetAppVOById(long id) {
        return getAppVO(getAppById(id));
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO, "tags");
        appVO.setTags(AppPolicy.decodeTags(app.getTags()));
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        if (coverTasks != null) coverTasks.decorate(List.of(appVO));
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream().map(App::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap =
                userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, userService::getUserVO));
        List<AppVO> views =
                appList.stream()
                        .map(
                                app -> {
                                    AppVO appVO = new AppVO();
                                    BeanUtil.copyProperties(app, appVO, "tags");
                                    appVO.setTags(AppPolicy.decodeTags(app.getTags()));
                                    // 列表不返回初始提示词或模型错误，减少公开信息暴露。
                                    appVO.setInitPrompt(null);
                                    appVO.setGenerationError(null);
                                    UserVO userVO = userVOMap.get(app.getUserId());
                                    appVO.setUser(userVO);
                                    return appVO;
                                })
                        .collect(Collectors.toList());
        if (coverTasks != null) coverTasks.decorate(views);
        return views;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId);
    }

    /** 根据 id 获取应用，不存在则抛异常 */
    private App getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不合法");
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        return app;
    }

    /** 校验应用归属 */
    private void checkOwnership(App app, User loginUser) {
        ThrowUtils.throwIf(
                loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(
                !Objects.equals(app.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR,
                "无权操作该应用");
    }

    /** 校验应用名称长度 */
    private void validateAppNameLength(String appName) {
        ThrowUtils.throwIf(
                appName.length() > AppConstant.APP_NAME_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "应用名称不能超过 " + AppConstant.APP_NAME_MAX_LENGTH + " 个字符");
    }

    /** 校验图片链接；空字符串用于清除封面。 */
    private String validateCover(String value) {
        String cover = StrUtil.trimToEmpty(value);
        ThrowUtils.throwIf(
                cover.length() > AppConstant.COVER_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "应用封面地址不能超过 " + AppConstant.COVER_MAX_LENGTH + " 个字符");
        if (cover.isEmpty()) {
            return cover;
        }
        try {
            URI uri = URI.create(cover);
            if (("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    && StrUtil.isNotBlank(uri.getHost())) {
                return cover;
            }
        } catch (IllegalArgumentException ignored) {
            // 统一转换为可展示的参数错误。
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "封面地址必须是有效的 HTTP 或 HTTPS 链接");
    }

    /**
     * 校验分页参数
     *
     * @param appQueryRequest 查询请求
     * @param maxPageSize 每页最大数量，0 表示不限制
     */
    private void validatePage(AppQueryRequest appQueryRequest, int maxPageSize) {
        ThrowUtils.throwIf(appQueryRequest.getPageNum() < 1, ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        ThrowUtils.throwIf(appQueryRequest.getPageSize() < 1, ErrorCode.PARAMS_ERROR, "每页数量必须大于 0");
        if (maxPageSize > 0) {
            ThrowUtils.throwIf(
                    appQueryRequest.getPageSize() > maxPageSize,
                    ErrorCode.PARAMS_ERROR,
                    "每页最多查询 " + maxPageSize + " 条");
        }
    }

    /** 将应用实体分页转换为 VO 分页 */
    private Page<AppVO> buildAppVOPage(Page<App> appPage, int pageNum, int pageSize) {
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        appVOPage.setRecords(getAppVOList(appPage.getRecords()));
        return appVOPage;
    }

    private void applyMetadata(
            App update, String description, List<String> tags, String visibility) {
        if (description != null) update.setDescription(AppPolicy.description(description));
        if (tags != null) update.setTags(AppPolicy.encodeTags(tags));
        if (visibility != null) {
            update.setVisibility(AppPolicy.visibility(visibility));
            if ("PRIVATE".equals(visibility)) {
                update.setPriority(0);
                update.setFeatureStatus("NONE");
                update.setFeatureReason("");
            }
        }
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        return chatToGenCode(appId, message, loginUser, event -> {});
    }

    @Override
    public Flux<String> chatToGenCode(
            Long appId,
            String message,
            User loginUser,
            java.util.function.Consumer<com.yuelei.appforge.workspace.GenerationProgress>
                    progress) {
        return chatToGenCode(appId, message, loginUser, progress, event -> {});
    }

    @Override
    public Flux<String> chatToGenCode(
            Long appId,
            String message,
            User loginUser,
            java.util.function.Consumer<com.yuelei.appforge.workspace.GenerationProgress>
                    progress,
            java.util.function.Consumer<com.yuelei.appforge.workspace.FileChangeEvent>
                    onFileChange) {
        return chatToGenCode(appId, message, loginUser, List.of(), progress, onFileChange);
    }

    @Override
    public Flux<String> chatToGenCode(
            Long appId,
            String message,
            User loginUser,
            List<Long> attachmentIds,
            java.util.function.Consumer<com.yuelei.appforge.workspace.GenerationProgress>
                    progress,
            java.util.function.Consumer<com.yuelei.appforge.workspace.FileChangeEvent>
                    onFileChange) {
        return chatToGenCode(
                appId, message, loginUser, attachmentIds, null, progress, onFileChange);
    }

    @Override
    public Flux<String> chatToGenCode(
            Long appId,
            String message,
            User loginUser,
            List<Long> attachmentIds,
            String element,
            java.util.function.Consumer<com.yuelei.appforge.workspace.GenerationProgress>
                    progress,
            java.util.function.Consumer<com.yuelei.appforge.workspace.FileChangeEvent>
                    onFileChange) {
        ThrowUtils.throwIf(
                appId == null || appId <= 0 || StrUtil.isBlank(message) || message.length() > 2000,
                ErrorCode.PARAMS_ERROR,
                "应用 ID 或消息无效，消息最多 2000 字");
        App app = getAppById(appId);
        AppPolicy.owner(app, loginUser);
        return Flux.defer(
                () -> {
                    StringBuilder response = new StringBuilder();
                    AtomicBoolean recorded = new AtomicBoolean();
                    AtomicLong userMessageId = new AtomicLong();
                    List<Long> ownedAttachmentIds =
                            attachmentIds == null ? List.of() : attachmentIds;
                    List<com.yuelei.appforge.model.entity.AppAttachment> attachments =
                            attachmentService == null
                                    ? List.of()
                                    : attachmentService.requireOwned(
                                            ownedAttachmentIds, appId, loginUser);
                    String elementBlock =
                            element == null || element.isBlank()
                                    ? ""
                                    : "\n\n<element_context>\n" + element + "\n</element_context>";
                    String prompt =
                            message
                                    + elementBlock
                                    + (attachmentService == null
                                            ? ""
                                            : attachmentService.buildPromptBlock(attachments));
                    var source =
                            generation.generate(
                                    app,
                                    prompt,
                                    () -> {
                                        userMessageId.set(
                                                chatHistoryService.appendChatMessage(
                                                        appId,
                                                        message,
                                                        ChatHistoryMessageTypeEnum.USER.getValue(),
                                                        loginUser.getId()));
                                        try {
                                            if (attachmentService != null
                                                    && !ownedAttachmentIds.isEmpty()) {
                                                attachmentService.bindToMessage(
                                                        ownedAttachmentIds,
                                                        userMessageId.get(),
                                                        loginUser.getId());
                                            }
                                        } catch (RuntimeException bindError) {
                                            log.warn(
                                                    "绑定聊天附件失败 appId={} userMessageId={}",
                                                    appId,
                                                    userMessageId.get(),
                                                    bindError);
                                        }
                                        recorded.set(true);
                                    },
                                    userMessageId::get,
                                    progress,
                                    onFileChange);
                    return streamHandlerExecutor
                            .doExecute(
                                    source,
                                    CodeGenTypeEnum.getEnumByValue(app.getCodeGenType()),
                                    response::append)
                            .doOnComplete(
                                    () -> {
                                        if (!response.isEmpty()) {
                                            ThrowUtils.throwIf(
                                                    !chatHistoryService.addChatMessage(
                                                            appId,
                                                            response.toString(),
                                                            ChatHistoryMessageTypeEnum.AI
                                                                    .getValue(),
                                                            loginUser.getId()),
                                                    ErrorCode.OPERATION_ERROR,
                                                    "保存 AI 消息失败");
                                            generation.afterConversationSaved(
                                                    appId, message, response.toString());
                                        }
                                    })
                            .doOnError(
                                    error -> {
                                        if (recorded.get()) {
                                            String text =
                                                    error
                                                                    instanceof
                                                                    AppGenerationService
                                                                            .GenerationStoppedException
                                                            ? "生成已停止，未保存不完整代码"
                                                            : "AI 回复失败："
                                                                    + AppGenerationService
                                                                            .safeError(error);
                                            try {
                                                chatHistoryService.addChatMessage(
                                                        appId,
                                                        text,
                                                        ChatHistoryMessageTypeEnum.AI.getValue(),
                                                        loginUser.getId());
                                            } catch (RuntimeException persistenceError) {
                                                log.error(
                                                        "保存失败消息出错 appId={}",
                                                        appId,
                                                        persistenceError);
                                            }
                                        }
                                    });
                });
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        AppPolicy.owner(getAppById(appId), loginUser);
        return workspace.deploy(appId, loginUser);
    }

    /** Compatibility entry point: screenshot targets are always fixed database versions. */
    @Override
    public void generateAppScreenshotAsync(Long appId, String ignoredUrl) {
        App app = getAppById(appId);
        coverTasks.enqueue(
                appId, app.getCurrentVersion() == null ? 0 : app.getCurrentVersion(), "REPAIR");
    }

    private boolean updateWithCover(App update) {
        if (coverTasks == null || !coverTasks.enabled() || update.getCover() == null)
            return this.updateById(update);
        return Boolean.TRUE.equals(
                coverTransaction.execute(
                        status -> {
                            coverTasks.manualChanged(update.getId());
                            return this.updateById(update);
                        }));
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

}
