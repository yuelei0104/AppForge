package com.yuelei.appforge.ai.guardrail;

import java.util.List;

/** A reviewer can request removals/redactions, but cannot add instructions or new requirements. */
public interface PromptReviewer {
    Review review(String message, String context);

    enum Action {
        ALLOW,
        REWRITE,
        BLOCK
    }

    enum Kind {
        SECRET,
        PERSONAL_DATA,
        INSTRUCTION_OVERRIDE
    }

    enum Rule {
        INPUT_EMPTY,
        INPUT_LENGTH,
        CONTEXT_LENGTH,
        SECRET_REDACTION,
        PROMPT_INJECTION,
        UNSAFE_CONTENT,
        PERSONAL_DATA,
        OUT_OF_SCOPE,
        SEMANTIC_REWRITE,
        REVIEW_UNAVAILABLE
    }

    record Redaction(String field, String text, Kind kind) {}

    record Review(Action action, List<Rule> rules, List<Redaction> redactions) {}
}
