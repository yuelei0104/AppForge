package com.yuelei.service.impl;

import com.yuelei.entity.User;
import com.yuelei.innerservice.InnerUserService;
import com.yuelei.service.UserService;
import com.yuelei.vo.UserVO;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 内部服务实现类。
 *
 * <p>当前用户服务与调用方在同一个 Spring 上下文中，直接委托本地 UserService；后续拆分部署时可替换为
 * Feign/Dubbo 实现。
 */
@Service
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserService userService;

    @Override
    public List<User> listByIds(Collection<? extends Serializable> ids) {
        return userService.listByIds(ids);
    }

    @Override
    public User getById(Serializable id) {
        return userService.getById(id);
    }

    @Override
    public UserVO getUserVO(User user) {
        return userService.getUserVO(user);
    }
}
