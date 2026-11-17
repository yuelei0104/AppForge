package com.yuelei.core.handler;

import com.yuelei.core.handler.SimpleTextStreamHandler;
import com.yuelei.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Component
public class StreamHandlerExecutor {
    private final SimpleTextStreamHandler text = new SimpleTextStreamHandler();
    private final JsonMessageStreamHandler json;

    public StreamHandlerExecutor() {
        this(com.yuelei.ai.tools.ToolManager.defaults());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StreamHandlerExecutor(com.yuelei.ai.tools.ToolManager tools) {
        json = new JsonMessageStreamHandler(tools);
    }

    public Flux<String> doExecute(
            Flux<String> source, CodeGenTypeEnum type, Consumer<String> history) {
        return type == CodeGenTypeEnum.VUE_PROJECT
                ? json.handle(source, history)
                : text.handle(source, history);
    }
}
