package com.yuelei.service.impl;

import com.yuelei.innerservice.InnerAppService;
import com.yuelei.service.AppService;
import com.yuelei.vo.AppVO;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 应用内部服务实现。
 */
@Service
@DubboService
public class InnerAppServiceImpl implements InnerAppService {

    @Resource
    private AppService appService;

    @Override
    public AppVO getAppVO(long id) {
        return appService.adminGetAppVOById(id);
    }
}
