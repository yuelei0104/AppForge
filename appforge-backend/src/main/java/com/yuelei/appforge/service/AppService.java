package com.yuelei.appforge.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yuelei.appforge.model.dto.app.AppAddRequest;
import com.yuelei.appforge.model.dto.app.AppAdminUpdateRequest;
import com.yuelei.appforge.model.dto.app.AppQueryRequest;
import com.yuelei.appforge.model.dto.app.AppUpdateRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.AppVO;

import java.util.List;

/**
 * 应用服务层。
 *
 * @author yuelei
 */
public interface AppService extends IService<App> {

    /**
     * 创建应用
     *
     * @param appAddRequest 创建请求
     * @param loginUser     当前登录用户
     * @return 新应用 id
     */
    long addApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 用户更新自己的应用（仅支持修改名称）
     *
     * @param appUpdateRequest 更新请求
     * @param loginUser        当前登录用户
     * @return 是否成功
     */
    boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser);

    /**
     * 用户删除自己的应用
     *
     * @param id        应用 id
     * @param loginUser 当前登录用户
     * @return 是否成功
     */
    boolean deleteApp(long id, User loginUser);

    /**
     * 用户查看自己的应用详情
     *
     * @param id        应用 id
     * @param loginUser 当前登录用户
     * @return 应用详情
     */
    AppVO getAppVOById(long id, User loginUser);

    /**
     * 分页查询当前用户的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param loginUser       当前登录用户
     * @return 应用分页
     */
    Page<AppVO> listMyAppByPage(AppQueryRequest appQueryRequest, User loginUser);

    /**
     * 分页查询精选应用列表（无需登录）
     *
     * @param appQueryRequest 查询请求
     * @return 应用分页
     */
    Page<AppVO> listFeaturedAppByPage(AppQueryRequest appQueryRequest);

    /**
     * 管理员删除任意应用
     *
     * @param id 应用 id
     * @return 是否成功
     */
    boolean adminDeleteApp(long id);

    /**
     * 管理员更新任意应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 是否成功
     */
    boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest);

    /**
     * 管理员分页查询应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用分页
     */
    Page<AppVO> listAppByPage(AppQueryRequest appQueryRequest);

    /**
     * 管理员查看任意应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    AppVO adminGetAppVOById(long id);

    /**
     * 应用实体转 VO
     *
     * @param app 应用实体
     * @return 应用 VO
     */
    AppVO getAppVO(App app);

    /**
     * 应用实体列表转 VO 列表
     *
     * @param appList 应用实体列表
     * @return 应用 VO 列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造管理员查询条件
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}
