package com.subshift.backend.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Binds gemini.* from application.yml.
 * models is the FALLBACK ORDER: the first model is tried first, and when it is rate limited
 * (or unavailable) the next one is used. Every model has its own quota, so falling back
 * effectively gives extra capacity. Quality first, cheaper "lite" models last.
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        @DefaultValue({"gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite"})
        List<String> models,
        // Minimum gap between two calls to the same model.
        @DefaultValue("1000") long minIntervalMs) {

    private static final List<String> DEFAULT_MODELS =
            List.of("gemini-3.5-flash", "gemini-2.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite");

    public GeminiProperties {
        List<String> cleaned = models == null ? List.of()
                : models.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        models = cleaned.isEmpty() ? DEFAULT_MODELS : cleaned;
    }
}
