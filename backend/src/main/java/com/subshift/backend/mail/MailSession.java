package com.subshift.backend.mail;

import com.subshift.backend.email.EmailFetcher.FetchedEmail;

import java.time.Instant;
import java.util.List;

/**
 * One open connection to a user's mailbox, used for a single scan or sync. Close it when done
 * (try-with-resources): the IMAP implementation keeps a socket open.
 * Implementations throw ResponseStatusException (usually 502) when the mail service fails.
 */
public interface MailSession extends AutoCloseable {

    /**
     * Ids of candidate emails (the cheap subject pre-filter), NEWEST FIRST, at most max.
     * since == null means "the last few months" (email.lookback-days).
     */
    List<String> listMessageIds(Instant since, int max);

    FetchedEmail fetchMessage(String id);

    /** Never throws. */
    @Override
    void close();
}
