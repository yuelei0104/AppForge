package com.yuelei.appforge.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /** 账号 */
    private String userAccount;

    /** 昵称，旧客户端未填写时由服务端生成。 */
    private String userName;

    /** 密码 */
    private String userPassword;

    /** 确认密码 */
    private String checkPassword;
}
