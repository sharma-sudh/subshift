package com.subshift.backend.auth;

import com.subshift.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private static final long STATE_TTL_MS = 10 * 60 * 1000L; // OAuth round-trips should take seconds
    private static final String PURPOSE_CLAIM = "purpose";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties props) {
        byte[] bytes = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = props.expirationMs();
    }

    /** Subject is the user's email; role is stored as a claim (e.g. "USER" or "ADMIN"). */
    public String generateToken(String email, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Verifies signature and expiry; throws a JwtException if the token is invalid. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Short-lived token for flows like the Gmail OAuth "state" parameter. It carries a purpose
     * claim and no role claim, so it can never be used as a login token (JwtAuthFilter needs
     * a role), and a login token can never be used as state (no purpose claim).
     */
    public String generateStateToken(String email, String purpose) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim(PURPOSE_CLAIM, purpose)
                .issuedAt(new Date(now))
                .expiration(new Date(now + STATE_TTL_MS))
                .signWith(key)
                .compact();
    }

    /** Returns the email inside a valid state token for the given purpose, else throws a JwtException. */
    public String parseStateToken(String token, String purpose) {
        Claims claims = parse(token);
        if (claims.getSubject() == null || !purpose.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new JwtException("Invalid state token");
        }
        return claims.getSubject();
    }
}
