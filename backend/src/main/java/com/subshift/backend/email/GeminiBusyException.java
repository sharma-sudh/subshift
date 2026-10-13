package com.subshift.backend.email;

/** Every configured Gemini model is rate limited or unavailable right now. Retry later. */
public class GeminiBusyException extends RuntimeException {

    public GeminiBusyException(String message) {
        super(message);
    }
}
