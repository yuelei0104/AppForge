package com.yuelei.appforge.model.dto.user;

/** 不生成 toString，避免重置码和密码进入日志。 */
@lombok.Getter
@lombok.Setter
public class PasswordResetRequest {
    private String userAccount;
    private String resetCode;
    private String newPassword;
    private String checkPassword;
}
