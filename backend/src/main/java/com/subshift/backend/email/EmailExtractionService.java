package com.subshift.backend.email;

import com.subshift.backend.catalog.Service;
import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.email.GeminiClient.Extraction;
import com.subshift.backend.gmail.GmailConnection;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.mail.MailProviders;
import com.subshift.backend.mail.MailSession;
import com.subshift.backend.ml.EmailClassifierService;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EmailExtractionService {

    public record ScanItem(String messageId, String subject, String from, LocalDate emailDate,
                           boolean isRenewal, boolean paymentFailed, boolean recurring,
                           String serviceName, String planName, BigDecimal price, String currency,
                           LocalDate billingDate, String model, String error, Double renewalScore) {

        /** Marks an email the local classifier skipped, so Gemini was never called for it. */
        public static final String CLASSIFIER_MODEL = "local-classifier";

        /** Marks an email with no wording about repeating billing, skipped before Gemini was called. */
        public static final String RECURRENCE_FILTER_MODEL = "local-recurrence-check";

        static ScanItem of(FetchedEmail email, Extraction x, Double renewalScore) {
            // If the email states no date, fall back to the day it was received.
            LocalDate date = x.billingDate() != null ? x.billingDate() : email.date();
            // The model's "recurring" is only trusted if the email text has some wording about repeating billing.
            boolean recurring = x.recurring() && RecurrenceEvidence.present(email.subject(), email.body());
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    x.isRenewal(), x.paymentFailed(), recurring, x.serviceName(), x.planName(), x.price(),
                    x.currency(), date, x.model(), null, renewalScore);
        }

        static ScanItem failed(FetchedEmail email, String error) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, false, false, null, null, null, null, null, null, error, null);
        }

        static ScanItem filtered(FetchedEmail email, double renewalScore) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, false, false, null, null, null, null, null, CLASSIFIER_MODEL, null, renewalScore);
        }

        static ScanItem noRecurrenceWording(FetchedEmail email, Double renewalScore) {
            return new ScanItem(email.id(), email.subject(), email.from(), email.date(),
                    false, false, false, null, null, null, null, null, RECURRENCE_FILTER_MODEL, null, renewalScore);
        }
    }

    /** The text shown to the classifier. It matches the format of the stored training emails. */
    static String classifierText(FetchedEmail email) {
        return "Subject: " + email.subject() + "\n\n" + email.body();
    }

    /** Names of the services already in the catalog, so the model can reuse them instead of inventing variants. */
    static List<String> knownServiceNames(ServiceRepository serviceRepository) {
        return serviceRepository.findAll().stream()
                .map(Service::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private final CurrentUserService currentUserService;
    private final GmailConnectionRepository connectionRepository;
    private final MailProviders mailProviders;
    private final ServiceRepository serviceRepository;
    private final GeminiClient geminiClient;
    private final EmailClassifierService classifierService;

    public List<ScanItem> scan(String userEmail, int limit) {
        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured");
        }
        User user = currentUserService.require(userEmail);
        GmailConnection connection = connectionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No mailbox is connected"));
        List<String> known = knownServiceNames(serviceRepository);

        List<ScanItem> items = new ArrayList<>();
        try (MailSession mailbox = mailProviders.open(user.getId(), connection.getProvider())) {
            for (String id : mailbox.listMessageIds(null, limit)) {
                FetchedEmail email = mailbox.fetchMessage(id);
                // Dry run: the classifier score is shown next to Gemini's answer but nothing is skipped.
                Double score = classifierService.score(classifierText(email));
                try {
                    items.add(ScanItem.of(email, geminiClient.extract(email, known), score));
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
        }
        return items;
    }
}
