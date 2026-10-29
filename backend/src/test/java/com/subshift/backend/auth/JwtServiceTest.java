package com.subshift.backend.auth;

import com.subshift.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests: no Spring context, no database. */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test-secret";

    private JwtService service(long expirationMs) {
        return new JwtService(new JwtProperties(SECRET, expirationMs));
    }

    @Test
    void tokenCarriesEmailAndRole() {
        JwtService jwt = service(60_000);
        Claims claims = jwt.parse(jwt.generateToken("a@b.com", "ADMIN"));
        assertEquals("a@b.com", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    @Test
    void secretShorterThan32CharactersIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("too-short", 60_000)));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(-1_000);
        String token = jwt.generateToken("a@b.com", "USER");
        assertThrows(JwtException.class, () -> jwt.parse(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(60_000);
        String token = jwt.generateToken("a@b.com", "USER");
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertThrows(JwtException.class, () -> jwt.parse(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtService(new JwtProperties("another-secret-another-secret-123456", 60_000))
                .generateToken("a@b.com", "USER");
        assertThrows(JwtException.class, () -> service(60_000).parse(token));
    }

    @Test
    void stateTokenWorksOnlyForItsOwnPurposeAndIsNotALoginToken() {
        JwtService jwt = service(60_000);
        String state = jwt.generateStateToken("a@b.com", "gmail-connect");
        assertEquals("a@b.com", jwt.parseStateToken(state, "gmail-connect"));
        assertThrows(JwtException.class, () -> jwt.parseStateToken(state, "outlook-connect"));
        assertTrue(jwt.parse(state).get("role", String.class) == null, "a state token must carry no role");
        assertThrows(JwtException.class, () -> jwt.parseStateToken(jwt.generateToken("a@b.com", "USER"), "gmail-connect"));
    }
}
