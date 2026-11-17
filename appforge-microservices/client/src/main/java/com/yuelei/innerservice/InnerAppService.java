package com.yuelei.innerservice;

import com.yuelei.vo.AppVO;

/**
 * 应用服务的内部 Dubbo 接口。
 *
 * <p>供其他微服务按 appId 读取应用基础信息，避免直接访问 app 数据库。
 */
public interface InnerAppService {

    /**
     * 按 id 查询应用信息。
     *
     * @param id 应用 id
     * @return 应用信息，不存在时返回 null
     */
    AppVO getAppVO(long id);
}
