package com.yuelei.appforge.model.dto.user;

import lombok.Data;

/** 当前用户昵称，身份仅从登录会话获取。 */
@Data
public class UserProfileUpdateRequest {
    private String userName;
}
