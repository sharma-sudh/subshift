package com.subshift.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the jwt.* block in application.yml. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {}
