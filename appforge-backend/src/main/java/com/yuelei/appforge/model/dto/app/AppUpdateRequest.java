package com.yuelei.appforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新应用请求（目前仅支持修改应用名称）。
 *
 * @author yuelei
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}
