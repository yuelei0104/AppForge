package com.yuelei.appforge.ai.guardrail;

import com.yuelei.appforge.exception.ErrorCode;

import dev.langchain4j.guardrail.GuardrailException;

import java.util.*;

/** Works for synchronous MVC failures and wrapped asynchronous SDK failures alike. */
public final class GuardrailErrors {
    private GuardrailErrors() {}

    public static String message(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean sdk = false;
        for (Throwable current = error;
                current != null && visited.add(current);
                current = current.getCause()) {
            if (current instanceof PromptGuardrailException guardrail)
                return guardrail.getMessage();
            if (current instanceof GuardrailException) sdk = true;
        }
        return sdk ? "请求未通过安全检查，请调整内容后重试" : null;
    }

    public static Map<String, Object> details(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        GuardrailException sdk = null;
        for (Throwable current = error;
                current != null && visited.add(current);
                current = current.getCause()) {
            if (current instanceof PromptGuardrailException guardrail) return guardrail.toMap();
            if (current instanceof GuardrailException guardrail) sdk = guardrail;
        }
        if (sdk != null) {
            String trace = UUID.randomUUID().toString();
            new GuardrailAudit()
                    .record(
                            new GuardrailAudit.Event(
                                    trace,
                                    null,
                                    null,
                                    "SDK",
                                    "BLOCK",
                                    List.of(PromptReviewer.Rule.UNSAFE_CONTENT),
                                    "SDK",
                                    "",
                                    "",
                                    0,
                                    0));
            return Map.of(
                    "error",
                    true,
                    "guardrail",
                    true,
                    "code",
                    ErrorCode.GUARDRAIL_REJECTED.getCode(),
                    "message",
                    "请求未通过安全检查，请调整内容后重试",
                    "requestId",
                    trace,
                    "rules",
                    List.of(PromptReviewer.Rule.UNSAFE_CONTENT));
        }
        return null;
    }
}
