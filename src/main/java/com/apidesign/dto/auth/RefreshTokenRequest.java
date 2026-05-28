package com.apidesign.dto.auth;

/**
 * Body for POST /auth/refresh. Deliberately no {@code @NotBlank} — the controller skips
 * {@code @Valid} so authentication failures present as 401, not 400.
 */
public record RefreshTokenRequest(String refreshToken) {}
