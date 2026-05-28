# Modernization pass

## Summary

This pass rebuilt the service around Spring Boot 3.3 + Java 21 conventions:
Maven becomes authoritative; Spring Security adds JWT auth; JPA gains proper auditing,
per-entity sequences, optimistic locking, and atomic stock updates; errors switch to
RFC 7807 ProblemDetail; DTOs become records; HATEOAS is removed; observability gains
actuator/Prometheus/OTel; and a Bucket4j + Caffeine rate limiter lands in the filter
chain. Local profile boots with zero env vars; dev/prod fail-fast on missing secrets.

## Changes

### Security
| Change | File | Rationale |
|---|---|---|
| Added Spring Security + OAuth2 resource server starters | `pom.xml` | Modern Spring stack for JWT |
| Added jjwt 0.12.6 (api/impl/jackson) | `pom.xml` | HS256 token issue/parse |
| `SecurityConfig`: stateless, CSRF off, CORS, BCrypt strength 12, filter ordering | `security/SecurityConfig.java` | Modern industry-standard JWT setup |
| `SecurityProperties` `app.security.jwt.{secret,expiryMinutes}` + CORS knobs | `config/SecurityProperties.java` | Externalised config |
| `JwtService` to issue/parse tokens (sub=userId, claims=email+roles) | `security/JwtService.java` | Decoupled JWT logic |
| `JwtAuthenticationFilter` (OncePerRequestFilter) populates SecurityContext from Bearer | `security/JwtAuthenticationFilter.java` | Stateless auth on every request |
| `AuthController` `/auth/register` + `/auth/login` | `controller/AuthController.java` | Onboarding + token issue |
| `AuthService` handles registration (bcrypt) + credential check | `service/AuthService.java` | Service layer separation |
| `User.passwordHash` (BCrypt) + `User.roles Set<String>` (eager `@ElementCollection`) | `entity/User.java` | Auth fields + RBAC |
| `@PreAuthorize("hasRole('ADMIN')")` on destructive endpoints | controllers | Role-based access |
| `AuditorAware<String>` from SecurityContext (fallback "system") | `config/DatabaseConfig.java` | Drives `@CreatedBy`/`@LastModifiedBy` |
| Auth/Swagger/health whitelist; everything else authenticated | `security/SecurityConfig.java` | Explicit access policy |

### JPA
| Change | File | Rationale |
|---|---|---|
| `BaseEntity` uses Spring Data `@CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy` + `AuditingEntityListener` | `entity/BaseEntity.java` | Standard Spring Data auditing |
| Custom `AuditingEntityListener.java` deleted | (removed) | Superseded by Spring Data's listener |
| `@Id` moved out of BaseEntity; per-entity sequences `USER_SEQ`/`PRODUCT_SEQ`/`ORDER_SEQ`/`ORDER_ITEM_SEQ` (allocationSize=50) | `entity/*.java` | Avoid contention on one shared sequence |
| `@Version` added on `Product` and `Order` | `entity/Product.java`, `entity/Order.java` | Optimistic locking |
| `OrderStatus` converted from String-constants class to proper enum | `constants/OrderStatus.java` | Type safety, Jackson + JPA enum-string support |
| `Order.orderStatus` annotated `@Enumerated(EnumType.STRING)` | `entity/Order.java` | Persists enum name |
| `Order.orderItems` uses `CascadeType.ALL` + `orphanRemoval = true` + bidirectional `addOrderItem`/`removeOrderItem` | `entity/Order.java` | Cleaner aggregate semantics |
| Removed `@PrePersist`/`@PreUpdate` from `User`, `Order`, `Product`, `OrderItem`, `BaseEntity` | entities | Spring Data auditing handles it |
| Repositories use Java text blocks for JPQL/native SQL | `repository/*.java` | Readability |
| `OrderRepository` parameters typed `OrderStatus` instead of `String` | `repository/OrderRepository.java` | Type safety |

### Concurrency
| Change | File | Rationale |
|---|---|---|
| `ProductRepository.decrementStock(id, qty)` + `restoreStock(id, qty)` atomic UPDATE...WHERE | `repository/ProductRepository.java` | Race-free stock writes |
| `OrderService.createOrder` uses `decrementStock`; throws `BusinessLogicException("Insufficient stock")` if rows-affected == 0 | `service/OrderService.java` | Lost-update prevention |
| `createOrder` wrapped in 3-attempt retry on `DataIntegrityViolationException` | `service/OrderService.java` | Tolerate rare order-number collisions |
| `OrderService.cancelOrder` calls `restoreStock` instead of read-modify-write | `service/OrderService.java` | Same |
| New order number: `"ORD-" + System.currentTimeMillis() + "-" + SecureRandom hex(8)` | `service/OrderService.java` | Wider keyspace than the prior date+UUID-prefix |

### Validation
| Change | File | Rationale |
|---|---|---|
| `CreateProductRequest.price`: `@NotNull` + `@PositiveBigDecimal` (was `@NotBlank` — invalid on numeric type) | `dto/CreateProductRequest.java` | Correct constraint |
| `CreateProductRequest.stockQuantity`: `@NotNull` + `@PositiveOrZero` | same | Allow 0 stock |
| `OrderController.updateOrderStatus` accepts `@Valid` body + `UpdateStatusRequest.newStatus` typed as `OrderStatus` (enum) | `controller/OrderController.java` | Jackson 400s on invalid enum |
| `ProductController.searchProducts` params all `required = false` | `controller/ProductController.java` | Search is meant to be optional-filter |
| Cross-field check: `minPrice <= maxPrice` enforced in `ProductService.searchProductsByPriceRange`; 400 with `INVALID_PRICE_RANGE` | `service/ProductService.java` | Per brief |
| `UpdateUserRequest.isActive` removed | `dto/UpdateUserRequest.java` | Dedicated `/deactivate` endpoint handles it |

### Errors
| Change | File | Rationale |
|---|---|---|
| `BaseException` sealed; subclasses `final` | `exception/*.java` | Closed hierarchy, pattern-matching friendly |
| `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`; emits RFC 7807 `ProblemDetail` for all errors | `advice/GlobalExceptionHandler.java` | Standardised |
| Handlers: NotFound/Business/Validation/Database/MethodArgumentNotValid/ConstraintViolation/HttpMessageNotReadable/TypeMismatch/MissingParam/OptimisticLock/DataIntegrity/AccessDenied/Authentication/IllegalArgument/Exception | same | Full coverage per brief |
| Field errors attached as `fieldErrors` extension on the ProblemDetail | same | Machine-readable form errors |
| `errorCode` + `correlationId` extensions on every error | same | Diagnostics |
| Success responses keep `ApiResponse<T>` envelope | `response/ApiResponse.java` | Per brief, distinction documented in README |

### Modernization
| Change | File | Rationale |
|---|---|---|
| All DTOs (`UserDTO`, `ProductDTO`, `OrderDTO`, `OrderItemDTO`, `CreateUserRequest`, `CreateProductRequest`, `CreateOrderRequest`, `UpdateUserRequest`, `UpdateProductRequest`, `PagedResponse`, `ValidationErrorResponse`, `ApiResponse`) became Java 21 records | `dto/**`, `response/**` | Records eliminate Lombok ceremony |
| New `dto/auth/{RegisterRequest,LoginRequest,TokenResponse}` records | `dto/auth/**` | Auth contracts |
| MapStruct mappers updated for records (explicit `@Mapping(target=..., ignore=true)` for fields not on request DTO) | `mapper/*.java` | Required when target has fields source doesn't |
| `spring.threads.virtual.enabled: true` | `application.yml` | Loom virtual threads for servlet container |
| All JPQL/native SQL switched to text blocks | `repository/*.java` | Readability |
| Pattern-style switch in `OrderStatus.isValidTransition` | `constants/OrderStatus.java` | Modern switch expression |
| HATEOAS removed entirely (`spring-hateoas` dropped from pom; `EntityModel`/`CollectionModel`/`WebMvcLinkBuilder` removed from all controllers) | `pom.xml`, controllers | Was half-applied (nested under `ApiResponse.data._links`), non-HAL-compliant, and no client consumed it |

### Observability
| Change | File | Rationale |
|---|---|---|
| `spring-boot-starter-actuator` + `micrometer-registry-prometheus` + `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` | `pom.xml` | Modern metrics + tracing |
| Exposed `health,info,metrics,prometheus,loggers`; liveness+readiness probes | `application.yml` | Standard SRE surface |
| `CorrelationIdFilter extends OncePerRequestFilter` with `@Order(HIGHEST_PRECEDENCE)`: reads/generates X-Correlation-Id, sets MDC, writes response header pre-handler so it lands even on exception, clears MDC in finally | `observability/CorrelationIdFilter.java` | Replaces the old MVC interceptor (which couldn't write headers on error paths) |
| Old `RequestInterceptor` + `WebConfig` deleted | (removed) | Superseded |
| Log pattern includes `[%X{correlationId:-}] [%X{traceId:-}/%X{spanId:-}]` | `application.yml` | Cross-tool correlation |
| `org.hibernate.type.descriptor.sql.BasicBinder: TRACE` moved out of base `application.yml`; only set in `application-local.yml` | `application*.yml` | Don't leak bind values in non-local logs |

### Rate limiting
| Change | File | Rationale |
|---|---|---|
| Bucket4j (`bucket4j_jdk11-core`, `bucket4j_jdk11-caffeine`) + Caffeine dependencies | `pom.xml` | Token-bucket lib + bounded cache |
| `RateLimitProperties` bound under `app.rate-limit` | `ratelimit/RateLimitProperties.java` | Externalised config |
| `RateLimitFilter` (OncePerRequestFilter) keyed `"user:"+name` / `"ip:"+addr`, skips auth/health/swagger, 429 + headers + ProblemDetail body | `ratelimit/RateLimitFilter.java` | Per brief |
| Registered after `JwtAuthenticationFilter` so authenticated keys are available | `security/SecurityConfig.java` | Per brief |
| Defaults block under `app.rate-limit` in `application.yml` | `application.yml` | Sensible out-of-the-box |

### Build
| Change | File | Rationale |
|---|---|---|
| Maven is authoritative; Gradle files removed | (removed) | One build tool |
| `spring-boot-maven-plugin` configured to activate `local` profile on `mvn spring-boot:run` | `pom.xml` | Zero-env-var local boot |
| `spotless-maven-plugin` with Google Java Format | `pom.xml` | Format on demand |
| `jacoco-maven-plugin` with prepare-agent + report (no enforcement) | `pom.xml` | Coverage reports |
| Lombok bumped to 1.18.38; MapStruct to 1.6.0 | `pom.xml` | JDK 21 compatibility (1.18.30 + 1.5.5 throw `TypeTag :: UNKNOWN` on JDK 21.0.8) |
| `lombok.config` cleaned (Lombok 1.18.x deprecated some keys / lists must use `+=`) | `lombok.config` | Stop warnings |
| `pom.xml` properties: removed `hateoas.version` | `pom.xml` | Dep removed |

### Config
| Change | File | Rationale |
|---|---|---|
| `application.yaml` (duplicate) deleted | (removed) | Single base file |
| `spring.profiles.active` removed from base | `application.yml` | Profile must be chosen at runtime; defaulted by Maven plugin for local |
| `application-local.yml` keeps `DB_PASSWORD:password` default, owns Hibernate BasicBinder TRACE | same | Per user constraint |
| `application-dev.yml` / `application-prod.yml`: env-var-only, no defaults | same | Fail-fast in higher envs |
| New `.env.example` documenting required vars | `.env.example` | Onboarding |
| `.gitignore` updated to ignore `.env`, `application-local-secrets.yml`, logs, target | `.gitignore` | Secret hygiene |

### Docs
| Change | File | Rationale |
|---|---|---|
| README full rewrite | `README.md` | Reflects the new app |
| `CLAUDE_ENHANCEMENTS.md` (this file) added | `CLAUDE_ENHANCEMENTS.md` | Audit trail of this pass |
| `API_DOCUMENTATION.md` + `VALIDATION_REPORT.md` deleted; content folded into README + this file | (removed) | Less doc sprawl |
| `LombokConfigurationGuide.java` deleted | (removed) | Was a documentation-only Java class |

## Breaking changes

- **Error response shape**: errors are now bare `application/problem+json` (RFC 7807),
  not the legacy `ApiResponse` envelope. Clients keying off `status`/`message` in error
  bodies must read `status` and `detail` (and `errorCode` extension) instead.
- **`OrderStatus` is an enum**, not a string. JSON serialization still uses the
  uppercase name (`"PENDING"`, etc.), but request bodies that previously sent unknown
  strings will now return 400.
- **HATEOAS removed**: responses no longer contain a `_links` object. Clients that
  parsed `EntityModel.links[]` must drop that code (the API was never HAL-compliant).
- **DTOs are records**: serialization is unchanged, but Java consumers using DTO
  setters/builders must switch to canonical constructors. Getter form changes from
  `dto.getEmail()` to `dto.email()`.
- **User entity gains `passwordHash` (NOT NULL) and `roles`**: existing rows without
  these columns will fail to validate against the schema. Run a migration.
- **`UpdateUserRequest` no longer accepts `isActive`**: clients toggling activation
  must call `POST /users/{id}/deactivate` instead.
- **Authentication is now required** for almost every endpoint. Public surface is
  limited to `/auth/**`, Swagger UI, health, and info.
- **`ID_SEQ` removed**; replaced by per-entity sequences. If existing data uses
  `ID_SEQ`, migrate or alias before going live.
- **Bucket4j coordinates**: the brief specified `bucket4j_jdk17-*:8.10.1`, but Maven
  Central does not publish those coordinates. Closest match (`bucket4j_jdk11-*:8.14.0`)
  used. Documented inline in `pom.xml`.

## Migration notes

For an operator with an existing deployment:

1. **Database schema changes**:
   - `users`: add `PASSWORD_HASH VARCHAR2(100) NOT NULL`. Backfill via temporary bcrypt
     hash and force password reset, or drop existing users.
   - New table `USER_ROLES (USER_ID NUMBER, ROLE VARCHAR2(30))` with FK to USERS.
   - `products`: add `VERSION NUMBER`.
   - `orders`: add `VERSION NUMBER`.
   - Replace the shared `ID_SEQ` with `USER_SEQ`, `PRODUCT_SEQ`, `ORDER_SEQ`,
     `ORDER_ITEM_SEQ` (all `INCREMENT BY 50`).
2. **Env vars**: set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and
   `SPRING_PROFILES_ACTIVE` for any non-local deployment. See `.env.example`.
3. **JWT secret**: generate a real one (`openssl rand -base64 48`). The committed
   placeholder is only safe for local.
4. **First admin user**: register via `/auth/register`, then update that row's
   `USER_ROLES` to include `ADMIN`.
5. **Clients**: update error parsing to ProblemDetail; remove HATEOAS link parsing;
   adapt to enum responses for `OrderStatus`.
6. **Observability**: point Prometheus at `/api/v1/actuator/prometheus`; set up an
   OTLP collector or unset `OTEL_EXPORTER_OTLP_ENDPOINT` (will silently no-op without
   a receiver).

## Removed files

- `build.gradle` — Maven is now authoritative.
- `settings.gradle` — same.
- `gradlew`, `gradlew.bat` — Gradle wrapper unused.
- `gradle/` directory — Gradle wrapper resources.
- `src/main/resources/application.yaml` — duplicate of `application.yml`.
- `src/main/java/com/apidesign/config/LombokConfigurationGuide.java` — documentation
  Java class; content folded into `lombok.config` and this file.
- `src/main/java/com/apidesign/config/AuditingEntityListener.java` — replaced by
  Spring Data JPA's built-in `AuditingEntityListener`.
- `src/main/java/com/apidesign/config/WebConfig.java` — only purpose was registering
  the old `RequestInterceptor`.
- `src/main/java/com/apidesign/interceptor/RequestInterceptor.java` — superseded by
  `CorrelationIdFilter` (which can set response headers before the handler runs).
- `API_DOCUMENTATION.md` — folded into README.
- `VALIDATION_REPORT.md` — content rendered obsolete by this pass.
- `logs/` directory — runtime artifacts shouldn't be checked in.

# Second pass

This pass adds four flag-controlled enhancements and one explicit endpoint on top of the
first pass: a persistent exception audit pipeline, a JWT enforcement toggle, an explicit
"getJWT" endpoint, and a global validation toggle. Local profile flips two of the new
flags (JWT, validation) to off for ergonomics; dev/prod retain the safe defaults.

## Files added

| File | Purpose |
|---|---|
| `entity/ApplicationException.java` | JPA entity for the new `APPLICATION_EXCEPTION` table; extends `BaseEntity`; per-entity `APP_EXCEPTION_SEQ` (allocationSize=50); CLOB columns truncated at the service layer (4096/2048/8192). |
| `repository/ApplicationExceptionRepository.java` | `JpaRepository`; derived finder `findByCorrelationIdOrderByCreatedAtDesc(String, Pageable)` for diagnostics. |
| `service/ExceptionAuditService.java` | Records exceptions in `REQUIRES_NEW` (`noRollbackFor = Throwable`). Wraps the entire body in try/catch — persistence failure logs ERROR but never throws. Pulls `userId` from SecurityContext, `correlationId` from MDC, `clientIp` honoring `X-Forwarded-For`, body from `ContentCachingRequestWrapper` (or `WebUtils.getNativeRequest(...)` if Spring wrapped it). Sets `exception.recorded` request attribute for dedup. Flag: `app.audit.exceptions.enabled` (default `true`). |
| `observability/RequestCachingFilter.java` | `OncePerRequestFilter` ordered `HIGHEST_PRECEDENCE + 10` (just after `CorrelationIdFilter`); wraps JSON or form-encoded requests in `ContentCachingRequestWrapper` so the audit pipeline can re-read the body. |
| `interceptor/RequestInterceptor.java` | Plain Spring `HandlerInterceptor`. `preHandle` stamps `interceptor.startNanos`, logs `REQ -> METHOD URI`. `afterCompletion` logs `REQ METHOD URI -> STATUS in Nms` and calls the audit service when `ex != null` or `status >= 500`. Skips when `exception.recorded` is already set (handler-side recording wins). |
| `config/WebConfig.java` | `WebMvcConfigurer`; registers the interceptor with `excludePathPatterns("/actuator/**", "/swagger-ui/**", "/api-docs/**", "/error")`. |
| `security/MethodSecurityConfig.java` | Split out of `SecurityConfig` so `@EnableMethodSecurity` can be conditionalised. Gated by `@ConditionalOnProperty(prefix="app.security.jwt", name="enabled", havingValue="true", matchIfMissing=true)` — method-level `@PreAuthorize` activates only when JWT is on. |
| `dto/auth/TokenRequest.java` | Record `(String username, String password)`. No validation annotations whatsoever — null/blank checks are done manually in `AuthService.getJwt(...)`. |

## Files modified

| File | One-line reason |
|---|---|
| `security/SecurityConfig.java` | Constructor takes `@Value("${app.security.jwt.enabled:true}")`. Branches `filterChain`: enabled = current behavior (JWT filter, anyRequest authenticated); disabled = `anyRequest().permitAll()`, JWT filter not added, rate limiter still wired. `@EnableMethodSecurity` removed (moved to `MethodSecurityConfig`). |
| `config/SecurityProperties.java` | Added `enabled` to `Jwt` record (compact constructor defaults null → `true`) so YAML binding accepts the new key without strict-property rejection. |
| `advice/GlobalExceptionHandler.java` | Constructor-injects `ExceptionAuditService`. Every typed `@ExceptionHandler` now accepts an extra `HttpServletRequest` and calls `audit(req, status, ex)` before returning ProblemDetail. Overridden `handleMethodArgumentNotValid`, `handleHttpMessageNotReadable`, `handleMissingServletRequestParameter` use a `httpRequestOf(WebRequest)` helper because Spring doesn't pass the servlet request to the overridden signatures. The audit service sets `exception.recorded` to dedup with the interceptor's `afterCompletion`. |
| `service/AuthService.java` | Added `getJwt(TokenRequest)` — manual null/blank checks; bcrypt verify; throws `BusinessLogicException` with status 401 / `AUTH-001` instead of `BadCredentialsException` so the response shape matches the explicit "no auth dependency" contract. |
| `controller/AuthController.java` | Added `POST /auth/token` mapping returning bare `TokenResponse` (no `ApiResponse` envelope) and no `@Valid`. |
| `config/ValidationConfig.java` | Replaces the documentation-only stub. Publishes a `@Primary` `LocalValidatorFactoryBean` — either the standard one or a `NoOpValidatorFactoryBean` subclass overriding `afterPropertiesSet`, `supports`, both `validate(Object, Errors[, hints...])`, and the three `validate*` jakarta methods to return `Set.of()`. Also publishes a `@Primary` `MethodValidationPostProcessor` — either the standard one or a `NoOpMethodValidationPostProcessor` that no-ops `afterPropertiesSet` and both `postProcessBefore/AfterInitialization` so method-level `@Validated` proxies are never wrapped. Flag: `app.validation.enabled`. |
| `resources/application.yml` | Added `app.security.jwt.enabled: true`, `app.validation.enabled: true`, `app.audit.exceptions.enabled: true`. |
| `resources/application-local.yml` | Set `app.security.jwt.enabled: false` and `app.validation.enabled: false`; explicit `app.audit.exceptions.enabled: true`. |
| `resources/application-dev.yml` | Explicit `app.security.jwt.enabled: true` + `app.validation.enabled: true`. |
| `resources/application-prod.yml` | Same as dev. |
| `README.md` | New "Feature flags" table, "Exception logging" section, `/auth/token` row in API reference, Quickstart and Authentication updated to call out local-profile disabled enforcement. |

## Rationale highlights

- **Two-source exception recording with dedup**: `GlobalExceptionHandler` covers 4xx
  (which never propagate past the advice and thus invisible to the interceptor); the
  interceptor covers the case where an exception is still bubbling at `afterCompletion`
  *and* the 5xx-status-without-exception case. The `exception.recorded` request attribute
  prevents both layers from inserting two rows for the same failure.
- **`REQUIRES_NEW` + `noRollbackFor` + try/catch**: audit writes must commit even when the
  outer transaction rolls back, must not be marked for rollback by upstream markers, and
  must never throw — three orthogonal safeguards.
- **Body capture via `ContentCachingRequestWrapper`**: the servlet input stream is
  one-shot. The dedicated filter wraps before Spring's `HttpMessageConverter` reads, so by
  the time the audit service runs the bytes are already buffered. Limited to JSON/form
  payloads to avoid buffering large binary uploads.
- **Split `MethodSecurityConfig`**: `@EnableMethodSecurity` registers a `BeanFactoryPostProcessor`
  that can't observe its own enclosing class's `@ConditionalOnProperty`. Hoisting it into
  a dedicated `@Configuration` lets the conditional gate the whole config, cleanly
  shutting off method security when JWT is off.
- **`NoOpValidatorFactoryBean`**: returning a true no-op subclass (rather than just
  skipping `LocalValidatorFactoryBean` registration) keeps `@Autowired Validator` injection
  points working — they just receive a validator that approves everything.
- **Method validation post-processor neutered**: `LocalValidatorFactoryBean`'s no-op alone
  doesn't stop `MethodValidationPostProcessor` from creating proxies around `@Validated`
  beans. Marking both `@Primary` ensures Spring Boot's auto-configuration doesn't
  re-introduce the enforcing version.

## Breaking-change notes

- **DB schema**: a new `APPLICATION_EXCEPTION` table appears. Hibernate `ddl-auto: update`
  (or `create-drop` for `local`) creates it automatically. Dev/prod run `validate` — they
  will fail to start until the migration is applied. Add the table and `APP_EXCEPTION_SEQ`
  sequence to your migration:
  ```sql
  CREATE SEQUENCE APP_EXCEPTION_SEQ INCREMENT BY 50;
  CREATE TABLE APPLICATION_EXCEPTION (
    ID NUMBER PRIMARY KEY,
    CORRELATION_ID VARCHAR2(64),
    HTTP_METHOD VARCHAR2(10),
    REQUEST_PATH VARCHAR2(512),
    QUERY_STRING VARCHAR2(2048),
    REQUEST_BODY CLOB,
    RESPONSE_STATUS NUMBER,
    EXCEPTION_CLASS VARCHAR2(256),
    EXCEPTION_MESSAGE CLOB,
    STACK_TRACE CLOB,
    USER_ID VARCHAR2(128),
    CLIENT_IP VARCHAR2(64),
    DURATION_MS NUMBER,
    CREATED_AT TIMESTAMP NOT NULL,
    UPDATED_AT TIMESTAMP,
    CREATED_BY VARCHAR2(100),
    UPDATED_BY VARCHAR2(100)
  );
  ```
- **Local profile behavioural change**: anyone running the `local` profile will find JWT
  enforcement and bean validation OFF by default. To restore the previous behavior locally,
  set `app.security.jwt.enabled=true` and `app.validation.enabled=true` (env vars or CLI
  `--app.security.jwt.enabled=true --app.validation.enabled=true`).
- **`/auth/token` response shape**: returns a bare `TokenResponse` (no `ApiResponse`
  envelope), unlike `/auth/login`. Clients hitting the new endpoint should unmarshal
  `accessToken` from the root object, not from `data.accessToken`.
- **Invalid `/auth/token` credentials → 401 with `BusinessLogicException`**: the existing
  `/auth/login` throws `BadCredentialsException` which the handler maps to 401 as well,
  but the new endpoint deliberately uses the domain exception so its status is independent
  of Spring Security being on the chain (works when `app.security.jwt.enabled=false`).
- **`SecurityProperties.Jwt` gained an `enabled` field**: code that constructed the record
  directly will need the new arg. No production code does this — only the binder.

# Third pass

This pass adds three observability features to the rate limiter: a self-inspection
endpoint, an admin bucket listing, and Micrometer counters/gauges. No new dependencies,
no schema changes — purely additive on top of the existing Bucket4j + Caffeine setup.

## Files added

| File | Purpose |
|---|---|
| `dto/ratelimit/RateLimitStatusResponse.java` | Record `(key, keyType, limit, available, resetInSeconds)` returned by `GET /rate-limit/status`. |
| `dto/ratelimit/BucketSnapshot.java` | Record `(key, available, capacity)` — one entry of the admin list. |
| `controller/RateLimitController.java` | `GET /rate-limit/status` (open) and `GET /admin/rate-limit/buckets` (`@PreAuthorize("hasRole('ADMIN')")`, paginated `PagedResponse<BucketSnapshot>`). |

## Files modified

| File | One-line reason |
|---|---|
| `ratelimit/RateLimitFilter.java` | Added `MeterRegistry` ctor arg; registers gauge `rate_limit.cache.size` + two counters `rate_limit.requests.total` / `rate_limit.rejections.total` on every filter invocation; added `inspect(req)` for the status endpoint (no-consume read of available tokens + reset estimate); added `listBuckets(Pageable)` that iterates `buckets.asMap()`, recomputes capacity from each key, sorts by `available ASC`, returns `Page<BucketSnapshot>`; appended `/rate-limit/status` and `/admin/rate-limit/**` to `SKIP_PATTERNS`. |
| `security/SecurityConfig.java` | Whitelisted `/rate-limit/status` under `permitAll()` so the self-inspection endpoint is reachable by anonymous callers when JWT enforcement is on. The admin endpoint stays under the default `authenticated()` rule and is further gated by `@PreAuthorize` (bypassed locally because `MethodSecurityConfig` is off when JWT is off). |
| `README.md` | Added "Observability" subsection under "Rate limiting" listing the two new endpoints, the three metrics + tags, and a curl example; added the two endpoint rows to the API reference table. |

## Rationale highlights

- **Capacity from key, not from `Bucket`**: Bucket4j 8.x doesn't surface bucket
  configuration cleanly on a live `Bucket` instance. Since `buildBucket(String)` is a
  pure function of the key (`user:`/`ip:` prefix + optional `|<pathPattern>` suffix
  determines the override), the inverse `capacityFromKey(String)` lets `listBuckets`
  recover the capacity without persisting it in a side map.
- **Counters not cached locally**: the brief permits either a `ConcurrentHashMap<TagSet, Counter>`
  cache or `Counter.builder(...).register(meterRegistry)` per call. Chose the latter —
  Micrometer interns meters by (name, tags) inside the registry, so the builder
  ultimately returns the same `Counter` instance for the same tag combination.
  Simpler code, same hot-path cost after the first call.
- **`/rate-limit/status` open vs `permitAll()`**: the brief says "open to any
  authenticated OR anonymous caller". In the JWT-enabled chain, `anyRequest().authenticated()`
  is the default, so anonymous callers are 401'd unless the path is explicitly listed
  under `permitAll()`. Added it to the same matcher block as `/actuator/health` etc.
- **Skip-list dedup**: the filter still records a `skipped` counter for these paths
  (with `key_type=user|ip`, `endpoint=default`) so dashboards see the filter ran but
  did not consume; ops can spot if status calls become a hotspot.
- **`resetInSeconds` derivation**: Bucket4j's refill is intervally — `limit` tokens
  every minute. The reset estimate is `(capacity - available) / limit * 60s`, clamped
  to 0 when the bucket is full. This is approximate (Bucket4j's actual refill is
  jump-style at interval boundaries, not continuous), but it's the right order of
  magnitude for client back-off logic and lines up with the same math the filter
  uses in its 429 `Retry-After` header.

## Breaking-change notes

- **`RateLimitFilter` constructor signature changed**: now takes `MeterRegistry` as a
  third arg. No production code wires this filter manually — Spring resolves the bean —
  but any unit test that `new`s the filter directly must supply a `MeterRegistry`
  (e.g. `new SimpleMeterRegistry()`).
- **New paths in the security whitelist**: `/rate-limit/status` is now `permitAll()`
  in the JWT-enabled chain. The endpoint returns only the caller's own bucket state
  (no PII beyond an email-or-IP they already know), so the relaxation is bounded.
- **`PagedResponse` field naming**: the admin endpoint returns the project's existing
  `PagedResponse` record, whose fields are `content/pageNumber/pageSize/totalElements/
  totalPages/isFirst/isLast/hasNext/hasPrevious`. This differs from the
  `{content,totalElements,page,size}` shape shown in the original brief — keeping the
  project's existing envelope was an explicit constraint, so consistency wins.

---

# Fourth pass — production-grade Spring features

## Summary

The fourth pass layered ten production-grade Spring features onto the existing CRUD +
auth + rate-limit core. The goal was breadth across the most common "what does a
Spring app look like in production?" surfaces — schema migrations, async, caching,
scheduling, refresh-token rotation, resilience, custom health, architecture rules,
WebSocket, Testcontainers — each implemented with the canonical Spring idiom rather
than an exotic alternative. Compiles clean under `mvn -q -DskipTests compile`.

## Changes by area

### Database migrations
| Change | File | Rationale |
|---|---|---|
| Added `flyway-core` + `flyway-database-oracle` | `pom.xml` | Spring Boot 3.3 ships with Flyway 10.x; the Oracle dialect lives in a separate module. |
| Switched all profiles to `ddl-auto: validate` | `application.yml`, `application-local.yml` | Flyway owns DDL; Hibernate just verifies entity mapping. |
| `spring.flyway.enabled: true`, `baseline-on-migrate: true` | `application.yml` | Lets existing databases adopt Flyway without re-running V1. |
| `V1__initial_schema.sql` | `src/main/resources/db/migration/` | USERS / PRODUCTS / ORDERS / ORDER_ITEMS / USER_ROLES + sequences + indexes + status check. Matches @Column annotations exactly. USERS double-quoted (Oracle reserved word). |
| `V2__application_exception.sql` | (same dir) | Audit table + APP_EXCEPTION_SEQ. |
| `V3__refresh_tokens.sql` | (same dir) | REFRESH_TOKENS + REFRESH_TOKEN_SEQ + indexes on tokenHash (unique), familyId, userId. |
| `V4__shedlock.sql` | (same dir) | SHEDLOCK table required by JdbcTemplateLockProvider. |

### Async + domain events
| Change | File | Rationale |
|---|---|---|
| `AsyncConfig` with `@EnableAsync` + bounded `eventTaskExecutor` (core 4, max 10, queue 100) | `config/AsyncConfig.java` | Standard pattern; avoids colliding with Spring's default executor. |
| Three event records: Created / Shipped / Cancelled | `event/OrderCreatedEvent.java`, etc. | Capture the minimum payload listeners need — entity look-ups stay in listeners. |
| `OrderEventPublisher` wraps `ApplicationEventPublisher` | `event/OrderEventPublisher.java` | Callers depend on a typed domain API, not the framework abstraction. |
| `NotificationListener` with `@Async("eventTaskExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)` | `event/listener/NotificationListener.java` | AFTER_COMMIT guarantees the row is durable before "sending" the notification; @Async frees the request thread. |
| Wired publisher into `OrderService.createOrder` / `updateOrderStatus(SHIPPED, CANCELLED)` / `cancelOrder` | `service/OrderService.java` | Events emit at the exact moment state changes; transactional listener defers delivery. |
| Mock added to `OrderServiceTest` | `src/test/java/.../OrderServiceTest.java` | Constructor signature changed; @InjectMocks picks up the new @Mock automatically. |

### Caching
| Change | File | Rationale |
|---|---|---|
| Added `spring-boot-starter-cache` | `pom.xml` | (Caffeine was already on the classpath via rate-limit.) |
| `CacheConfig` with `@EnableCaching`, `CaffeineCacheManager`, spec `maximumSize=10_000,expireAfterWrite=10m,recordStats()` | `config/CacheConfig.java` | Pre-registers cache names so mutations don't lazily create caches with wrong specs. Disables null caching. |
| `@Cacheable` on `getProductById`, `getProductsByCategory` | `service/ProductService.java` | Read-heavy endpoints benefit; key includes pageable for category. |
| `@CacheEvict` (with `@Caching`) on `createProduct`, `updateProduct`, `deleteProduct` | `service/ProductService.java` | Single-product evictions key on `#productId`; category cache always cleared (one row can shuffle any page). |

### Scheduling
| Change | File | Rationale |
|---|---|---|
| Added `shedlock-spring` + `shedlock-provider-jdbc-template` (5.13.0) | `pom.xml` | Distributed lock over the same DB; no extra infrastructure. |
| `SchedulingConfig`: `@EnableScheduling`, `@EnableSchedulerLock`, `JdbcTemplateLockProvider` with `usingDbTime()` | `config/SchedulingConfig.java` | DB clock avoids inter-instance clock skew. |
| `PendingOrderCleanupJob` with hourly cron + `@SchedulerLock(lockAtLeastFor=PT1M, lockAtMostFor=PT5M)` | `job/PendingOrderCleanupJob.java` | Auto-cancels PENDING orders > 24h old; publishes OrderCancelledEvent so listeners fire normally. |
| `findByOrderStatusAndCreatedAtBefore` derived query | `repository/OrderRepository.java` | Used by the job to find stale orders. |

### Refresh tokens with rotation
| Change | File | Rationale |
|---|---|---|
| `RefreshToken` entity with tokenHash / userId / expiresAt / revoked / replacedByHash / familyId | `entity/RefreshToken.java` | Stores hash only — DB leak cannot impersonate users. familyId chains the rotation lineage. |
| `RefreshTokenRepository.findByTokenHash`, `findAllByFamilyId`, `markFamilyRevoked` | `repository/RefreshTokenRepository.java` | Lookup + family-wide bulk revoke for theft response. |
| `JwtService.generateRefreshToken()` (32B SecureRandom → base64url no-padding) + `hashToken()` (SHA-256 hex) | `security/JwtService.java` | Stateless generation; deterministic hash for indexed lookup. |
| `SecurityProperties.Jwt.refreshExpiryDays` (default 7) | `config/SecurityProperties.java` | Configurable lifetime. |
| `AuthService.issueTokens(...)` shared by login/getJwt; `refresh(String)` performs rotation + reuse detection | `service/AuthService.java` | On reuse-after-revoke, marks family revoked and 401s — canonical theft signal. |
| `TokenResponse` gained `refreshToken` field; back-compat overload kept | `dto/auth/TokenResponse.java` | Existing callers unaffected. |
| `RefreshTokenRequest` record | `dto/auth/RefreshTokenRequest.java` | No `@Valid`; 401 on missing/blank, not 400. |
| `POST /auth/refresh` endpoint | `controller/AuthController.java` | Already whitelisted via existing `/auth/**` permit. |

### Testcontainers
| Change | File | Rationale |
|---|---|---|
| Added `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-oracle-free` (1.19.8) | `pom.xml` | Spring Boot integration + Oracle Free container. |
| `UserIntegrationTest` — `@SpringBootTest(RANDOM_PORT)`, `@Testcontainers`, `@Container static OracleContainer`, `@DynamicPropertySource` wiring | `src/test/java/.../integration/UserIntegrationTest.java` | The canonical pattern. `.withReuse(true)` keeps the container alive between runs. Tests: registerThenLogin issues tokens; wrong password returns 401. Note: `org.testcontainers.oracle.OracleContainer` is the new package as of 1.19. |

### Resilience4j
| Change | File | Rationale |
|---|---|---|
| Added `resilience4j-spring-boot3` (2.2.0) | `pom.xml` | Spring Boot 3 auto-config. |
| `PaymentService.chargeOrder(...)` with `@CircuitBreaker(name=payment, fallbackMethod=chargeFallback) @Retry @Bulkhead` | `service/PaymentService.java` | Stub: 30% random failure + 100-2000ms random latency. Demonstrates all four primitives plus fallback. |
| `PaymentController` `POST /payments/charge` with `@PreAuthorize("hasRole('ADMIN')")` | `controller/PaymentController.java` | Admin-only demo endpoint. |
| `resilience4j.{circuitbreaker,retry,bulkhead}.instances.payment.*` | `application.yml` | Standard Resilience4j Boot binding — no Java config needed. |
| `ChargeRequest` DTO record | `dto/payment/ChargeRequest.java` | Simple body. |

### Custom health indicators
| Change | File | Rationale |
|---|---|---|
| `RateLimitFilter.cacheStats()` + nested `CacheStats` record | `ratelimit/RateLimitFilter.java` | Exposes occupancy as a typed value. |
| `RateLimitCacheHealthIndicator` extends `AbstractHealthIndicator`; DOWN when size > 95% of 100k | `health/RateLimitCacheHealthIndicator.java` | Hot cache means evictions; surface to ops via /actuator/health. |
| `ApplicationExceptionRepository.countByCreatedAtAfter(LocalDateTime)` | `repository/ApplicationExceptionRepository.java` | Backs the audit indicator. |
| `ExceptionAuditHealthIndicator` always UP, includes recent-5-min count | `health/ExceptionAuditHealthIndicator.java` | Informational — flipping a probe just because users are seeing errors would be worse than the errors themselves. |

### ArchUnit
| Change | File | Rationale |
|---|---|---|
| Added `archunit-junit5` (1.3.0) | `pom.xml` | Static architectural invariants. |
| `ArchitectureRulesTest` with 5 rules | `src/test/java/.../arch/ArchitectureRulesTest.java` | controllers ⊥ repositories, entities ⊥ DTOs, services ⊥ controllers, repository discoverability (@Repository or JpaRepository subtype), no System.out. |

### WebSocket
| Change | File | Rationale |
|---|---|---|
| Added `spring-boot-starter-websocket` | `pom.xml` | STOMP support. |
| `WebSocketConfig` implements `WebSocketMessageBrokerConfigurer` — `/ws` endpoint with SockJS, simple broker on `/topic`, app prefix `/app` | `config/WebSocketConfig.java` | Default in-memory broker is fine for dev. |
| `WebSocketOrderBridge` listens to all three OrderEvents via plain `@EventListener`, pushes to `/topic/orders/{userId}` via `SimpMessagingTemplate` | `event/listener/WebSocketOrderBridge.java` | Plain (not transactional) listener — broker dispatches async; clients refetch via REST anyway. |

### Documentation
| Change | File | Rationale |
|---|---|---|
| New "Implemented learning features" section | `README.md` | Table mapping each new feature to teaching value + file paths + curl. |
| New "Future scope" section grouped by tier | `README.md` | Modulith, @HttpExchange, Outbox+Kafka, GraalVM, Spring AI, Querydsl, hexagonal, CQRS, gRPC, JSON logging, observability stack, REST Docs, PIT, OWASP, GHA, Buildpacks, Helm. |
| One-time DB cleanup SQL block | `README.md` | DROP statements for migrating off ddl-auto. |
| Fourth pass section (this section) | `CLAUDE_ENHANCEMENTS.md` | Single source of truth for what changed and why. |

## Cross-cutting notes

- **`USERS` is reserved in Oracle.** The entity uses `@Table(name = "USERS")` (Hibernate auto-quotes), so V1 must also double-quote it (`CREATE TABLE "USERS"`). All FK references to it (USER_ROLES, ORDERS, REFRESH_TOKENS) double-quote consistently.
- **ShedLock 5.x package path.** `LockProvider` lives in `net.javacrumbs.shedlock.core`, not `support`. The 4.x → 5.x rename trips a lot of people.
- **Testcontainers oracle-free 1.19.x package.** `OracleContainer` was relocated to `org.testcontainers.oracle.OracleContainer` (was previously under `containers`).
- **TokenResponse compatibility.** Adding `refreshToken` to a record would normally break callers of `TokenResponse.bearer(token, seconds)`. Kept the 2-arg factory as a back-compat overload that passes `null` for the refresh token.
- **OrderServiceTest constructor change.** Adding `OrderEventPublisher` to the constructor required adding a `@Mock private OrderEventPublisher` field — `@InjectMocks` then auto-resolves the new param.

## Breaking-change notes

- **All profiles now use `ddl-auto: validate`.** The local profile previously used `create-drop`. Existing local databases must drop schema once (see README) or rely on Flyway baseline.
- **`OrderService` constructor signature.** Added `OrderEventPublisher` as the 6th argument. Any code directly constructing `OrderService` outside Spring's container must supply the publisher.
- **`AuthService` constructor signature.** Added `RefreshTokenRepository` as the 5th argument. Same caveat.
- **`TokenResponse` record shape.** Now has 4 components instead of 3. Jackson deserialization from older payloads still works (missing `refreshToken` → null), but tests asserting `equals` on the record will need updating.
- **`SecurityProperties.Jwt` record gained `refreshExpiryDays`.** Default 7. Tests that construct the record manually need the extra param.
