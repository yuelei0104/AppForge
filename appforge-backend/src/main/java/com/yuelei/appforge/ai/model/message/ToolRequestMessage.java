package com.yuelei.appforge.ai.model.message;

import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {
    private String id;
    private String name;
    private int index;
    private String arguments;

    public ToolRequestMessage(String id, String name, int index, String arguments) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = id;
        this.name = name;
        this.index = index;
        this.arguments = arguments;
    }
}
