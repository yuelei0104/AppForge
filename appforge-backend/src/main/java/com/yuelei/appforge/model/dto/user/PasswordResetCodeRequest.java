package com.yuelei.appforge.model.dto.user;

@lombok.Getter
@lombok.Setter
public class PasswordResetCodeRequest {
    private Long userId;

    /** 管理员确认已通过站外渠道核验申请人身份。 */
    private boolean identityVerified;
}
