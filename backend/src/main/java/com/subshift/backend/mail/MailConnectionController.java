package com.subshift.backend.mail;

import com.subshift.backend.mail.MailConnectionService.StatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Mailbox connections for every provider. (Gmail's own OAuth endpoints stay under /api/gmail.) */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class MailConnectionController {

    private final MailConnectionService connectionService;
    private final OutlookOAuthService outlookOAuthService;

    public record ConnectResponse(String authUrl) {}

    public record YahooConnectRequest(@NotBlank String email, @NotBlank String appPassword) {}

    @GetMapping("/status")
    public StatusResponse status(Authentication authentication) {
        return connectionService.status(authentication.getName());
    }

    @DeleteMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication) {
        connectionService.disconnect(authentication.getName());
    }

    /** Returns the Microsoft consent URL as JSON (a browser redirect can't carry the Authorization header). */
    @GetMapping("/outlook/connect")
    public ConnectResponse connectOutlook(Authentication authentication) {
        return new ConnectResponse(outlookOAuthService.buildAuthUrl(authentication.getName()));
    }

    /** Public endpoint (see SecurityConfig): Microsoft redirects the browser here. */
    @GetMapping("/outlook/callback")
    public ResponseEntity<Void> outlookCallback(@RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String error) {
        String location = outlookOAuthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @PostMapping("/yahoo/connect")
    public StatusResponse connectYahoo(@Valid @RequestBody YahooConnectRequest request,
                                       Authentication authentication) {
        return connectionService.connectYahoo(authentication.getName(), request.email(), request.appPassword());
    }
}
