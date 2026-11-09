package com.yuelei.appforge.core.handler;

import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Component
public class StreamHandlerExecutor {
    private final SimpleTextStreamHandler text = new SimpleTextStreamHandler();
    private final JsonMessageStreamHandler json = new JsonMessageStreamHandler();

    public Flux<String> doExecute(
            Flux<String> source, CodeGenTypeEnum type, Consumer<String> history) {
        return type == CodeGenTypeEnum.VUE_PROJECT
                ? json.handle(source, history)
                : text.handle(source, history);
    }
}
