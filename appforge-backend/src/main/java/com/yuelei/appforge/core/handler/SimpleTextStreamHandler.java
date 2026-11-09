package com.yuelei.appforge.core.handler;

import reactor.core.publisher.Flux;

import java.util.function.Consumer;

public final class SimpleTextStreamHandler {
    public Flux<String> handle(Flux<String> source, Consumer<String> history) {
        return source.doOnNext(history);
    }
}
