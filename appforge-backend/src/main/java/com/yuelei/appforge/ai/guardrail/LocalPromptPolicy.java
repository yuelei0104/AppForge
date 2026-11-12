package com.yuelei.appforge.ai.guardrail;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cheap checks precede semantic review; ordinary security topics and words such as hackathon are
 * allowed.
 */
final class LocalPromptPolicy {
    private static final List<Pattern> SECRETS =
            List.of(
                    Pattern.compile(
                            "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END"
                                + " (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
                    Pattern.compile("(?<![A-Za-z0-9])sk[-_][A-Za-z0-9_-]{16,}"),
                    Pattern.compile("\\bAKID[A-Za-z0-9]{16,}\\b"),
                    Pattern.compile(
                            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{16,}\\b"),
                    Pattern.compile(
                            "(?i)(?:api[-_]?key|secret[-_]?key|password|passwd|access[-_]?token)[\\\"']?\\s*[:=]\\s*[\\\"']?[^\\s\\\"'<>;,]{4,}"));
    private static final Pattern OVERRIDE =
            Pattern.compile(
                    "(?i)(?:(?:忽略|无视|忘记|不要理会|别管)\\s*(?:之前|以上|前面|先前|所有|全部)\\s*(?:的)?\\s*(?:指令|命令|提示|要求|规则|设定)|(?:ignore|disregard|forget)\\s+(?:all\\s+)?(?:previous|above|prior)\\s+(?:instructions?|commands?|prompts?|rules?))");
    private static final Pattern HARD_OVERRIDE =
            Pattern.compile(
                    "(?i)(?:system\\s*:\\s*you\\s+are|<\\|(?:im_start|system)\\|>|(?:print|reveal|leak|expose)\\s+(?:the\\s+)?(?:system\\s+prompt|secrets?|api\\s*keys?)|(?:泄露|输出|打印|获取)\\s*(?:你的|系统的|后台的)?\\s*(?:系统提示词|API密钥|后台密钥))");

    record Check(String text, Set<PromptReviewer.Rule> rules, boolean blocked) {}

    static Check inspect(String input) {
        Set<PromptReviewer.Rule> rules = new LinkedHashSet<>();
        String text = input;
        for (Pattern pattern : SECRETS) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                rules.add(PromptReviewer.Rule.SECRET_REDACTION);
                text = matcher.replaceAll("[已移除敏感信息]");
            }
        }
        var override = OVERRIDE.matcher(text);
        if (override.find()) {
            rules.add(PromptReviewer.Rule.PROMPT_INJECTION);
            text = override.replaceAll("").strip();
        }
        String normalized =
                Normalizer.normalize(text, Normalizer.Form.NFKC)
                        .replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        boolean blocked =
                OVERRIDE.matcher(normalized).find() || HARD_OVERRIDE.matcher(normalized).find();
        if (blocked) rules.add(PromptReviewer.Rule.PROMPT_INJECTION);
        return new Check(text, Collections.unmodifiableSet(rules), blocked);
    }

    static boolean hasRequest(String text) {
        return !text.replace("[已移除敏感信息]", "").replaceAll("[\\p{P}\\p{Z}\\s]", "").isBlank();
    }
}
