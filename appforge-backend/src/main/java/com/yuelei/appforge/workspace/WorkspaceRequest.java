package com.yuelei.appforge.workspace;

import lombok.Data;

@Data
public class WorkspaceRequest {
    private Long appId;
    private Integer version;
    private Boolean approved;
    private String reason;
}
