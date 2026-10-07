package com.subshift.backend.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.subshift.backend.auth.JwtService;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.config.GoogleOAuthProperties;
import com.subshift.backend.users.User;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;

@Slf4j
@Service
public class GmailOAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    /** Read-only access to the mailbox: the minimum needed to fetch renewal emails. */
    private static final String GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String STATE_PURPOSE = "gmail-oauth";

    public record StatusResponse(boolean connected, LocalDateTime connectedAt, LocalDateTime lastSyncedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         String scope) {}

    private final GoogleOAuthProperties props;
    private final JwtService jwtService;
    private final TokenEncryptor encryptor;
    private final GmailConnectionRepository connectionRepository;
    private final CurrentUserService currentUserService;
    private final String frontendUrl;
    private final RestClient http = RestClient.create();

    public GmailOAuthService(GoogleOAuthProperties props, JwtService jwtService, TokenEncryptor encryptor,
                             GmailConnectionRepository connectionRepository,
                             CurrentUserService currentUserService,
                             @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.props = props;
        this.jwtService = jwtService;
        this.encryptor = encryptor;
        this.connectionRepository = connectionRepository;
        this.currentUserService = currentUserService;
        this.frontendUrl = frontendUrl;
    }

    /** Step 1: build the Google consent URL, with a signed state that identifies this user. */
    public String buildAuthUrl(String email) {
        if (props.clientId() == null || props.clientId().isBlank()
                || props.clientSecret() == null || props.clientSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google OAuth is not configured");
        }
        currentUserService.require(email);
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", GMAIL_SCOPE)
                .queryParam("access_type", "offline")   // we need a refresh token
                .queryParam("prompt", "consent")         // forces Google to return it every time
                .queryParam("state", jwtService.generateStateToken(email, STATE_PURPOSE))
                .build().encode().toUriString();
    }

    /**
     * Step 2: Google sends the browser back with ?code=...&state=... .
     * Never throws; always returns the frontend URL the browser should be sent to.
     */
    public String handleCallback(String code, String state, String error) {
        if (error != null) {
            return failure("access_denied"); // user pressed Cancel, or Google refused
        }
        if (code == null || state == null) {
            return failure("invalid_request");
        }
        String email;
        try {
            email = jwtService.parseStateToken(state, STATE_PURPOSE);
        } catch (JwtException | IllegalArgumentException e) {
            return failure("invalid_state");
        }
        try {
            User user = currentUserService.require(email);
            TokenResponse tokens = exchangeCode(code);
            if (tokens == null || tokens.accessToken() == null || tokens.refreshToken() == null) {
                return failure("no_refresh_token");
            }
            // The consent screen lets people untick individual permissions.
            if (tokens.scope() == null || !tokens.scope().contains(GMAIL_SCOPE)) {
                return failure("scope_missing");
            }
            save(user, tokens);
            return frontendUrl + "/dashboard?gmail=connected";
        } catch (RestClientException e) {
            log.warn("Google token exchange failed: {}", e.getClass().getSimpleName());
            return failure("token_exchange_failed");
        } catch (Exception e) {
            log.error("Gmail connection failed", e);
            return failure("server_error");
        }
    }

    public StatusResponse status(String email) {
        User user = currentUserService.require(email);
        return connectionRepository.findByUserId(user.getId())
                .map(c -> new StatusResponse(true, c.getConnectedAt(), c.getLastSyncedAt()))
                .orElse(new StatusResponse(false, null, null));
    }

    public void disconnect(String email) {
        User user = currentUserService.require(email);
        connectionRepository.findByUserId(user.getId()).ifPresent(c -> {
            revokeAtGoogle(c);
            connectionRepository.delete(c);
        });
    }

    private TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");
        return http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    private void save(User user, TokenResponse tokens) {
        // One row per user (user_id is UNIQUE): reconnecting just replaces the tokens.
        GmailConnection connection = connectionRepository.findByUserId(user.getId())
                .orElseGet(GmailConnection::new);
        connection.setUser(user);
        connection.setAccessTokenEncrypted(encryptor.encrypt(tokens.accessToken()));
        connection.setRefreshTokenEncrypted(encryptor.encrypt(tokens.refreshToken()));
        connectionRepository.save(connection);
    }

    private void revokeAtGoogle(GmailConnection connection) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", encryptor.decrypt(connection.getRefreshTokenEncrypted()));
            http.post().uri(REVOKE_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Best effort: the local connection is removed regardless.
            log.warn("Could not revoke Google token: {}", e.getClass().getSimpleName());
        }
    }

    private String failure(String reason) {
        return frontendUrl + "/dashboard?gmail=error&reason=" + reason;
    }
}
