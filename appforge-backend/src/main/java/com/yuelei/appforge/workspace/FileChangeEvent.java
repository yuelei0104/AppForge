package com.yuelei.appforge.workspace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成过程中单个文件的变更事件。
 *
 * <p>事件只用于实时展示，生成结束后的版本文件接口才是最终权威。DELETED 事件的 content 为
 * null；超过 SSE 上限时 content 也为 null，并通过 truncated 标记提示前端在完成后补全。</p>
 */
public record FileChangeEvent(
        String path, FileOperation operation, String content, boolean truncated) {

    /** Hutool 的 Bean 序列化不识别 record 访问器，显式提供 SSE 字段。 */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("operation", operation == null ? null : operation.name());
        result.put("content", content);
        result.put("truncated", truncated);
        return result;
    }
}
