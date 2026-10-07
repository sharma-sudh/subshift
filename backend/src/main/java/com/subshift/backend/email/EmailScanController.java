package com.subshift.backend.email;

import com.subshift.backend.email.EmailExtractionService.ScanItem;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailScanController {

    private static final int MAX_LIMIT = 25;

    private final EmailExtractionService emailExtractionService;

    /** Dry run: fetches and extracts but saves nothing. */
    @PostMapping("/scan")
    public List<ScanItem> scan(@RequestParam(defaultValue = "10") int limit, Authentication authentication) {
        int bounded = Math.max(1, Math.min(limit, MAX_LIMIT));
        return emailExtractionService.scan(authentication.getName(), bounded);
    }
}
