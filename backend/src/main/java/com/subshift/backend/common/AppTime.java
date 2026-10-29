package com.subshift.backend.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Time conventions for the whole app:
 * - Timestamps (created_at, sent_at, last_synced_at, ...) are stored and sent to the client in UTC, without a zone
 *   suffix. The frontend shows them in IST.
 * - Calendar dates ("today", the date an email arrived) are IST dates, because that is the day the user sees.
 */
public final class AppTime {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private AppTime() {}

    /** Today's date in India. */
    public static LocalDate today() {
        return LocalDate.now(IST);
    }

    /** The India calendar date of a moment in time. */
    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(IST).toLocalDate();
    }
}
