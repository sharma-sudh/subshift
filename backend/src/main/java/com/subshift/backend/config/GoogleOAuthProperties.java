package com.subshift.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the google.oauth.* block in application.yml. */
@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(String clientId, String clientSecret, String redirectUri) {}
