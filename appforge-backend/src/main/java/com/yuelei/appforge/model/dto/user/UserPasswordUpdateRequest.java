package com.yuelei.appforge.model.dto.user;

/** 不生成 toString，避免密码意外进入日志。 */
@lombok.Getter
@lombok.Setter
public class UserPasswordUpdateRequest {
    private String oldPassword;
    private String newPassword;
    private String checkPassword;
}
