package com.yuelei.appforge.ai.guardrail;

import cn.hutool.json.JSONUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Audit interventions without storing prompts, credentials, model replies or uploaded document
 * text.
 */
@Component
public class GuardrailAudit {
    private static final Logger LOG = LoggerFactory.getLogger("APPFORGE_GUARDRAIL_AUDIT");

    public record Event(
            String requestId,
            Long userId,
            Long appId,
            String source,
            String action,
            List<PromptReviewer.Rule> rules,
            String provider,
            String inputHash,
            String outputHash,
            int inputLength,
            int outputLength) {
        public Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("event", "prompt_guardrail");
            value.put("time", Instant.now().toString());
            value.put("requestId", requestId);
            value.put("userId", userId);
            value.put("appId", appId);
            value.put("source", source);
            value.put("action", action);
            value.put("rules", rules);
            value.put("provider", provider);
            value.put("inputHash", inputHash);
            value.put("outputHash", outputHash);
            value.put("inputLength", inputLength);
            value.put("outputLength", outputLength);
            return value;
        }
    }

    public void record(Event event) {
        LOG.info("{}", JSONUtil.toJsonStr(event.toMap()));
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
