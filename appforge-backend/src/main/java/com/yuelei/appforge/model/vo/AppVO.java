package com.yuelei.appforge.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppVO implements Serializable {
    private Boolean coverEnabled;
    private String coverMode;
    private String coverStatus;
    private Integer coverVersion;
    private String coverRevision;

    private String description;

    private java.util.List<String> tags;

    private String visibility;

    private String generationStatus;

    private String generationError;

    private Integer currentVersion;

    private Integer deployedVersion;

    private String deploymentStatus;

    private String featureStatus;

    private String featureReason;

    /** id */
    private Long id;

    /** 应用名称 */
    private String appName;

    /** 应用封面 */
    private String cover;

    /** 应用初始化的 prompt */
    private String initPrompt;

    /** 代码生成类型（枚举） */
    private String codeGenType;

    /** 部署标识 */
    private String deployKey;

    /** 部署时间 */
    private LocalDateTime deployedTime;

    /** 优先级 */
    private Integer priority;

    /** 创建用户id */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建用户信息 */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
