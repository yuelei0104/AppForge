package com.yuelei.appforge.ai.model.message;

public enum StreamMessageTypeEnum {
    AI_RESPONSE("ai_response"),
    TOOL_REQUEST("tool_request"),
    TOOL_EXECUTED("tool_executed");
    private final String value;

    StreamMessageTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StreamMessageTypeEnum getEnumByValue(String value) {
        for (var type : values()) if (type.value.equals(value)) return type;
        return null;
    }
}
