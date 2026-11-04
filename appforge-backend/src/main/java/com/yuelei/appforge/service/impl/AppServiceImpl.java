package com.yuelei.appforge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuelei.appforge.constant.AppConstant;
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
import com.yuelei.appforge.service.AppService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

        // 用户只能修改应用名称
        App updateApp = new App();
        updateApp.setId(app.getId());
        updateApp.setAppName(appName);
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
        checkOwnership(app, loginUser);
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
            String cover = StrUtil.trim(appAdminUpdateRequest.getCover());
            ThrowUtils.throwIf(
                cover.length() > AppConstant.COVER_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "应用封面地址不能超过 " + AppConstant.COVER_MAX_LENGTH + " 个字符"
            );
            updateApp.setCover(cover);
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
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream().map(this::getAppVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");

        QueryWrapper queryWrapper = QueryWrapper.create();
        if (appQueryRequest.getId() != null) {
            queryWrapper.eq("id", appQueryRequest.getId());
        }
        if (StrUtil.isNotBlank(appQueryRequest.getAppName())) {
            queryWrapper.like("appName", appQueryRequest.getAppName().trim());
        }
        if (StrUtil.isNotBlank(appQueryRequest.getCover())) {
            queryWrapper.like("cover", appQueryRequest.getCover().trim());
        }
        if (StrUtil.isNotBlank(appQueryRequest.getInitPrompt())) {
            queryWrapper.like("initPrompt", appQueryRequest.getInitPrompt().trim());
        }
        if (StrUtil.isNotBlank(appQueryRequest.getCodeGenType())) {
            queryWrapper.eq("codeGenType", appQueryRequest.getCodeGenType().trim());
        }
        if (StrUtil.isNotBlank(appQueryRequest.getDeployKey())) {
            queryWrapper.like("deployKey", appQueryRequest.getDeployKey().trim());
        }
        if (appQueryRequest.getPriority() != null) {
            queryWrapper.eq("priority", appQueryRequest.getPriority());
        }
        if (appQueryRequest.getUserId() != null) {
            queryWrapper.eq("userId", appQueryRequest.getUserId());
        }
        return queryWrapper;
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
}
