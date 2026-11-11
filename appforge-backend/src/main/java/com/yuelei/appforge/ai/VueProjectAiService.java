package com.yuelei.appforge.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/** Vue 工程生成服务：创建与修改使用独立提示词和工具集。 */
public interface VueProjectAiService {
    @SystemMessage(fromResource = "prompt/codegen-vue-create-system-prompt.txt")
    TokenStream create(@UserMessage String request);

    @SystemMessage(fromResource = "prompt/codegen-vue-edit-system-prompt.txt")
    TokenStream edit(@UserMessage String request);
}
