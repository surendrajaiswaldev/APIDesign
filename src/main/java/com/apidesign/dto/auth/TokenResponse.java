package com.apidesign.dto.auth;

/**
 * Issued by /auth/login, /auth/token, and /auth/refresh. The refresh token is included
 * on the same payload — the client is expected to hold both and call /auth/refresh with
 * the refresh token before {@code expiresIn} (access) elapses.
 */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }

    /** Back-compat overload — used where a refresh token is not yet wired. */
    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, null, "Bearer", expiresInSeconds);
    }
}
