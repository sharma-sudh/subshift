package com.subshift.backend.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.subshift.backend.common.RestClients;
import com.subshift.backend.config.GoogleOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/** Turns a user's stored (encrypted) refresh token into a short-lived access token for Gmail API calls. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmailTokenService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefreshResponse(@JsonProperty("access_token") String accessToken) {}

    private final GmailConnectionRepository connectionRepository;
    private final TokenEncryptor encryptor;
    private final GoogleOAuthProperties props;
    private final RestClient http = RestClients.withTimeouts(Duration.ofSeconds(15));

    public String freshAccessToken(Integer userId) {
        GmailConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Gmail is not connected"));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("refresh_token", encryptor.decrypt(connection.getRefreshTokenEncrypted()));
        form.add("grant_type", "refresh_token");

        try {
            RefreshResponse response = http.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RefreshResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google returned no access token");
            }
            return response.accessToken();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Google token refresh failed with HTTP {}", status);
            if (status == 400 || status == 401) {
                // invalid_grant: the user revoked access, or a Testing-mode refresh token hit its 7-day limit.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Gmail connection expired or was revoked; please reconnect Gmail");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google token refresh failed");
        }
    }
}
