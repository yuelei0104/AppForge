package com.yuelei.appforge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuelei.appforge.constant.AppConstant;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;
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
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.model.vo.AppVO;
import com.yuelei.appforge.model.vo.UserVO;
import com.yuelei.appforge.service.AppService;
import com.yuelei.appforge.service.UserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;

import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用服务层实现。
 *
 * @author yuelei
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    /**
     * 管理员列表允许排序的字段，避免直接拼接未校验的字段名
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
        "id", "appName", "cover", "initPrompt", "codeGenType",
        "deployKey", "priority", "userId", "editTime", "createTime", "updateTime"
    );

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Override
    public long addApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验初始化 prompt
        String initPrompt = StrUtil.trim(appAddRequest.getInitPrompt());
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        // 应用名称不填时使用默认名称
        String appName = StrUtil.trim(appAddRequest.getAppName());
        if (StrUtil.isBlank(appName)) {
            appName = AppConstant.DEFAULT_APP_NAME;
        }
        ThrowUtils.throwIf(
            appName.length() > AppConstant.APP_NAME_MAX_LENGTH,
            ErrorCode.PARAMS_ERROR,
            "应用名称不能超过 " + AppConstant.APP_NAME_MAX_LENGTH + " 个字符"
        );

        // 封面可为空
        String cover = StrUtil.trim(appAddRequest.getCover());
        if (StrUtil.isBlank(cover)) {
            cover = null;
        }
        ThrowUtils.throwIf(
            cover != null && cover.length() > AppConstant.COVER_MAX_LENGTH,
            ErrorCode.PARAMS_ERROR,
            "应用封面地址不能超过 " + AppConstant.COVER_MAX_LENGTH + " 个字符"
        );

        // 代码生成类型不填时默认 HTML 模式
        String codeGenType = StrUtil.trim(appAddRequest.getCodeGenType());
        if (StrUtil.isBlank(codeGenType)) {
            codeGenType = CodeGenTypeEnum.HTML.getValue();
        }
        ThrowUtils.throwIf(
            CodeGenTypeEnum.getEnumByValue(codeGenType) == null,
            ErrorCode.PARAMS_ERROR,
            "不支持的代码生成类型"
        );

        App app = new App();
        app.setAppName(appName);
        app.setCover(cover);
        app.setInitPrompt(initPrompt);
        app.setCodeGenType(codeGenType);
        app.setPriority(0);
        app.setUserId(loginUser.getId());
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建应用失败");
        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null,
            ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(appUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不合法");

        String appName = StrUtil.trim(appUpdateRequest.getAppName());
        ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        validateAppNameLength(appName);

        App app = getAppById(appUpdateRequest.getId());
        checkOwnership(app, loginUser);

        // 普通用户仅能更新白名单字段，不能修改优先级或应用归属。
        App updateApp = new App();
        updateApp.setId(app.getId());
        updateApp.setAppName(appName);
        if (appUpdateRequest.getCover() != null) {
            updateApp.setCover(validateCover(appUpdateRequest.getCover()));
        }
        updateApp.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用失败");
        return true;
    }

    @Override
    public boolean deleteApp(long id, User loginUser) {
        App app = getAppById(id);
        checkOwnership(app, loginUser);
        boolean result = this.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        return true;
    }

    @Override
    public AppVO getAppVOById(long id, User loginUser) {
        App app = getAppById(id);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 精选应用允许其他登录用户查看，写操作仍单独校验归属。
        ThrowUtils.throwIf(
            !Objects.equals(app.getUserId(), loginUser.getId())
                && !Objects.equals(app.getPriority(), AppConstant.GOOD_APP_PRIORITY),
            ErrorCode.NO_AUTH_ERROR,
            "无权查看该应用"
        );
        return getAppVO(app);
    }

    @Override
    public Page<AppVO> listMyAppByPage(AppQueryRequest appQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        validatePage(appQueryRequest, AppConstant.USER_PAGE_SIZE_LIMIT);

        QueryWrapper queryWrapper = QueryWrapper.create()
            .eq("userId", loginUser.getId())
            .orderBy("createTime", false);
        String appName = StrUtil.trim(appQueryRequest.getAppName());
        if (StrUtil.isNotBlank(appName)) {
            queryWrapper.like("appName", appName);
        }

        Page<App> appPage = this.page(
            Page.of(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()),
            queryWrapper
        );
        return buildAppVOPage(appPage, appQueryRequest.getPageNum(), appQueryRequest.getPageSize());
    }

    @Override
    public Page<AppVO> listFeaturedAppByPage(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        validatePage(appQueryRequest, AppConstant.USER_PAGE_SIZE_LIMIT);

        QueryWrapper queryWrapper = QueryWrapper.create()
            .gt("priority", 0)
            .orderBy("priority", false)
            .orderBy("createTime", false);
        String appName = StrUtil.trim(appQueryRequest.getAppName());
        if (StrUtil.isNotBlank(appName)) {
            queryWrapper.like("appName", appName);
        }

        Page<App> appPage = this.page(
            Page.of(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()),
            queryWrapper
        );
        return buildAppVOPage(appPage, appQueryRequest.getPageNum(), appQueryRequest.getPageSize());
    }

    @Override
    public boolean adminDeleteApp(long id) {
        getAppById(id);
        boolean result = this.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        return true;
    }

    @Override
    public boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null,
            ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
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
        if (appAdminUpdateRequest.getCover() != null) {
            updateApp.setCover(validateCover(appAdminUpdateRequest.getCover()));
            hasUpdate = true;
        }
        if (appAdminUpdateRequest.getPriority() != null) {
            ThrowUtils.throwIf(
                appAdminUpdateRequest.getPriority() < 0,
                ErrorCode.PARAMS_ERROR,
                "优先级不能小于 0"
            );
            updateApp.setPriority(appAdminUpdateRequest.getPriority());
            hasUpdate = true;
        }

        // 没有需要更新的字段，直接返回成功
        if (!hasUpdate) {
            return true;
        }

        updateApp.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用失败");
        return true;
    }

    @Override
    public Page<AppVO> listAppByPage(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        validatePage(appQueryRequest, 0);

        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        String sortField = StrUtil.trim(appQueryRequest.getSortField());
        if (StrUtil.isNotBlank(sortField) && SORTABLE_FIELDS.contains(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(appQueryRequest.getSortOrder()));
        } else {
            queryWrapper.orderBy("createTime", false);
        }

        Page<App> appPage = this.page(
            Page.of(appQueryRequest.getPageNum(), appQueryRequest.getPageSize()),
            queryWrapper
        );
        return buildAppVOPage(appPage, appQueryRequest.getPageNum(), appQueryRequest.getPageSize());
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
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }


    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
            .map(App::getUserId)
            .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
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
            .eq("userId", userId)
            .orderBy(sortField, "ascend".equals(sortOrder));
    }


    /**
     * 根据 id 获取应用，不存在则抛异常
     */
    private App getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不合法");
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        return app;
    }

    /**
     * 校验应用归属
     */
    private void checkOwnership(App app, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(
            !Objects.equals(app.getUserId(), loginUser.getId()),
            ErrorCode.NO_AUTH_ERROR,
            "无权操作该应用"
        );
    }

    /**
     * 校验应用名称长度
     */
    private void validateAppNameLength(String appName) {
        ThrowUtils.throwIf(
            appName.length() > AppConstant.APP_NAME_MAX_LENGTH,
            ErrorCode.PARAMS_ERROR,
            "应用名称不能超过 " + AppConstant.APP_NAME_MAX_LENGTH + " 个字符"
        );
    }

    /** 校验图片链接；空字符串用于清除封面。 */
    private String validateCover(String value) {
        String cover = StrUtil.trimToEmpty(value);
        ThrowUtils.throwIf(cover.length() > AppConstant.COVER_MAX_LENGTH,
            ErrorCode.PARAMS_ERROR, "应用封面地址不能超过 " + AppConstant.COVER_MAX_LENGTH + " 个字符");
        if (cover.isEmpty()) {
            return cover;
        }
        try {
            URI uri = URI.create(cover);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
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
     * @param maxPageSize     每页最大数量，0 表示不限制
     */
    private void validatePage(AppQueryRequest appQueryRequest, int maxPageSize) {
        ThrowUtils.throwIf(appQueryRequest.getPageNum() < 1, ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        ThrowUtils.throwIf(appQueryRequest.getPageSize() < 1, ErrorCode.PARAMS_ERROR, "每页数量必须大于 0");
        if (maxPageSize > 0) {
            ThrowUtils.throwIf(
                appQueryRequest.getPageSize() > maxPageSize,
                ErrorCode.PARAMS_ERROR,
                "每页最多查询 " + maxPageSize + " 条"
            );
        }
    }

    /**
     * 将应用实体分页转换为 VO 分页
     */
    private Page<AppVO> buildAppVOPage(Page<App> appPage, int pageNum, int pageSize) {
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        appVOPage.setRecords(getAppVOList(appPage.getRecords()));
        return appVOPage;
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在：" + appId);
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 调用 AI 生成代码
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
    }
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在：" + appId);
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "应用代码生成类型不合法");
        String sourceDirName = codeGenTypeEnum.getValue() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回部署标识，由 Controller 拼接当前访问域名
        return deployKey;
    }

}

