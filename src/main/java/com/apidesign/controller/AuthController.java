package com.apidesign.controller;

import com.apidesign.dto.UserDTO;
import com.apidesign.dto.auth.LoginRequest;
import com.apidesign.dto.auth.RefreshTokenRequest;
import com.apidesign.dto.auth.RegisterRequest;
import com.apidesign.dto.auth.TokenRequest;
import com.apidesign.dto.auth.TokenResponse;
import com.apidesign.response.ApiResponse;
import com.apidesign.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@Valid @RequestBody RegisterRequest req) {
        UserDTO created = authService.register(req);
        return new ResponseEntity<>(
            ApiResponse.created(created, "User registered"), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        TokenResponse token = authService.login(req);
        return ResponseEntity.ok(ApiResponse.success(token, "Authenticated"));
    }

    /**
     * Explicit "getJWT" endpoint. No {@code @Valid} — body shape is checked manually so
     * the contract is independent of {@code app.validation.enabled}. Returns a bearer
     * token usable for protected endpoints (when JWT enforcement is on) or simply as a
     * proof-of-issuance (when enforcement is off).
     */
    @PostMapping("/token")
    public TokenResponse getJwt(@RequestBody TokenRequest body) {
        return authService.getJwt(body);
    }

    /**
     * Rotates a refresh token. No {@code @Valid}: every failure mode (missing/expired/
     * reused) must surface as 401, never 400. Returns a new bearer access token + a
     * new refresh token; the presented refresh token is revoked.
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshTokenRequest body) {
        return authService.refresh(body == null ? null : body.refreshToken());
    }
}
