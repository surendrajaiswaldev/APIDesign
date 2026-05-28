package com.apidesign.dto.auth;

/**
 * Credentials payload for the {@code POST /auth/token} ("getJWT") endpoint.
 *
 * Intentionally has <strong>no bean-validation annotations</strong> — null/blank checks are
 * performed manually in the controller. This contract is stable regardless of the
 * {@code app.validation.enabled} flag and the {@code app.security.jwt.enabled} flag (token
 * is always issuable; it just won't be required when JWT enforcement is off).
 */
public record TokenRequest(String username, String password) {}
