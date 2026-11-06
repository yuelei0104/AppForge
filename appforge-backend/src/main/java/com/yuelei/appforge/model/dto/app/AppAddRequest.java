package com.yuelei.appforge.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建应用请求。
 *
 * @author yuelei
 */
@Data
public class AppAddRequest implements Serializable {

    /** 应用名称（不填则使用默认名称） */
    private String appName;

    /** 应用封面 */
    private String cover;

    /** 应用初始化的 prompt */
    private String initPrompt;

    /** 代码生成类型（html/multi_file，不填默认 html） */
    private String codeGenType;

    private String description;
    private java.util.List<String> tags;
    private String visibility;

    private static final long serialVersionUID = 1L;
}
