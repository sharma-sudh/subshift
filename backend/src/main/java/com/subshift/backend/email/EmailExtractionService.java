package com.subshift.backend.email;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.email.GeminiClient.Extraction;
import com.subshift.backend.gmail.GmailTokenService;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetch candidate emails, then ask Gemini to extract renewal data from each.
 * This step only READS and EXTRACTS. Nothing is written to the database; persisting the results
 * (price_log, alerts, notifications) belongs to the transactional pipeline built on top of this.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailExtractionService {

    public record ScanItem(String messageId, String subject, String from, LocalDate emailDate,
                           boolean isRenewal, String serviceName, BigDecimal price, String currency,
                           LocalDate billingDate, String error) {

        static ScanItem of(FetchedEmail email, Extraction x) {
            // If the email states no date, fall back to the day it was received.
            LocalDate date = x.billingDate() != null ? x.billingDate() : email.date();
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    x.isRenewal(), x.serviceName(), x.price(), x.currency(), date, null);
        }

        static ScanItem failed(FetchedEmail email, String error) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, null, null, null, null, error);
        }
    }

    private final CurrentUserService currentUserService;
    private final GmailTokenService gmailTokenService;
    private final EmailFetcher emailFetcher;
    private final GeminiClient geminiClient;

    public List<ScanItem> scan(String userEmail, int limit) {
        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured");
        }
        User user = currentUserService.require(userEmail);
        String accessToken = gmailTokenService.freshAccessToken(user.getId());
        List<FetchedEmail> emails = emailFetcher.fetch(accessToken, limit);

        List<ScanItem> items = new ArrayList<>();
        for (FetchedEmail email : emails) {
            try {
                items.add(ScanItem.of(email, geminiClient.extract(email)));
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                log.warn("Gemini call failed for message {}: HTTP {}", email.id(), status);
                items.add(ScanItem.failed(email, "Gemini returned HTTP " + status));
                if (status == 429) {
                    break; // rate limited: stop instead of hammering the API
                }
            } catch (Exception e) {
                log.warn("Extraction failed for message {}: {}", email.id(), e.getMessage());
                items.add(ScanItem.failed(email, "Extraction failed"));
            }
        }
        return items;
    }
}
