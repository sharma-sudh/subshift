package com.subshift.backend.email;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.email.GeminiClient.Extraction;
import com.subshift.backend.gmail.GmailTokenService;
import com.subshift.backend.ml.EmailClassifierService;
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
 * Dry run: fetch the newest candidate emails and show what Gemini extracts from each.
 * Nothing is saved. EmailSyncService is the version that writes to the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailExtractionService {

    public record ScanItem(String messageId, String subject, String from, LocalDate emailDate,
                           boolean isRenewal, String serviceName, BigDecimal price, String currency,
                           LocalDate billingDate, String model, String error, Double renewalScore) {

        /** Marks an email the local classifier skipped, so Gemini was never called for it. */
        public static final String CLASSIFIER_MODEL = "local-classifier";

        static ScanItem of(FetchedEmail email, Extraction x, Double renewalScore) {
            // If the email states no date, fall back to the day it was received.
            LocalDate date = x.billingDate() != null ? x.billingDate() : email.date();
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    x.isRenewal(), x.serviceName(), x.price(), x.currency(), date, x.model(), null, renewalScore);
        }

        static ScanItem failed(FetchedEmail email, String error) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, null, null, null, null, null, error, null);
        }

        static ScanItem filtered(FetchedEmail email, double renewalScore) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, null, null, null, null, CLASSIFIER_MODEL, null, renewalScore);
        }
    }

    /** The text shown to the classifier. It matches the format of the stored training emails. */
    static String classifierText(FetchedEmail email) {
        return "Subject: " + email.subject() + "\n\n" + email.body();
    }

    private final CurrentUserService currentUserService;
    private final GmailTokenService gmailTokenService;
    private final EmailFetcher emailFetcher;
    private final GeminiClient geminiClient;
    private final EmailClassifierService classifierService;

    public List<ScanItem> scan(String userEmail, int limit) {
        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured");
        }
        User user = currentUserService.require(userEmail);
        String accessToken = gmailTokenService.freshAccessToken(user.getId());

        List<ScanItem> items = new ArrayList<>();
        for (String id : emailFetcher.listMessageIds(accessToken, null, limit)) {
            FetchedEmail email = emailFetcher.fetchMessage(accessToken, id);
            // Dry run: the classifier score is shown next to Gemini's answer but nothing is skipped.
            Double score = classifierService.score(classifierText(email));
            try {
                items.add(ScanItem.of(email, geminiClient.extract(email), score));
            } catch (GeminiBusyException e) {
                items.add(ScanItem.failed(email, "All Gemini models are rate limited"));
                break;
            } catch (RestClientResponseException e) {
                items.add(ScanItem.failed(email, "Gemini returned HTTP " + e.getStatusCode().value()));
            } catch (Exception e) {
                log.warn("Extraction failed for message {}: {}", email.id(), e.getMessage());
                items.add(ScanItem.failed(email, "Extraction failed"));
            }
        }
        return items;
    }
}
