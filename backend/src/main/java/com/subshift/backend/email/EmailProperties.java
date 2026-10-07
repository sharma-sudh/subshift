package com.subshift.backend.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds email.* from application.yml. Both values are optional overrides. */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        // Gmail search syntax: a cheap pre-filter before anything goes to Gemini.
        @DefaultValue("newer_than:90d (renewal OR receipt OR invoice OR subscription OR billing OR membership OR \"payment received\")")
        String searchQuery,
        @DefaultValue("6000") int maxBodyChars) {}
