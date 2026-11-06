package com.yuelei.appforge.ai;

import com.yuelei.appforge.ai.model.HtmlCodeResult;
import com.yuelei.appforge.ai.model.MultiFileCodeResult;

import dev.langchain4j.service.SystemMessage;

import reactor.core.publisher.Flux;

public interface AiCodeGeneratorService {

    @SystemMessage("根据用户的网站描述生成简短中文应用名称，最多 20 字。只返回 JSON 对象，格式为 {\"name\":\"应用名称\"}，不要添加其他内容。")
    String generateAppName(String description);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);
}
