package com.apidesign.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.apidesign.dto.auth.LoginRequest;
import com.apidesign.dto.auth.RegisterRequest;
import com.apidesign.dto.auth.TokenResponse;
import com.apidesign.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * End-to-end auth flow against a real Oracle instance via Testcontainers.
 *
 * <p>Boots the full Spring application (random port), runs Flyway against a freshly
 * started Oracle Free container, and exercises register → login through the wire.
 *
 * <p>Requires Docker on the host. Reuse is enabled — set {@code testcontainers.reuse.enable=true}
 * in {@code ~/.testcontainers.properties} so the container survives between runs.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class UserIntegrationTest {

    @Container
    static OracleContainer oracle =
        new OracleContainer("gvenzl/oracle-free:23-slim-faststart").withReuse(true);

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.OracleDialect");
        // We want the real production-style path: Flyway runs, Hibernate validates.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Turn enforcement on so the tests exercise the production code path.
        registry.add("app.security.jwt.enabled", () -> "true");
        registry.add("app.validation.enabled", () -> "true");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registerThenLogin_shouldIssueTokens() throws Exception {
        RegisterRequest register = new RegisterRequest(
            "Ada",
            "Lovelace",
            "ada@example.com",
            "correct horse battery staple",
            "5551234567");
        ResponseEntity<String> registerResponse =
            restTemplate.postForEntity("/auth/register", register, String.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginRequest login =
            new LoginRequest("ada@example.com", "correct horse battery staple");
        ResponseEntity<String> loginResponse =
            restTemplate.postForEntity("/auth/login", login, String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ApiResponse<TokenResponse> envelope = objectMapper.readValue(
            loginResponse.getBody(), new TypeReference<ApiResponse<TokenResponse>>() {});
        assertThat(envelope.data()).isNotNull();
        assertThat(envelope.data().accessToken()).isNotBlank();
        assertThat(envelope.data().refreshToken()).isNotBlank();
        assertThat(envelope.data().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_withWrongPassword_returns401() {
        RegisterRequest register = new RegisterRequest(
            "Grace",
            "Hopper",
            "grace@example.com",
            "compilers-are-cool",
            "5559876543");
        restTemplate.postForEntity("/auth/register", register, String.class);

        LoginRequest login = new LoginRequest("grace@example.com", "wrong-password");
        ResponseEntity<String> response =
            restTemplate.postForEntity("/auth/login", login, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
