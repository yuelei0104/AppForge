package com.yuelei.appforge.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** 独立、无会话记忆的摘要调用，避免污染应用对话和名称生成。 */
public interface AiChatSummaryService {
    @SystemMessage(fromResource = "prompt/chat-summary-system-prompt.txt")
    String summarize(@UserMessage String transcript);
}
