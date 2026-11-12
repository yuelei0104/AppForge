package com.yuelei.appforge.service;

import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.enums.ChatHistoryMessageTypeEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Handles pure social messages separately from code generation.
 *
 * <p>The classifier is intentionally conservative: only exact greetings, thanks, and capability
 * questions take the chat path. Anything containing a concrete change request continues through
 * the existing generation pipeline.
 */
@Component
@RequiredArgsConstructor
public class ChatIntentService {
    private static final Pattern GREETING =
            Pattern.compile(
                    "^(你好|您好|hi|hello|hey|嗨|哈喽|在吗|在么|早上好|上午好|下午好|晚上好|早安|晚安)"
                            + "(呀|啊|哦|呢|嘛|哈|啦)*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern THANKS =
            Pattern.compile(
                    "^(谢谢|多谢|感谢|辛苦了|thanks|thankyou)(你|您|呀|啊|哦|呢|啦|哈)*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern CAPABILITY =
            Pattern.compile(
                    "^((你|您)(能|会|可以)(做|干|帮|提供)(什么|啥|哪些).*"
                            + "|(你好|您好)?(有什么|有啥)(帮助|功能|能力).*)$");

    private static final Pattern IDENTITY =
            Pattern.compile("^(你是谁|你叫什么|介绍一下你|你是干什么的).*$");

    private final ChatHistoryService chatHistoryService;

    public boolean isSmallTalk(String message, List<Long> attachmentIds, String element) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) return false;
        if (element != null && !element.isBlank()) return false;
        String text = normalize(message);
        return !text.isEmpty()
                && (GREETING.matcher(text).matches()
                        || THANKS.matcher(text).matches()
                        || CAPABILITY.matcher(text).matches()
                        || IDENTITY.matcher(text).matches());
    }

    public Flux<String> reply(Long appId, String message, User user) {
        return Flux.defer(
                        () -> {
                            String text = message.strip();
                            chatHistoryService.appendChatMessage(
                                    appId,
                                    text,
                                    ChatHistoryMessageTypeEnum.USER.getValue(),
                                    user.getId());
                            String answer = answerFor(text);
                            ThrowUtils.throwIf(
                                    !chatHistoryService.addChatMessage(
                                            appId,
                                            answer,
                                            ChatHistoryMessageTypeEnum.AI.getValue(),
                                            user.getId()),
                                    ErrorCode.OPERATION_ERROR,
                                    "保存 AI 消息失败");
                            return Flux.just(answer);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String answerFor(String message) {
        String text = normalize(message);
        if (THANKS.matcher(text).matches()) {
            return "不客气，有需要继续告诉我。";
        }
        if (CAPABILITY.matcher(text).matches()) {
            return "我可以根据你的描述生成 HTML 页面或 Vue 应用，也能在现有应用上修改样式、添加功能。"
                    + "直接告诉我你想做什么就行。";
        }
        if (IDENTITY.matcher(text).matches()) {
            return "我是 AppForge 助手，可以帮你把想法生成可预览的应用。";
        }
        return "你好！有什么可以帮你的吗？你可以直接描述想生成或修改的页面。";
    }

    private static String normalize(String message) {
        return message == null
                ? ""
                : message.replace("\uFEFF", "")
                        .replaceAll("[\\u200B-\\u200D]", "")
                        .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
}
