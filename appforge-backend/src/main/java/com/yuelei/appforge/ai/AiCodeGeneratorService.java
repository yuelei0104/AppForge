package com.yuelei.appforge.ai;

import com.yuelei.appforge.ai.model.HtmlCodeResult;
import com.yuelei.appforge.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    /**
     * 根据描述生成应用名称。
     *
     * @param description 应用描述
     * @return JSON 格式的应用名称
     */
    @SystemMessage("根据用户的网站描述生成简短中文应用名称，最多 20 字。只返回 JSON 对象，格式为 {\"name\":\"应用名称\"}，不要添加其他内容。")
    String generateAppName(@UserMessage String description);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(@UserMessage String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(@UserMessage String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage 用户消息
     * @return 代码流
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(@UserMessage String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 代码流
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(@UserMessage String userMessage);
}
