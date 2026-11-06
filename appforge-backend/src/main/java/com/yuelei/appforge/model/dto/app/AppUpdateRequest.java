package com.yuelei.appforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新自己的应用名称与封面，不接受优先级、生成类型等管理字段。
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

    /** 应用封面；不传则保持原值，空字符串表示清除。 */
    private String cover;

    private static final long serialVersionUID = 1L;
}

