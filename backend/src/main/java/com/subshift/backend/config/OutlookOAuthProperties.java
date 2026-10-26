package com.subshift.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the microsoft.oauth.* block in application.yml (an Azure app registration). */
@ConfigurationProperties(prefix = "microsoft.oauth")
public record OutlookOAuthProperties(String clientId, String clientSecret, String redirectUri, String tenant) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** "common" accepts both personal Microsoft accounts and work/school accounts. */
    public String tenantOrCommon() {
        return tenant == null || tenant.isBlank() ? "common" : tenant.trim();
    }
}
