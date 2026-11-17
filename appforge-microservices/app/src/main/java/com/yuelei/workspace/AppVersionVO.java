package com.yuelei.workspace;

import java.time.LocalDateTime;

public record AppVersionVO(
        int version,
        String kind,
        Integer sourceVersion,
        String summary,
        LocalDateTime createTime) {}
