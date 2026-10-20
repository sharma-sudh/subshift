package com.subshift.backend.email;

import java.util.regex.Pattern;

/**
 * A code-side check on the model's "recurring" answer. Gemini sometimes calls a one-off purchase recurring
 * (it did for a phone-case order), and its answer can change between runs. A charge only counts as recurring
 * if the email text itself contains some wording about repeating billing.
 */
final class RecurrenceEvidence {

    private static final Pattern WORDS = Pattern.compile(
            "subscri|renew|recurring|auto[- ]?pay|autopay|membership|billing (cycle|period|date)"
                    + "|next (billing|payment|charge)|(per|a|every|each) (month|year|week|quarter)"
                    + "|monthly|yearly|annual|weekly|quarterly|/ ?(mo|month|yr|year)\\b",
            Pattern.CASE_INSENSITIVE);

    private RecurrenceEvidence() {}

    /** True if the subject or body says anything about the charge repeating. */
    static boolean present(String subject, String body) {
        return WORDS.matcher((subject == null ? "" : subject) + "\n" + (body == null ? "" : body)).find();
    }
}
