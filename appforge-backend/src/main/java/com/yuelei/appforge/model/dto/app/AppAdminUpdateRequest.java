package com.yuelei.appforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新应用请求。
 *
 * @author yuelei
 */
@Data
public class AppAdminUpdateRequest implements Serializable {

    /** 应用 id */
    private Long id;

    /** 应用名称 */
    private String appName;

    /** 应用封面 */
    private String cover;

    /** 优先级（大于 0 时展示为精选应用） */
    private Integer priority;

    private String description;
    private java.util.List<String> tags;
    private String visibility;

    private static final long serialVersionUID = 1L;
}
