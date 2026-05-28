package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.UserDTO;
import com.apidesign.dto.auth.LoginRequest;
import com.apidesign.dto.auth.RegisterRequest;
import com.apidesign.dto.auth.TokenRequest;
import com.apidesign.dto.auth.TokenResponse;
import com.apidesign.entity.RefreshToken;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.mapper.UserMapper;
import com.apidesign.repository.RefreshTokenRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.security.JwtService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(
        UserRepository userRepository,
        UserMapper userMapper,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public UserDTO register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessLogicException(
                "User with email " + req.email() + " already exists",
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS);
        }

        Set<String> roles = new HashSet<>();
        roles.add("USER");

        User user = User.builder()
            .firstName(req.firstName())
            .lastName(req.lastName())
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .phoneNumber(req.phoneNumber())
            .isActive(true)
            .userType("CUSTOMER")
            .roles(roles)
            .build();

        User saved = userRepository.save(user);
        log.info("Registered user id={} email={}", saved.getId(), saved.getEmail());
        return userMapper.toDTO(saved);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("Account disabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return issueTokens(user, UUID.randomUUID().toString());
    }

    /**
     * Issues a JWT for the supplied credentials. Distinct from {@link #login(LoginRequest)}
     * in that it does NOT depend on bean validation — the controller takes a body without
     * {@code @Valid} and we surface missing fields as 401 {@code BusinessLogicException}
     * rather than 400 {@code MethodArgumentNotValidException}. This is the explicit "getJWT"
     * path required by the API spec and works regardless of {@code app.security.jwt.enabled}.
     */
    @Transactional
    public TokenResponse getJwt(TokenRequest req) {
        if (req == null
            || req.username() == null
            || req.username().isBlank()
            || req.password() == null
            || req.password().isBlank()) {
            throw new BusinessLogicException(
                "Invalid credentials",
                ErrorCodes.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED.value());
        }

        User user = userRepository.findByEmailIgnoreCase(req.username())
            .orElseThrow(() -> new BusinessLogicException(
                "Invalid credentials",
                ErrorCodes.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED.value()));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessLogicException(
                "Invalid credentials",
                ErrorCodes.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED.value());
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessLogicException(
                "Invalid credentials",
                ErrorCodes.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED.value());
        }
        return issueTokens(user, UUID.randomUUID().toString());
    }

    /**
     * Rotate a refresh token. The classic mitigation: revoke the presented token, mint a
     * new pair on the same family, link old→new via {@code replacedByHash}. If the caller
     * presents an already-revoked token (i.e. a stolen leftover) we kill the whole family
     * and reject — the legitimate user will be forced to re-login.
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorized();
        }
        String hash = jwtService.hashToken(rawRefreshToken);
        Optional<RefreshToken> existing = refreshTokenRepository.findByTokenHash(hash);
        if (existing.isEmpty()) {
            throw unauthorized();
        }
        RefreshToken token = existing.get();

        if (token.isRevoked()) {
            // Reuse-after-revoke is the canonical theft signal: kill the family entirely.
            log.warn("Refresh-token reuse detected; revoking family {}", token.getFamilyId());
            refreshTokenRepository.markFamilyRevoked(token.getFamilyId());
            throw unauthorized();
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw unauthorized();
        }

        User user = userRepository.findById(token.getUserId()).orElseThrow(this::unauthorized);
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw unauthorized();
        }

        // Mint the replacement first so we can link back from the old row.
        String newRaw = jwtService.generateRefreshToken();
        String newHash = jwtService.hashToken(newRaw);

        token.setRevoked(true);
        token.setReplacedByHash(newHash);
        refreshTokenRepository.save(token);

        RefreshToken next = RefreshToken.builder()
            .tokenHash(newHash)
            .userId(user.getId())
            .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirySeconds()))
            .familyId(token.getFamilyId())
            .revoked(false)
            .build();
        refreshTokenRepository.save(next);

        String access = jwtService.issue(user.getId(), user.getEmail(), user.getRoles());
        return TokenResponse.bearer(access, newRaw, jwtService.getExpirySeconds());
    }

    /** Mints a new access + refresh pair and persists the refresh hash. */
    private TokenResponse issueTokens(User user, String familyId) {
        String access = jwtService.issue(user.getId(), user.getEmail(), user.getRoles());
        String rawRefresh = jwtService.generateRefreshToken();
        String hash = jwtService.hashToken(rawRefresh);

        RefreshToken row = RefreshToken.builder()
            .tokenHash(hash)
            .userId(user.getId())
            .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirySeconds()))
            .familyId(familyId)
            .revoked(false)
            .build();
        refreshTokenRepository.save(row);

        return TokenResponse.bearer(access, rawRefresh, jwtService.getExpirySeconds());
    }

    private BusinessLogicException unauthorized() {
        return new BusinessLogicException(
            "Invalid refresh token",
            ErrorCodes.AUTH_TOKEN_INVALID,
            HttpStatus.UNAUTHORIZED.value());
    }
}
