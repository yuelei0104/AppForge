package com.yuelei.appforge.ratelimiter;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 分布式限流切面。
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UserService userService;

    @Before(value = "@annotation(rateLimit)", argNames = "point,rateLimit")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        long rate = rateLimit.rate();
        long intervalSeconds = rateLimit.rateInterval();

        if (rate <= 0 || intervalSeconds <= 0) {
            throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "限流次数和时间间隔必须大于 0"
            );
        }

        String key = generateRateLimitKey(point, rateLimit);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // 仅在尚未初始化时设置规则。
        rateLimiter.trySetRate(
            RateType.OVERALL,
            rate,
            intervalSeconds,
            RateIntervalUnit.SECONDS
        );

        // 先初始化再设置过期时间。
        // 有效期至少一小时，并且长于限流周期。
        Duration ttl = Duration.ofSeconds(intervalSeconds)
            .plusHours(1);
        rateLimiter.expire(ttl);

        // 非阻塞获取一个令牌。
        if (!rateLimiter.tryAcquire(1)) {
            log.debug("请求被限流，key={}", key);
            throw new BusinessException(
                ErrorCode.TOO_MANY_REQUEST,
                rateLimit.message()
            );
        }
    }

    /**
     * 生成限流 Key。
     * 同时包含方法信息，避免不同接口误用同一个用户/IP额度。
     */
    private String generateRateLimitKey(
        JoinPoint point, RateLimit rateLimit) {

        MethodSignature signature =
            (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        StringBuilder keyBuilder = new StringBuilder("rate_limit:");

        if (!rateLimit.key().isBlank()) {
            keyBuilder.append(rateLimit.key()).append(":");
        }

        // 包含完整方法签名，区分不同类、方法及重载。
        keyBuilder.append(method.toGenericString()).append(":");
        String prefix = keyBuilder.toString();

        return switch (rateLimit.limitType()) {
            case API -> prefix + "api";

            case USER -> {
                HttpServletRequest request = getCurrentRequest();

                try {
                    User loginUser = userService.getLoginUser(request);

                    if (loginUser != null && loginUser.getId() != null) {
                        yield prefix + "user:" + loginUser.getId();
                    }
                } catch (BusinessException e) {
                    // 未获取到登录用户时，按 IP 限流。
                    // 接口的登录权限仍由原有鉴权逻辑负责。
                }

                yield prefix + "ip:" + getClientIP(request);
            }

            case IP -> {
                HttpServletRequest request = getCurrentRequest();
                yield prefix + "ip:" + getClientIP(request);
            }

            default -> throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "不支持的限流类型"
            );
        };
    }

    /**
     * 获取当前 HTTP 请求。
     */
    private HttpServletRequest getCurrentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();

        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }

        throw new BusinessException(
            ErrorCode.SYSTEM_ERROR,
            "无法获取当前 HTTP 请求"
        );
    }

    /**
     * 获取客户端地址。
     */
    private String getClientIP(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

}
