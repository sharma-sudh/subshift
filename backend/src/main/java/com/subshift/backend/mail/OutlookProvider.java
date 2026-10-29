package com.subshift.backend.mail;

import com.subshift.backend.common.AppTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.subshift.backend.common.RestClients;
import com.subshift.backend.config.OutlookOAuthProperties;
import com.subshift.backend.email.EmailFetcher;
import com.subshift.backend.email.EmailFetcher.FetchedEmail;
import com.subshift.backend.email.EmailProperties;
import com.subshift.backend.gmail.GmailConnection;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.gmail.TokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Outlook / Microsoft 365 mail through the Microsoft Graph REST API (OAuth, read-only Mail.Read). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutlookProvider implements MailProvider {

    private static final String MESSAGES_URL = "https://graph.microsoft.com/v1.0/me/messages";
    /** Each page is up to 100 messages, so this reads at most the newest 1000 in the time window. */
    private static final int MAX_PAGES = 10;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefreshResponse(@JsonProperty("access_token") String accessToken,
                           @JsonProperty("refresh_token") String refreshToken) {}

    private final GmailConnectionRepository connectionRepository;
    private final TokenEncryptor encryptor;
    private final OutlookOAuthProperties oauthProps;
    private final EmailProperties emailProps;
    private final RestClient http = RestClients.withTimeouts(Duration.ofSeconds(20));

    @Override
    public ProviderType type() {
        return ProviderType.OUTLOOK;
    }

    @Override
    public MailSession open(Integer userId) {
        String accessToken = freshAccessToken(userId);
        return new MailSession() {
            @Override
            public List<String> listMessageIds(Instant since, int max) {
                return OutlookProvider.this.listMessageIds(accessToken, since, max);
            }

            @Override
            public FetchedEmail fetchMessage(String id) {
                return OutlookProvider.this.fetchMessage(accessToken, id);
            }

            @Override
            public void close() {
                // Nothing to release: every call is a separate HTTPS request.
            }
        };
    }

    /** Turns the stored refresh token into a short-lived access token. Microsoft may rotate the refresh token. */
    private String freshAccessToken(Integer userId) {
        GmailConnection connection = connectionRepository.findByUserId(userId)
                .filter(c -> c.getProvider() == ProviderType.OUTLOOK && c.getRefreshTokenEncrypted() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Outlook is not connected"));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", oauthProps.clientId());
        form.add("client_secret", oauthProps.clientSecret());
        form.add("refresh_token", encryptor.decrypt(connection.getRefreshTokenEncrypted()));
        form.add("grant_type", "refresh_token");
        form.add("scope", OutlookOAuthService.SCOPE);
        try {
            RefreshResponse response = http.post()
                    .uri(String.format(OutlookOAuthService.TOKEN_URL, oauthProps.tenantOrCommon()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RefreshResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Microsoft returned no access token");
            }
            if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
                connection.setRefreshTokenEncrypted(encryptor.encrypt(response.refreshToken()));
                connectionRepository.save(connection);
            }
            return response.accessToken();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Microsoft token refresh failed with HTTP {}", status);
            if (status == 400 || status == 401) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Outlook connection expired or was revoked; please reconnect Outlook");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Microsoft token refresh failed");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Microsoft");
        }
    }

    /**
     * Newest first. Graph can't combine a date filter, sorting and a subject search reliably, so the server filters
     * by date and the subject keywords are applied here, on the short list of subjects.
     */
    private List<String> listMessageIds(String accessToken, Instant since, int max) {
        Instant from = (since != null ? since : Instant.now().minus(emailProps.lookbackDays(), ChronoUnit.DAYS))
                .truncatedTo(ChronoUnit.SECONDS);
        URI next = UriComponentsBuilder.fromUriString(MESSAGES_URL)
                .queryParam("$filter", "receivedDateTime ge " + from)
                .queryParam("$orderby", "receivedDateTime desc")
                .queryParam("$select", "id,subject")
                .queryParam("$top", 100)
                .build().encode().toUri();

        List<String> ids = new ArrayList<>();
        for (int page = 0; next != null && page < MAX_PAGES && ids.size() < max; page++) {
            JsonNode body = get(next, accessToken, false);
            for (JsonNode message : body.path("value")) {
                if (subjectMatches(message.path("subject").asText(""))) {
                    ids.add(message.path("id").asText());
                    if (ids.size() >= max) {
                        break;
                    }
                }
            }
            String link = body.path("@odata.nextLink").asText("");
            next = link.isEmpty() ? null : URI.create(link);
        }
        return ids;
    }

    private FetchedEmail fetchMessage(String accessToken, String id) {
        URI uri = UriComponentsBuilder.fromUriString(MESSAGES_URL)
                .pathSegment(id)
                .queryParam("$select", "id,subject,from,receivedDateTime,body")
                .build().encode().toUri();
        JsonNode message = get(uri, accessToken, true);

        Instant receivedAt = parseInstant(message.path("receivedDateTime").asText(""));
        JsonNode sender = message.path("from").path("emailAddress");
        String from = sender.path("name").asText("");
        String address = sender.path("address").asText("");
        String fromText = from.isBlank() ? address : (address.isBlank() ? from : from + " <" + address + ">");

        String content = message.path("body").path("content").asText("");
        String text = "html".equalsIgnoreCase(message.path("body").path("contentType").asText(""))
                ? EmailFetcher.htmlToText(content)
                : content.replaceAll("\\s+", " ").trim();
        return new FetchedEmail(id, message.path("subject").asText(""), fromText,
                AppTime.dateOf(receivedAt), receivedAt,
                EmailFetcher.truncate(text, emailProps.maxBodyChars()));
    }

    private boolean subjectMatches(String subject) {
        String lower = subject.toLowerCase(Locale.ROOT);
        for (String keyword : emailProps.subjectKeywords()) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private JsonNode get(URI uri, String accessToken, boolean textBody) {
        try {
            RestClient.RequestHeadersSpec<?> request = http.get().uri(uri)
                    .header("Authorization", "Bearer " + accessToken);
            if (textBody) {
                // Ask Graph for a plain-text body so no HTML has to be stripped.
                request = request.header("Prefer", "outlook.body-content-type=\"text\"");
            }
            JsonNode body = request.retrieve().body(JsonNode.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Outlook returned an empty reply");
            }
            return body;
        } catch (RestClientResponseException e) {
            log.warn("Microsoft Graph returned HTTP {}", e.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Outlook returned an error");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Outlook");
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
