package com.subshift.backend.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Binds email.* from application.yml. Every value is an optional override. */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        // Gmail search syntax: a cheap pre-filter before anything goes to Gemini.
        @DefaultValue("newer_than:90d (renewal OR receipt OR invoice OR subscription OR billing OR membership OR \"payment received\")")
        String searchQuery,
        @DefaultValue("6000") int maxBodyChars,
        // The same pre-filter for Outlook and Yahoo, which can't use Gmail's search syntax: the subject must
        // contain one of these words, and only mail from the last lookbackDays days is read on a first sync.
        @DefaultValue({"renewal", "receipt", "invoice", "subscription", "billing", "payment", "membership",
                "your plan", "price change"}) List<String> subjectKeywords,
        @DefaultValue("90") int lookbackDays) {}
