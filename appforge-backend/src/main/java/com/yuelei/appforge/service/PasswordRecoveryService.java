package com.yuelei.appforge.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.yuelei.appforge.constant.UserConstant;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.model.dto.user.PasswordResetCodeRequest;
import com.yuelei.appforge.model.dto.user.PasswordResetRequest;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.PasswordResetCodeVO;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** 管理员核验身份后签发一次性重置码，沿用现有 Redis 和密码格式。 */
@Service
@Slf4j
public class PasswordRecoveryService {
    private static final String PREFIX = "appforge:password-recovery:";
    private static final int EXPIRES_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_CODE = "账号或重置码不正确，或重置码已过期";
    private static final DefaultRedisScript<Long> CONSUME =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL',"
                            + " KEYS[1]) else return 0 end",
                    Long.class);
    private static final DefaultRedisScript<Long> LIMIT =
            new DefaultRedisScript<>(
                    "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE',"
                            + " KEYS[1], 60) end; return n",
                    Long.class);

    @Resource private UserService userService;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redis;

    public PasswordResetCodeVO issue(PasswordResetCodeRequest body, HttpServletRequest request) {
        User admin = userService.getLoginUser(request);
        if (!UserConstant.ADMIN_ROLE.equals(admin.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (body == null
                || body.getUserId() == null
                || body.getUserId() <= 0
                || !body.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先核验用户身份并确认后再生成重置码");
        }
        limit("issue:" + admin.getId());
        User user = userService.getById(body.getUserId());
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue()
                .set(grantKey(user), proof(code, user), Duration.ofSeconds(EXPIRES_SECONDS));
        log.info("管理员签发密码重置码，adminId={}, userId={}", admin.getId(), user.getId());
        return new PasswordResetCodeVO(code, EXPIRES_SECONDS);
    }

    public void reset(PasswordResetRequest body, HttpServletRequest request) {
        // 使用连接地址，不信任客户端可随意伪造的转发 Header。
        limit("reset:" + digest(String.valueOf(request.getRemoteAddr())));
        if (body == null
                || body.getUserAccount() == null
                || body.getUserAccount().isBlank()
                || body.getUserAccount().length() < 4
                || body.getUserAccount().length() > 256
                || body.getResetCode() == null
                || !body.getResetCode().matches("[A-Za-z0-9_-]{32}")) {
            throw invalidCode();
        }
        String password = body.getNewPassword();
        if (password == null
                || password.isBlank()
                || password.length() < 8
                || password.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码需为 8～64 位");
        }
        if (!password.equals(body.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");
        }
        User user =
                userService.getOne(QueryWrapper.create().eq("userAccount", body.getUserAccount()));
        if (user == null) throw invalidCode();
        String expected = proof(body.getResetCode(), user);
        String key = grantKey(user);
        if (!expected.equals(redis.opsForValue().get(key))) throw invalidCode();
        String newHash = userService.getEncryptPassword(password);
        if (newHash.equals(user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }
        // 原子比较后消费：错误码不消耗凭证，重放及并发兑换最多成功一次。
        if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key), expected)))
            throw invalidCode();
        User patch = new User();
        patch.setUserPassword(newHash);
        patch.setEditTime(LocalDateTime.now());
        boolean updated =
                userService.update(
                        patch,
                        QueryWrapper.create()
                                .eq("id", user.getId())
                                .eq("userPassword", user.getUserPassword()));
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_ERROR, "重置码已失效，请联系管理员重新获取");
        // getLoginUser 对比密码快照，旧会话会在下次鉴权时失效，不自动登录。
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        log.info("密码重置成功，userId={}", user.getId());
    }

    private void limit(String subject) {
        Long count = redis.execute(LIMIT, List.of(PREFIX + "rate:" + subject));
        if (count == null || count > 10) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "操作过于频繁，请一分钟后重试");
        }
    }

    private static String grantKey(User user) {
        return PREFIX + "grant:" + user.getId();
    }

    private static String proof(String code, User user) {
        // 仅保存摘要，并绑定签发时的密码版本；普通改密后旧重置码也不能使用。
        return digest(code) + ":" + digest(user.getUserPassword());
    }

    private static String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static BusinessException invalidCode() {
        return new BusinessException(ErrorCode.PARAMS_ERROR, INVALID_CODE);
    }
}
