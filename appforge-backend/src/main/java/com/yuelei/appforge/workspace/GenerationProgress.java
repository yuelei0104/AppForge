package com.yuelei.appforge.workspace;

/** 仅传递操作基本信息，不向浏览器流式发送工具参数和文件内容。 */
public record GenerationProgress(
        String id, String operation, String path, String state, String message) {
    /** Hutool 的 Bean 序列化不识别 record 访问器，显式提供 SSE 字段。 */
    public java.util.Map<String, String> toMap() {
        return java.util.Map.of(
                "id",
                id,
                "operation",
                operation,
                "path",
                java.util.Objects.toString(path, ""),
                "state",
                state,
                "message",
                message);
    }
}
