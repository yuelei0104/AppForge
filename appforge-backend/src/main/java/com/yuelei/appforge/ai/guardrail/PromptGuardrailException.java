package com.yuelei.appforge.ai.guardrail;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;

import java.util.*;

/** Only controlled messages and rule identifiers are exposed to SSE clients. */
public final class PromptGuardrailException extends BusinessException {
    private final String requestId;
    private final List<PromptReviewer.Rule> rules;

    PromptGuardrailException(
            ErrorCode code,
            String message,
            String requestId,
            Collection<PromptReviewer.Rule> rules) {
        super(code, message);
        this.requestId = requestId;
        this.rules = List.copyOf(rules);
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "error",
                true,
                "guardrail",
                true,
                "code",
                getCode(),
                "message",
                getMessage(),
                "requestId",
                requestId,
                "rules",
                rules);
    }
}
