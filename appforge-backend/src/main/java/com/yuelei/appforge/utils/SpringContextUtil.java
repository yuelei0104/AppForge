package com.yuelei.appforge.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 静态获取 Spring Bean 的工具。
 *
 * <p>AI 路由服务需要在每次调用时动态获取 prototype 的 ChatModel，因此不能只在工厂
 * 初始化时注入一次。这里保存 ApplicationContext，供 {@code AiCodeGenTypeRoutingServiceFactory}
 * 在并发调用中按名称获取新实例。</p>
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * 按名称和类型获取 Bean。
     *
     * @param name Bean 名称
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(String name, Class<T> type) {
        ApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("Spring 上下文尚未初始化，无法获取 Bean: " + name);
        }
        return context.getBean(name, type);
    }
}
