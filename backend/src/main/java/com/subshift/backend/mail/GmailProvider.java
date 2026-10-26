package com.subshift.backend.mail;

import com.subshift.backend.email.EmailFetcher;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.gmail.GmailTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Gmail through the Gmail REST API (OAuth, read-only). The existing fetcher does the work. */
@Component
@RequiredArgsConstructor
public class GmailProvider implements MailProvider {

    private final GmailTokenService tokenService;
    private final EmailFetcher fetcher;

    @Override
    public ProviderType type() {
        return ProviderType.GMAIL;
    }

    @Override
    public MailSession open(Integer userId) {
        String accessToken = tokenService.freshAccessToken(userId);
        return new MailSession() {
            @Override
            public List<String> listMessageIds(Instant since, int max) {
                return fetcher.listMessageIds(accessToken, since, max);
            }

            @Override
            public FetchedEmail fetchMessage(String id) {
                return fetcher.fetchMessage(accessToken, id);
            }

            @Override
            public void close() {
                // Nothing to release: every call is a separate HTTPS request.
            }
        };
    }
}
