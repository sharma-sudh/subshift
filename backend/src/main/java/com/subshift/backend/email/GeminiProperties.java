package com.subshift.backend.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds gemini.* from application.yml. */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, @DefaultValue("gemini-3.5-flash") String model) {}
