package com.apidesign.security;

import com.apidesign.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 JWTs.
 *
 * Token payload:
 * <ul>
 *   <li>{@code sub}: user id (numeric, as string)</li>
 *   <li>{@code email}: user email</li>
 *   <li>{@code roles}: list of role names (no {@code ROLE_} prefix)</li>
 *   <li>{@code iat} / {@code exp}: standard timestamps</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32; // 256-bit token

    private final SecretKey signingKey;
    private final long expirySeconds;
    private final long refreshExpirySeconds;

    public JwtService(SecurityProperties props) {
        byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256 requires >= 256-bit key. Pad rather than fail hard so local dev still boots.
            byte[] padded = new byte[32];
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            secretBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirySeconds = props.jwt().expiryMinutes() * 60L;
        this.refreshExpirySeconds = props.jwt().refreshExpiryDays() * 24L * 60L * 60L;
    }

    public String issue(Long userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirySeconds, ChronoUnit.SECONDS);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claims(Map.of("email", email, "roles", roles))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }

    public long getRefreshExpirySeconds() {
        return refreshExpirySeconds;
    }

    /**
     * Issues a fresh refresh token. 32 cryptographically-random bytes encoded URL-safe
     * (no padding) — the format is opaque to clients but reasonable to put in a URL or
     * cookie if needed.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hex of the raw refresh token. Stored server-side; the raw token is never
     * persisted so a database leak cannot impersonate users.
     */
    public String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JRE-mandated algorithm; this branch is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
