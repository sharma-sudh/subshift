package com.subshift.backend.gmail;

import com.subshift.backend.gmail.GmailOAuthService.StatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
public class GmailOAuthController {

    private final GmailOAuthService gmailOAuthService;

    public record ConnectResponse(String authUrl) {}

    /**
     * Returns the Google consent URL as JSON instead of redirecting, because a browser redirect
     * can't carry the Authorization header. The frontend calls this with fetch(), then sets
     * window.location to authUrl.
     */
    @GetMapping("/connect")
    public ConnectResponse connect(Authentication authentication) {
        return new ConnectResponse(gmailOAuthService.buildAuthUrl(authentication.getName()));
    }

    /** Public endpoint (see SecurityConfig): Google redirects the browser here. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String location = gmailOAuthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/status")
    public StatusResponse status(Authentication authentication) {
        return gmailOAuthService.status(authentication.getName());
    }

    @DeleteMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication) {
        gmailOAuthService.disconnect(authentication.getName());
    }
}
