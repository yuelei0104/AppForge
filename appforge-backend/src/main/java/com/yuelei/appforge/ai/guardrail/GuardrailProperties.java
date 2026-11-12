package com.yuelei.appforge.ai.guardrail;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dedicated semantic review configuration; blank connection settings reuse the ordinary chat
 * settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "appforge.guardrail")
public class GuardrailProperties {
    private boolean semanticEnabled = true;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private int timeoutSeconds = 15;
    private int maxContextChars = 80_000;
}
