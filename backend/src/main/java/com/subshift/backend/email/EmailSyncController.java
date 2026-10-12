package com.subshift.backend.email;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailSyncController {

    private static final int MAX_LIMIT = 25;

    private final EmailSyncService emailSyncService;

    /**
     * Reads new emails, extracts renewals with Gemini and saves the results.
     * Can take a while (each email is a separate Gemini call). If emailsRemaining in the response
     * is above 0, call it again to continue.
     */
    @PostMapping("/sync")
    public SyncReport sync(@RequestParam(defaultValue = "10") int limit, Authentication authentication) {
        return emailSyncService.sync(authentication.getName(), Math.max(1, Math.min(limit, MAX_LIMIT)));
    }
}
