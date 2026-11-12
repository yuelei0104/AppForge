package com.yuelei.appforge.ai.guardrail;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
        "忽略之前的指令", "忽略之前的命令", "忽略以上", "忽略前面",
        "无视之前", "无视以上", "忘记之前", "忘记以上",
        "ignore previous instructions", "ignore above",
        "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)(?:忽略|无视|忘记|不要理会|别管)\\s*(?:之前|以上|前面|先前|所有|全部)\\s*(?:的)?\\s*(?:指令|命令|提示|要求|规则|设定)"),
        Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
        Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
        Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
        Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
        Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage == null ? null : userMessage.singleText();
        // 检查是否为空
        if (input == null || input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }
        // 检查输入长度
        if (input.length() > 1000) {
            return fatal("输入内容过长，不要超过 1000 字");
        }
        // 检查敏感词
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        // 检查注入攻击模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        return success();
    }

    /**
     * 在进入生成链路前校验用户输入。校验失败直接抛出业务异常，
     * 由 SSE 异常处理返回 business-error 事件。
     */
    public void ensureSafe(String input) {
        InputGuardrailResult result = validate(UserMessage.from(input == null ? "" : input));
        if (!result.isSuccess()) {
            String message =
                    result.failures().stream()
                            .findFirst()
                            .map(f -> f.message())
                            .orElse("输入包含不当内容，请修改后重试");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }
}
