package com.yuelei.appforge.ai.guardrail;

import static com.yuelei.appforge.ai.guardrail.PromptReviewer.*;

import com.yuelei.appforge.exception.ErrorCode;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** Application entry checks cover native HTTP streams as well as Vue AI Services. */
@Component
public class PromptSafetyInputGuardrail implements InputGuardrail {
    private final GuardrailProperties properties;
    private final PromptReviewer reviewer;
    private final GuardrailAudit audit;

    /**
     * Compatibility adapter for manually constructed SDK services; local checks and audit remain
     * active.
     */
    public PromptSafetyInputGuardrail() {
        this(localSettings(), null, new GuardrailAudit());
    }

    @Autowired
    public PromptSafetyInputGuardrail(
            GuardrailProperties properties, PromptReviewer reviewer, GuardrailAudit audit) {
        this.properties = properties;
        this.reviewer = reviewer;
        this.audit = audit;
    }

    private static GuardrailProperties localSettings() {
        var properties = new GuardrailProperties();
        properties.setSemanticEnabled(false);
        return properties;
    }

    public record Prepared(
            String message, String context, String requestId, Action action, List<Rule> rules) {
        public boolean rewritten() {
            return action == Action.REWRITE;
        }
    }

    /** Shape checks can run before SSE without performing a paid or blocking model request. */
    public void checkShape(String input, Long userId, Long appId) {
        String text = Objects.toString(input, "");
        String trace = UUID.randomUUID().toString();
        if (text.isBlank() || text.length() > 2000) {
            Rule rule = text.isBlank() ? Rule.INPUT_EMPTY : Rule.INPUT_LENGTH;
            audit(trace, userId, appId, "HTTP", "BLOCK", List.of(rule), "LOCAL", text, "");
            throw new PromptGuardrailException(
                    ErrorCode.PARAMS_ERROR,
                    text.isBlank() ? "输入内容不能为空" : "输入内容过长，不要超过 2000 字",
                    trace,
                    List.of(rule));
        }
    }

    public Prepared prepare(
            String message, String context, Long userId, Long appId, String source) {
        String originalMessage = Objects.toString(message, "");
        String originalContext = Objects.toString(context, "");
        String original = originalMessage + "\u0000" + originalContext;
        String trace = UUID.randomUUID().toString();
        Set<Rule> rules = new LinkedHashSet<>();
        String provider = "LOCAL";
        String cleanMessage = originalMessage;
        String cleanContext = originalContext;
        try {
            if (message == null || message.isBlank()) {
                rules.add(Rule.INPUT_EMPTY);
                throw rejected(ErrorCode.PARAMS_ERROR, "输入内容不能为空", trace, rules);
            }
            if (message.length() > 2000) {
                rules.add(Rule.INPUT_LENGTH);
                throw rejected(ErrorCode.PARAMS_ERROR, "输入内容过长，不要超过 2000 字", trace, rules);
            }
            if (originalContext.length()
                    > Math.clamp(properties.getMaxContextChars(), 2000, 200_000)) {
                rules.add(Rule.CONTEXT_LENGTH);
                throw rejected(ErrorCode.PARAMS_ERROR, "附件或选中元素内容过长，请精简后重试", trace, rules);
            }
            var localMessage = LocalPromptPolicy.inspect(originalMessage);
            var localContext = LocalPromptPolicy.inspect(originalContext);
            rules.addAll(localMessage.rules());
            rules.addAll(localContext.rules());
            cleanMessage = localMessage.text();
            cleanContext = localContext.text();
            if (localMessage.blocked()
                    || localContext.blocked()
                    || !LocalPromptPolicy.hasRequest(cleanMessage)) throw blocked(trace, rules);

            if (properties.isSemanticEnabled()) {
                provider = "MODEL";
                Review review = checkedReview(cleanMessage, cleanContext);
                rules.addAll(review.rules());
                if (review.action() == Action.BLOCK) throw blocked(trace, rules);
                if (review.action() == Action.REWRITE) {
                    if (review.redactions().isEmpty()) throw new IllegalStateException();
                    for (Redaction item : review.redactions()) {
                        if (item == null
                                || item.kind() == null
                                || item.text() == null
                                || item.text().isBlank()
                                || item.text().length() > 10_000) throw new IllegalStateException();
                        String replacement =
                                item.kind() == Kind.INSTRUCTION_OVERRIDE ? "" : "[已移除敏感信息]";
                        rules.add(
                                switch (item.kind()) {
                                    case SECRET -> Rule.SECRET_REDACTION;
                                    case PERSONAL_DATA -> Rule.PERSONAL_DATA;
                                    case INSTRUCTION_OVERRIDE -> Rule.PROMPT_INJECTION;
                                });
                        switch (Objects.toString(item.field(), "")) {
                            case "message" -> {
                                if (!cleanMessage.contains(item.text()))
                                    throw new IllegalStateException();
                                cleanMessage =
                                        cleanMessage.replace(item.text(), replacement).strip();
                            }
                            case "context" -> {
                                if (!cleanContext.contains(item.text()))
                                    throw new IllegalStateException();
                                cleanContext = cleanContext.replace(item.text(), replacement);
                            }
                            default -> throw new IllegalStateException();
                        }
                    }
                    rules.add(Rule.SEMANTIC_REWRITE);
                    var checkMessage = LocalPromptPolicy.inspect(cleanMessage);
                    var checkContext = LocalPromptPolicy.inspect(cleanContext);
                    if (!LocalPromptPolicy.hasRequest(cleanMessage)
                            || checkMessage.blocked()
                            || checkContext.blocked()
                            || !checkMessage.text().equals(cleanMessage)
                            || !checkContext.text().equals(cleanContext))
                        throw blocked(trace, rules);
                    // At most one rewrite and one recheck: no unbounded re-prompt loop.
                    Review recheck = checkedReview(cleanMessage, cleanContext);
                    if (recheck.action() != Action.ALLOW) {
                        rules.addAll(recheck.rules());
                        throw blocked(trace, rules);
                    }
                }
            }
            boolean changed =
                    !cleanMessage.equals(originalMessage) || !cleanContext.equals(originalContext);
            Action action = changed ? Action.REWRITE : Action.ALLOW;
            if (changed)
                audit(
                        trace,
                        userId,
                        appId,
                        source,
                        action.name(),
                        rules,
                        provider,
                        original,
                        cleanMessage + "\u0000" + cleanContext);
            return new Prepared(cleanMessage, cleanContext, trace, action, List.copyOf(rules));
        } catch (PromptGuardrailException blocked) {
            audit(trace, userId, appId, source, "BLOCK", rules, provider, original, "");
            throw blocked;
        } catch (RuntimeException unavailable) {
            rules.add(Rule.REVIEW_UNAVAILABLE);
            audit(trace, userId, appId, source, "ERROR", rules, provider, original, "");
            // Do not silently allow a request when the configured reviewer fails.
            throw rejected(ErrorCode.GUARDRAIL_UNAVAILABLE, "安全检测暂不可用，请稍后重试", trace, rules);
        }
    }

    private Review checkedReview(String message, String context) {
        if (reviewer == null) throw new IllegalStateException();
        Review result = reviewer.review(message, context);
        if (result == null
                || result.action() == null
                || result.rules() == null
                || result.redactions() == null
                || result.rules().size() > 12
                || result.redactions().size() > 20
                || result.rules().stream().anyMatch(Objects::isNull)
                || (result.action() == Action.ALLOW && !result.rules().isEmpty())
                || (result.action() != Action.REWRITE && !result.redactions().isEmpty()))
            throw new IllegalStateException();
        return result;
    }

    private PromptGuardrailException blocked(String trace, Set<Rule> rules) {
        if (rules.isEmpty()) rules.add(Rule.UNSAFE_CONTENT);
        String message =
                rules.contains(Rule.UNSAFE_CONTENT)
                        ? "请求涉及不安全用途，请调整需求后重试"
                        : rules.contains(Rule.PROMPT_INJECTION)
                                ? "检测到越权指令，请保留网站需求后重试"
                                : rules.contains(Rule.OUT_OF_SCOPE)
                                        ? "当前仅支持网站生成与修改，请调整需求后重试"
                                        : "输入包含不当内容或敏感信息，请修改后重试";
        return rejected(ErrorCode.GUARDRAIL_REJECTED, message, trace, rules);
    }

    private PromptGuardrailException rejected(
            ErrorCode code, String text, String trace, Collection<Rule> rules) {
        return new PromptGuardrailException(code, text, trace, rules);
    }

    private void audit(
            String trace,
            Long userId,
            Long appId,
            String source,
            String action,
            Collection<Rule> rules,
            String provider,
            String input,
            String output) {
        audit.record(
                new GuardrailAudit.Event(
                        trace,
                        userId,
                        appId,
                        source,
                        action,
                        List.copyOf(rules),
                        provider,
                        GuardrailAudit.hash(input),
                        GuardrailAudit.hash(output),
                        input.length(),
                        output.length()));
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        try {
            String input = userMessage == null ? "" : userMessage.singleText();
            Prepared prepared = prepare(input, "", null, null, "SDK");
            return prepared.rewritten() ? successWith(prepared.message()) : success();
        } catch (PromptGuardrailException error) {
            return fatal(error.getMessage(), error);
        }
    }

    /**
     * Legacy void callers cannot consume a rewrite, so never approve an unchanged unsafe original.
     */
    public void ensureSafe(String input) {
        Prepared prepared = prepare(input, "", null, null, "LEGACY");
        if (prepared.rewritten()) {
            audit(
                    prepared.requestId(),
                    null,
                    null,
                    "LEGACY",
                    "BLOCK",
                    prepared.rules(),
                    "LOCAL",
                    Objects.toString(input, ""),
                    "");
            throw rejected(
                    ErrorCode.GUARDRAIL_REJECTED,
                    "输入包含不当内容，请修改后重试",
                    prepared.requestId(),
                    prepared.rules());
        }
    }
}
