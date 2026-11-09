package com.yuelei.appforge.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface VueProjectAiService {
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generate(@UserMessage String request);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    Flux<String> generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

}
