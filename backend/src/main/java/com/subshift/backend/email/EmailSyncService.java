package com.subshift.backend.email;

import com.subshift.backend.catalog.ServiceRepository;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.email.EmailExtractionService.ScanItem;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.gmail.GmailConnection;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.mail.MailProviders;
import com.subshift.backend.mail.MailSession;
import com.subshift.backend.ml.EmailClassifierService;
import com.subshift.backend.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates a sync: list new emails from the user's connected mailbox (Gmail, Outlook or Yahoo), extract each
 * with Gemini (network, no transaction), then hand everything to EmailSyncPersister for ONE transactional write.
 *
 * Emails are processed oldest first and gmail_connections.last_synced_at is a watermark that only
 * moves past emails that were really handled. If Gemini runs out of quota midway, the finished
 * emails are saved and the next sync continues exactly where this one stopped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSyncService {

    private static final int MAX_CANDIDATES = 100;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final Set<Integer> running = ConcurrentHashMap.newKeySet();

    private final CurrentUserService currentUserService;
    private final GmailConnectionRepository connectionRepository;
    private final MailProviders mailProviders;
    private final ServiceRepository serviceRepository;
    private final GeminiClient geminiClient;
    private final EmailSyncPersister persister;
    private final EmailClassifierService classifierService;

    public SyncReport sync(String userEmail, int limit) {
        if (!geminiClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API key is not configured");
        }
        User user = currentUserService.require(userEmail);
        // One sync per user at a time: protects the Gemini quota and the services table.
        if (!running.add(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A sync is already running for this account");
        }
        try {
            return doSync(user, limit);
        } finally {
            running.remove(user.getId());
        }
    }

    private SyncReport doSync(User user, int limit) {
        Instant syncStart = Instant.now();
        GmailConnection connection = connectionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No mailbox is connected"));
        // The 60s overlap guards against clock skew; re-reading an email is harmless.
        Instant since = connection.getLastSyncedAt() == null ? null
                : connection.getLastSyncedAt().atZone(ZONE).toInstant().minusSeconds(60);
        List<String> knownServices = EmailExtractionService.knownServiceNames(serviceRepository);

        List<ScanItem> items = new ArrayList<>();
        Instant watermark = null;
        int processed = 0;
        String stopReason = null;
        List<String> ids;
        try (MailSession mailbox = mailProviders.open(user.getId(), connection.getProvider())) {
            ids = new ArrayList<>(mailbox.listMessageIds(since, MAX_CANDIDATES));
            Collections.reverse(ids); // mailboxes return newest first; we go oldest first
            List<String> batch = ids.subList(0, Math.min(limit, ids.size()));

            for (String id : batch) {
                FetchedEmail email;
                try {
                    email = mailbox.fetchMessage(id);
                } catch (ResponseStatusException e) {
                    stopReason = "mailbox_error";
                    break;
                }
                // The local classifier goes first: a confident "not a renewal" saves a Gemini call.
                Double score = classifierService.score(EmailExtractionService.classifierText(email));
                if (classifierService.shouldSkip(score)) {
                    log.info("Classifier skipped message {} (renewal probability {})",
                            email.id(), String.format("%.3f", score));
                    items.add(ScanItem.filtered(email, score));
                    watermark = email.receivedAt();
                    processed++;
                    continue;
                }
                // An email with no wording about repeating billing is a one-off charge, a promotion or similar.
                // Gemini's answer would be thrown away anyway (see ScanItem.of), so don't spend a call on it.
                if (!RecurrenceEvidence.present(email.subject(), email.body())) {
                    items.add(ScanItem.noRecurrenceWording(email, score));
                    watermark = email.receivedAt();
                    processed++;
                    continue;
                }
                try {
                    items.add(ScanItem.of(email, geminiClient.extract(email, knownServices), score));
                } catch (GeminiBusyException e) {
                    stopReason = "gemini_rate_limited";
                    break; // this email is NOT counted as processed, so the next sync retries it
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (status == 401 || status == 403) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Gemini rejected the API key (HTTP " + status + ")");
                    }
                    items.add(ScanItem.failed(email, "Gemini returned HTTP " + status));
                } catch (Exception e) {
                    log.warn("Extraction failed for message {}: {}", email.id(), e.getMessage());
                    items.add(ScanItem.failed(email, "Extraction failed"));
                }
                watermark = email.receivedAt();
                processed++;
            }
        }

        LocalDateTime newLastSynced = ids.isEmpty() ? toLocal(syncStart)
                : (watermark == null ? null : toLocal(watermark));
        SyncReport saved = persister.persist(user.getId(), items, newLastSynced);
        return new SyncReport(processed, ids.size() - processed, stopReason != null, stopReason,
                saved.pricesRecorded(), saved.hikesNotified(), saved.dropsNotified(), saved.newSubscriptions(),
<<<<<<< HEAD
                saved.newServices(), saved.ignoredNonSubscription(), saved.lastSyncedAt(), saved.outcomes());
=======
                saved.newServices(), saved.lastSyncedAt(), saved.outcomes());
>>>>>>> 6b6bd0521eb4946bb2bef89b191da62c145f2505
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }
}
