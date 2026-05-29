package com.apidesign.security;

import com.apidesign.config.SecurityProperties;
import com.apidesign.ratelimit.RateLimitFilter;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT auth. CSRF off (JSON API).
 *
 * Filter chain ordering: JwtAuthenticationFilter populates the SecurityContext, then
 * RateLimitFilter runs after so it can key buckets by authenticated user when present.
 *
 * Method security ({@code @PreAuthorize}, etc.) is configured separately in
 * {@code MethodSecurityConfig} so it can be conditionalised by the same flag.
 *
 * Flag {@code app.security.jwt.enabled} (default {@code true}):
 * <ul>
 *   <li>{@code true} — current behavior: JWT filter on the chain, every non-whitelisted
 *       endpoint requires authentication.</li>
 *   <li>{@code false} — JWT filter not added, all endpoints {@code permitAll()}. Rate
 *       limit + CORS still apply. The {@code /auth/token} endpoint is callable regardless.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final boolean jwtEnabled;

    public SecurityConfig(@Value("${app.security.jwt.enabled:true}") boolean jwtEnabled) {
        this.jwtEnabled = jwtEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
        throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthFilter,
        RateLimitFilter rateLimitFilter,
        // Disambiguate: in Spring Boot 3.x, `mvcHandlerMappingIntrospector` also implements
        // CorsConfigurationSource. Without this @Qualifier, autowire finds two beans and
        // bails with "expected single matching bean but found 2".
        @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
        throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (jwtEnabled) {
            log.info("JWT authentication ENABLED (app.security.jwt.enabled=true)");
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                        "/auth/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/api-docs/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/rate-limit/status")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        } else {
            log.warn(
                "JWT authentication DISABLED (app.security.jwt.enabled=false). "
                    + "All endpoints are public. Do NOT use this in production.");
            http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(splitCsv(props.cors().allowedOrigins()));
        config.setAllowedMethods(splitCsv(props.cors().allowedMethods()));
        config.setAllowedHeaders(splitCsv(props.cors().allowedHeaders()));
        config.setExposedHeaders(
            List.of("X-Correlation-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining",
                "X-RateLimit-Reset", "Retry-After"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of("*");
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
