package com.yuelei.workspace;

/** 用户主动停止生成时抛出，用于终止流式生成。 */
public class GenerationStoppedException extends RuntimeException {
    public GenerationStoppedException() {
        super("生成已停止");
    }
}
