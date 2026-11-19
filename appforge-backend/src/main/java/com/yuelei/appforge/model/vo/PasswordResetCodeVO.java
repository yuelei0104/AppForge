package com.yuelei.appforge.model.vo;

/** 重置码仅在签发响应中返回，不生成 toString。 */
@lombok.Getter
@lombok.AllArgsConstructor
public class PasswordResetCodeVO {
    private final String resetCode;
    private final int expiresInSeconds;
}
