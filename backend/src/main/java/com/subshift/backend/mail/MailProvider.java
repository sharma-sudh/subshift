package com.subshift.backend.mail;

/** One way of reading mail. To add a provider, implement this and make it a Spring @Component. */
public interface MailProvider {

    ProviderType type();

    /** Opens a session using the credentials stored for this user. Throws 409 if they are missing or no longer valid. */
    MailSession open(Integer userId);
}
