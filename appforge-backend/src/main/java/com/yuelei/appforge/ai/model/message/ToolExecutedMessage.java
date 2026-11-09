package com.yuelei.appforge.ai.model.message;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolExecutedMessage extends StreamMessage {
    private String id;
    private String name;
    private String arguments;
    private String result;
    private String content;
    private boolean failed;

    public ToolExecutedMessage(
            String id, String name, String arguments, String content, boolean failed) {
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.content = content;
        this.failed = failed;
    }
}
