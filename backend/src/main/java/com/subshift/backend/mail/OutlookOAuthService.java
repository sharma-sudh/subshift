package com.subshift.backend.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.subshift.backend.auth.JwtService;
import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.config.OutlookOAuthProperties;
import com.subshift.backend.gmail.GmailConnection;
import com.subshift.backend.gmail.GmailConnectionRepository;
import com.subshift.backend.gmail.TokenEncryptor;
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

/**
 * Connects an Outlook / Microsoft account through the Microsoft identity platform (OAuth 2.0 authorization code
 * flow) with the read-only Mail.Read permission. Same shape as GmailOAuthService.
 */
@Slf4j
@Service
public class OutlookOAuthService {

    static final String SCOPE = "offline_access https://graph.microsoft.com/Mail.Read";
    private static final String AUTH_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String STATE_PURPOSE = "outlook-oauth";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         String scope) {}

    private final OutlookOAuthProperties props;
    private final JwtService jwtService;
    private final TokenEncryptor encryptor;
    private final GmailConnectionRepository connectionRepository;
    private final CurrentUserService currentUserService;
    private final String frontendUrl;
    private final RestClient http = RestClient.create();

    public OutlookOAuthService(OutlookOAuthProperties props, JwtService jwtService, TokenEncryptor encryptor,
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

    /** Step 1: the Microsoft consent URL, with a signed state that identifies this user. */
    public String buildAuthUrl(String email) {
        if (!props.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Outlook sign-in is not configured");
        }
        User user = currentUserService.require(email);
        connectionRepository.findByUserId(user.getId())
                .filter(c -> c.getProvider() != ProviderType.OUTLOOK)
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Disconnect " + c.getProvider().label() + " before connecting Outlook");
                });
        return UriComponentsBuilder.fromUriString(String.format(AUTH_URL, props.tenantOrCommon()))
                .queryParam("client_id", props.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_mode", "query")
                .queryParam("scope", SCOPE)
                .queryParam("prompt", "select_account")
                .queryParam("state", jwtService.generateStateToken(email, STATE_PURPOSE))
                .build().encode().toUriString();
    }

    /** Step 2: Microsoft sends the browser back with ?code=...&state=... . Never throws. */
    public String handleCallback(String code, String state, String error) {
        if (error != null) {
            return failure("access_denied");
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
            if (tokens.scope() == null || !tokens.scope().toLowerCase().contains("mail.read")) {
                return failure("scope_missing");
            }
            save(user, tokens);
            return frontendUrl + "/dashboard?mail=connected&provider=outlook";
        } catch (RestClientException e) {
            log.warn("Microsoft token exchange failed: {}", e.getClass().getSimpleName());
            return failure("token_exchange_failed");
        } catch (Exception e) {
            log.error("Outlook connection failed", e);
            return failure("server_error");
        }
    }

    private TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");
        form.add("scope", SCOPE);
        return http.post().uri(String.format(TOKEN_URL, props.tenantOrCommon()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    private void save(User user, TokenResponse tokens) {
        GmailConnection connection = connectionRepository.findByUserId(user.getId())
                .orElseGet(GmailConnection::new);
        connection.setUser(user);
        connection.useProvider(ProviderType.OUTLOOK);
        connection.setAccessTokenEncrypted(encryptor.encrypt(tokens.accessToken()));
        connection.setRefreshTokenEncrypted(encryptor.encrypt(tokens.refreshToken()));
        connectionRepository.save(connection);
    }

    private String failure(String reason) {
        return frontendUrl + "/dashboard?mail=error&reason=" + reason;
    }
}
