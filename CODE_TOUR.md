# Code Tour — a topic-by-topic learning guide

The fastest way to learn a real Spring Boot codebase is to read it. This guide lists every pattern implemented in this project with a one-paragraph explanation and the exact files to read in order.

Pair with [`UPGRADE_GUIDE.md`](./UPGRADE_GUIDE.md) (what's done + what's next) and [`LEARNING_ROADMAP.md`](./LEARNING_ROADMAP.md) (status across both projects).

## How to use this document

- Pick a topic you want to understand.
- Read the files in the order listed.
- Each entry has a "**TL;DR**" line so you can decide if it's interesting.
- File paths are clickable in IDEs that support markdown.
- "**Try it:**" lines suggest a curl call or breakpoint to make the concept concrete.

---

## Bootstrap & startup

> **TL;DR — How Spring Boot starts the app and what beans get wired.**

| File | What it does |
|---|---|
| [`ApiDesignApplication.java`](./src/main/java/com/apidesign/ApiDesignApplication.java) | The `main()` entry. `@SpringBootApplication` triggers component scan, auto-config, and configuration class loading. `@EnableAspectJAutoProxy(proxyTargetClass = true)` enables CGLIB proxies for any `@Aspect`. |
| [`pom.xml`](./pom.xml) | Dependencies in dependencyManagement section. Reading top-to-bottom shows the whole tech stack. |
| [`application.yml`](./src/main/resources/application.yml) | Base config — DB, JPA, Flyway, security, rate limit, observability, feature flags, Resilience4j. Most app-wide knobs live here. |
| [`application-local.yml`](./src/main/resources/application-local.yml) | Local-profile overrides: friendly DB defaults, JWT off, validation off, Flyway diagnostic logging on. |

**Try it:** `mvn spring-boot:run` and watch the log order — autoconfig → Flyway → JPA → security filter chain → Tomcat. Each log section maps to a config file.

---

## Spring Security & JWT

> **TL;DR — Stateless JWT auth gated by a feature flag; refresh-token rotation detects theft.**

| File | What it does |
|---|---|
| [`config/SecurityConfig.java`](./src/main/java/com/apidesign/config/SecurityConfig.java) | The `SecurityFilterChain` bean. Branches on `app.security.jwt.enabled`: when true the JWT filter and authorization rules apply; when false everything is permitAll (local-dev friendly). |
| [`security/JwtAuthenticationFilter.java`](./src/main/java/com/apidesign/security/JwtAuthenticationFilter.java) | `OncePerRequestFilter` that reads `Authorization: Bearer …`, validates the JWT via `JwtService`, and populates `SecurityContextHolder` with an `Authentication` carrying authorities derived from the `roles` claim. |
| [`security/JwtService.java`](./src/main/java/com/apidesign/security/JwtService.java) | Issues access tokens (HS256 via JJWT 0.12), generates refresh tokens (256-bit URL-safe base64), and SHA-256-hashes them for DB storage. |
| [`controller/AuthController.java`](./src/main/java/com/apidesign/controller/AuthController.java) | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/token` (no-`@Valid` variant). |
| [`service/AuthService.java`](./src/main/java/com/apidesign/service/AuthService.java) | Registration, login, refresh — including **family-revoke-on-reuse**: presenting a revoked refresh token revokes every token in that family, signalling theft. |
| [`entity/RefreshToken.java`](./src/main/java/com/apidesign/entity/RefreshToken.java) + [`repository/RefreshTokenRepository.java`](./src/main/java/com/apidesign/repository/RefreshTokenRepository.java) + [`db/migration/V3__refresh_tokens.sql`](./src/main/resources/db/migration/V3__refresh_tokens.sql) | Storage. `family_id` is the key concept — every login starts a family; every refresh rotates inside the same family; reuse blasts the family. |

**Try it:** Login, save the refresh token, refresh twice with the original refresh token. Second call returns 401 AND every still-valid token in the family is also revoked.

---

## Bean validation (flag-controlled)

> **TL;DR — `@Valid` becomes a no-op when `app.validation.enabled=false` so devs can iterate.**

| File | What it does |
|---|---|
| [`config/ValidationConfig.java`](./src/main/java/com/apidesign/config/ValidationConfig.java) | When the flag is true: standard `LocalValidatorFactoryBean`. When false: a subclass that overrides every `validate(...)` to no-op. Same trick applies to `MethodValidationPostProcessor`. |
| Any DTO under `dto/` | `@NotNull`/`@Email`/`@Positive` annotations. They stay on the code; the flag controls whether they fire. |

**Try it:** Local profile defaults validation off. POST a malformed body — it goes through. Flip `app.validation.enabled=true` via env var, POST the same body, see 400.

---

## Exception handling & ProblemDetail

> **TL;DR — Every error returns RFC 7807 JSON; the GlobalExceptionHandler maps every domain/framework exception explicitly.**

| File | What it does |
|---|---|
| [`exception/BaseException.java`](./src/main/java/com/apidesign/exception/BaseException.java) | Sealed root of the domain exception tree. Subclasses are final. |
| [`exception/ResourceNotFoundException.java`](./src/main/java/com/apidesign/exception/ResourceNotFoundException.java), `BusinessLogicException.java`, `ValidationException.java`, `DatabaseException.java` | Permitted subtypes. |
| [`advice/GlobalExceptionHandler.java`](./src/main/java/com/apidesign/advice/GlobalExceptionHandler.java) | `@RestControllerAdvice` with explicit handlers for `BaseException` subclasses, validation errors, message-not-readable, type mismatch, constraint violations, optimistic locking, data integrity, access denied, and a catch-all `Exception`. Returns `ProblemDetail` with custom `errorCode` + `correlationId` properties. |

**Try it:** Curl a non-existent user — the response body is `application/problem+json` with `type`, `title`, `status`, `detail`, `errorCode`, `correlationId`.

---

## Exception audit table

> **TL;DR — Every handled exception is persisted with request context so you can replay failed calls.**

| File | What it does |
|---|---|
| [`entity/ApplicationException.java`](./src/main/java/com/apidesign/entity/ApplicationException.java) + [`repository/ApplicationExceptionRepository.java`](./src/main/java/com/apidesign/repository/ApplicationExceptionRepository.java) + [`db/migration/V2__application_exception.sql`](./src/main/resources/db/migration/V2__application_exception.sql) | Persistent log of failures. |
| [`service/ExceptionAuditService.java`](./src/main/java/com/apidesign/service/ExceptionAuditService.java) | `@Transactional(REQUIRES_NEW)` writer — so audit persistence doesn't roll back when the outer transaction does. Truncates oversized bodies/stack traces. |
| [`observability/RequestCachingFilter.java`](./src/main/java/com/apidesign/observability/RequestCachingFilter.java) | Wraps JSON request bodies in `ContentCachingRequestWrapper` so the audit service can read the body that triggered the failure. |
| [`interceptor/RequestInterceptor.java`](./src/main/java/com/apidesign/interceptor/RequestInterceptor.java) | `HandlerInterceptor` that catches anything that escapes the global handler and audits it. Deduplicated via request attribute. |

**Try it:** Cause any 4xx/5xx, then `SELECT * FROM APPLICATION_EXCEPTION WHERE correlation_id = '<from response header>'`.

---

## Distributed tracing + correlation

> **TL;DR — Every request gets a correlation ID and an OTel trace; both flow into MDC and into the response header.**

| File | What it does |
|---|---|
| [`util/CorrelationIdUtil.java`](./src/main/java/com/apidesign/util/CorrelationIdUtil.java) | Wraps SLF4J `MDC` with helpers for the correlation-ID key. |
| [`observability/CorrelationIdFilter.java`](./src/main/java/com/apidesign/observability/CorrelationIdFilter.java) | `OncePerRequestFilter` ordered `HIGHEST_PRECEDENCE`. Reads or generates `X-Correlation-Id`, sets MDC, sets the response header **before** the chain runs (so it lands even on exception). |
| `application.yml` → `logging.pattern.console` | Pattern includes `[%X{correlationId:-}] [%X{traceId:-}/%X{spanId:-}]` so every log line is correlatable. |
| `application.yml` → `management.tracing.sampling.probability` + `management.otlp.tracing.endpoint` | OTel sampling and OTLP exporter target (defaults to local Tempo). |

**Try it:** Curl any endpoint with `-v`. Find `X-Correlation-Id` in the response. Grep logs for that ID — all log lines from that request appear together.

---

## Persistence — JPA + Hibernate

> **TL;DR — Standard Spring Data JPA with optimistic locking, per-entity sequences, atomic stock decrement, and `@EntityListeners(AuditingEntityListener.class)`.**

| File | What it does |
|---|---|
| [`entity/BaseEntity.java`](./src/main/java/com/apidesign/entity/BaseEntity.java) | Mapped-superclass with `id`, `@CreatedDate createdAt`, `@LastModifiedDate updatedAt`, `@CreatedBy createdBy`, `@LastModifiedBy updatedBy`. Annotated with `@EntityListeners(AuditingEntityListener.class)`. |
| [`config/DatabaseConfig.java`](./src/main/java/com/apidesign/config/DatabaseConfig.java) | `@EnableJpaAuditing` + `AuditorAware<String>` bean pulling principal from `SecurityContextHolder`, falling back to `"system"`. |
| [`entity/Product.java`](./src/main/java/com/apidesign/entity/Product.java) | Note `@Version Long version` (optimistic locking) and per-entity sequence `PRODUCT_SEQ` `allocationSize = 50`. |
| [`repository/ProductRepository.java`](./src/main/java/com/apidesign/repository/ProductRepository.java) | Look at the `@Modifying @Query` for atomic stock decrement (`UPDATE … WHERE stock_quantity >= :qty`). Race-condition-free. |
| [`constants/OrderStatus.java`](./src/main/java/com/apidesign/constants/OrderStatus.java) | Proper enum with `isValidTransition(from, to)` — no more string typos. |

**Try it:** Two concurrent decrement calls on the same product — only one succeeds; the other gets 409 InsufficientStock because the `WHERE … >= :qty` clause filters it out.

---

## Flyway versioned migrations

> **TL;DR — Schema-as-code; each migration is idempotent (PL/SQL guards) so re-runs are safe.**

| File | What it does |
|---|---|
| [`db/migration/V1__initial_schema.sql`](./src/main/resources/db/migration/V1__initial_schema.sql) | Users / Products / Orders / OrderItems / UserRoles / sequences. Every CREATE is wrapped in `BEGIN EXECUTE IMMEDIATE … EXCEPTION WHEN OTHERS THEN IF SQLCODE = -955 …` so re-running is safe. |
| `V2__application_exception.sql`, `V3__refresh_tokens.sql`, `V4__shedlock.sql`, `V5__idempotency_keys.sql`, `V6__order_saga.sql` | Each subsequent feature adds one migration. Reading them in order tells the history of the project. |
| [`scripts/cleanup-local-db.sql`](./scripts/cleanup-local-db.sql) | Fully-qualified `"apiservice"."<TABLE>"` drops with existence checks. Run before re-bootstrapping. |
| [`scripts/apply-schema-manually.sql`](./scripts/apply-schema-manually.sql) | Emergency fallback that chains V1..V6 by sqlplus `@include`. No SQL duplication. |
| [`scripts/seed-dummy-data.sql`](./scripts/seed-dummy-data.sql) | Idempotent INSERTs (procedure-based, natural-key checked) with two test users + 10 products + 1 demo order. Default password: `Password123!`. |
| `spring.flyway.schemas: apiservice` in `application.yml` + `spring.jpa.properties.hibernate.default_schema: apiservice` | Both Flyway and Hibernate target the same dedicated schema. |

**Try it:** `sqlplus … @scripts/cleanup-local-db.sql` then `mvn spring-boot:run`. Watch Flyway logs apply V1..V6 in order; then `SELECT version, description FROM apiservice.flyway_schema_history`.

---

## Spring Cache + Caffeine

> **TL;DR — Declarative caching on read-heavy methods; manual eviction on writes.**

| File | What it does |
|---|---|
| [`config/CacheConfig.java`](./src/main/java/com/apidesign/config/CacheConfig.java) | `@EnableCaching` + `CaffeineCacheManager` with `maximumSize=10_000, expireAfterWrite=10m, recordStats()`. |
| [`service/ProductService.java`](./src/main/java/com/apidesign/service/ProductService.java) | `@Cacheable(cacheNames = "products", key = "#id")` on read; `@CacheEvict` on create/update/delete (some single-key, some `allEntries=true`). |

**Try it:** Same `GET /products/1` twice in a row. Second one has no SQL log line.

---

## Async events + `@TransactionalEventListener`

> **TL;DR — Order state changes publish events; listeners run async AFTER the transaction commits.**

| File | What it does |
|---|---|
| [`event/OrderCreatedEvent.java`](./src/main/java/com/apidesign/event/OrderCreatedEvent.java), `OrderShippedEvent.java`, `OrderCancelledEvent.java` | Plain records describing what happened. |
| [`event/OrderEventPublisher.java`](./src/main/java/com/apidesign/event/OrderEventPublisher.java) | Wraps `ApplicationEventPublisher`. |
| [`config/AsyncConfig.java`](./src/main/java/com/apidesign/config/AsyncConfig.java) | `@EnableAsync` + `ThreadPoolTaskExecutor` (core 4, max 10, queue 100, CallerRunsPolicy). |
| [`event/listener/NotificationListener.java`](./src/main/java/com/apidesign/event/listener/NotificationListener.java) | `@Async("eventTaskExecutor")` + `@TransactionalEventListener(phase = AFTER_COMMIT)`. Fires after the order's transaction commits, on a different thread. |
| [`event/listener/WebSocketOrderBridge.java`](./src/main/java/com/apidesign/event/listener/WebSocketOrderBridge.java) | `@EventListener` (sync) pushing the same events to STOMP topic `/topic/orders/{userId}`. |

**Try it:** `POST /orders`. Watch the log: HTTP response goes out first, then `event-X` thread logs "Notification:". That's `AFTER_COMMIT` working.

---

## Scheduled jobs + ShedLock

> **TL;DR — Cron jobs that are safe across multiple app instances.**

| File | What it does |
|---|---|
| [`config/SchedulingConfig.java`](./src/main/java/com/apidesign/config/SchedulingConfig.java) | `@EnableScheduling` + `@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")` + JDBC `LockProvider` bound to the DataSource. |
| [`db/migration/V4__shedlock.sql`](./src/main/resources/db/migration/V4__shedlock.sql) | The `SHEDLOCK` table — one row per lock name. |
| [`job/PendingOrderCleanupJob.java`](./src/main/java/com/apidesign/job/PendingOrderCleanupJob.java) | `@Scheduled(cron = "0 0 * * * *")` + `@SchedulerLock(lockAtLeastFor=PT1M, lockAtMostFor=PT5M)` + `@Transactional(timeout = 240)` (under lock so it aborts cleanly before lock can expire). |

**Try it:** Insert an order, set its `created_at` 25 hours in the past, status PENDING. Wait for the top of the hour (or change the cron to every minute temporarily). The job cancels it + emits `OrderCancelledEvent`.

---

## Resilience4j (CircuitBreaker + Retry + Bulkhead)

> **TL;DR — A stub PaymentService demonstrates how to wrap unreliable downstream calls in three primitives.**

| File | What it does |
|---|---|
| [`service/PaymentService.java`](./src/main/java/com/apidesign/service/PaymentService.java) | `@CircuitBreaker(name="payment", fallbackMethod="chargeFallback")` + `@Retry(name="payment")` + `@Bulkhead(name="payment")`. The method itself randomly fails 30% to demo fallback. |
| `application.yml` → `resilience4j.{circuitbreaker,retry,bulkhead}.instances.payment.*` | Sliding-window, threshold, retry attempts, max concurrent calls. |
| [`controller/PaymentController.java`](./src/main/java/com/apidesign/controller/PaymentController.java) | `POST /payments/charge` (ADMIN-only) exposes the stub for poking. |

**Try it:** Curl `/payments/charge` 20 times in a row. Some succeed, some hit the fallback, eventually the breaker opens.

---

## Rate limiter (Bucket4j + Caffeine)

> **TL;DR — Token-bucket rate limiting per IP or per authenticated user, configurable per endpoint, observable via metrics.**

| File | What it does |
|---|---|
| [`ratelimit/RateLimitFilter.java`](./src/main/java/com/apidesign/ratelimit/RateLimitFilter.java) | `OncePerRequestFilter`. Builds a key (`user:<email>` or `ip:<addr>`), gets/creates a Bucket from a Caffeine `LoadingCache`, tries to consume 1 token. On miss: 429 + `Retry-After` + `X-RateLimit-*` headers + ProblemDetail body. |
| [`ratelimit/RateLimitProperties.java`](./src/main/java/com/apidesign/ratelimit/RateLimitProperties.java) | `@ConfigurationProperties("app.rate-limit")` — default rate, burst, multiplier for authenticated, per-endpoint overrides. |
| [`controller/RateLimitController.java`](./src/main/java/com/apidesign/controller/RateLimitController.java) | `GET /rate-limit/status` (self-inspection, no consume) + `GET /admin/rate-limit/buckets` (ADMIN paginated). |
| Metrics in `RateLimitFilter`: `rate_limit_cache_size` (gauge), `rate_limit_requests_total{outcome=…}`, `rate_limit_rejections_total{key_type=…}` | Scrape via `/actuator/prometheus`. |

**Try it:** `for i in $(seq 1 80); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/products; done`. First ~70 are 200, then 429 with `Retry-After`.

---

## Idempotency-Key on POST

> **TL;DR — Stripe-style safe-retry pattern: same key + same body → cached response replayed; same key + different body → 422.**

| File | What it does |
|---|---|
| [`filter/IdempotencyKeyFilter.java`](./src/main/java/com/apidesign/filter/IdempotencyKeyFilter.java) | `OncePerRequestFilter` running after caching. Hashes request body, looks up the key, replays or stores. |
| [`entity/IdempotencyKey.java`](./src/main/java/com/apidesign/entity/IdempotencyKey.java) + [`repository/IdempotencyKeyRepository.java`](./src/main/java/com/apidesign/repository/IdempotencyKeyRepository.java) + [`db/migration/V5__idempotency_keys.sql`](./src/main/resources/db/migration/V5__idempotency_keys.sql) | Storage. Columns: `idempotencyKey`, `requestHash`, `responseStatus`, `responseBody`, `endpoint`, `userId`. |
| `app.idempotency.enabled` in `application.yml` | Flag to disable the filter entirely. |

**Try it:** Use the three-step curl in `UPGRADE_GUIDE.md` (first call, replay with same key, mismatch with same key + different body).

---

## HATEOAS / HAL on GET endpoints

> **TL;DR — Resource representations include `_links` per HAL conventions; collections use `PagedModel`.**

| File | What it does |
|---|---|
| [`assembler/UserModelAssembler.java`](./src/main/java/com/apidesign/assembler/UserModelAssembler.java) | `RepresentationModelAssembler<User, EntityModel<UserDTO>>`. Builds `self`, `users` (collection), `user-orders` links via `linkTo(methodOn(Controller.class).method(...)).withRel("...")`. |
| `assembler/ProductModelAssembler.java`, `assembler/OrderModelAssembler.java` | Same pattern; `OrderModelAssembler` adds a conditional `cancel` link when the order is in a cancellable state. |
| Any `GET` method on the three controllers | Returns `EntityModel<DTO>` or `PagedModel<EntityModel<DTO>>` with `produces = MediaTypes.HAL_JSON_VALUE`. No more wrapping inside `ApiResponse`. |
| POST/PUT/DELETE | Still return `ApiResponse<DTO>` — those are commands, not resource representations. |

**Try it:** `curl -H "Accept: application/hal+json" http://localhost:8080/api/v1/users/1 | jq` — see `_links` next to the entity fields.

---

## Saga (orchestration)

> **TL;DR — Order creation is now a 4-step saga with explicit compensations and persisted state.**

| File | What it does |
|---|---|
| [`saga/SagaStep.java`](./src/main/java/com/apidesign/saga/SagaStep.java) | Enum `RESERVE_STOCK, CHARGE_PAYMENT, SCHEDULE_SHIPPING, COMPLETE_ORDER`. |
| [`saga/SagaState.java`](./src/main/java/com/apidesign/saga/SagaState.java) | Enum `STARTED, STOCK_RESERVED, PAYMENT_CHARGED, SHIPPING_SCHEDULED, COMPLETED, COMPENSATING_SHIPPING, COMPENSATING_PAYMENT, COMPENSATING_STOCK, COMPENSATED, FAILED`. |
| [`saga/OrderSagaOrchestrator.java`](./src/main/java/com/apidesign/saga/OrderSagaOrchestrator.java) | The orchestrator. Each step + state transition runs in `REQUIRES_NEW` so they commit independently — proper saga semantics. On failure, compensations run in reverse for completed steps. |
| [`entity/OrderSaga.java`](./src/main/java/com/apidesign/entity/OrderSaga.java) + [`repository/OrderSagaRepository.java`](./src/main/java/com/apidesign/repository/OrderSagaRepository.java) + [`db/migration/V6__order_saga.sql`](./src/main/resources/db/migration/V6__order_saga.sql) | State persistence. The `compensationLog` CLOB records every transition for post-mortem. |
| [`service/ShippingService.java`](./src/main/java/com/apidesign/service/ShippingService.java) | New stub that random-fails 10% to demo compensation paths. |
| [`service/OrderService.java`](./src/main/java/com/apidesign/service/OrderService.java) | `createOrder` now delegates to the orchestrator in 3 lines. The Resilience4j circuit-breaker on `PaymentService` stays — it's used inside the saga's CHARGE_PAYMENT step. |
| [`saga/OrderSagaOrchestratorTest.java`](./src/test/java/com/apidesign/saga/OrderSagaOrchestratorTest.java) | Unit tests for happy path, payment-fails compensation, shipping-fails compensation. |

**Try it:** `POST /orders` repeatedly. After ~10 calls some will hit ShippingService's random failure — `SELECT current_state, compensation_log FROM apiservice.order_saga ORDER BY created_at DESC` shows the chain.

---

## API design polish — ETag, Deprecation, OpenAPI

> **TL;DR — Three small additions that make the API more "professional".**

| File | What it does |
|---|---|
| [`config/WebConfig.java`](./src/main/java/com/apidesign/config/WebConfig.java) → `shallowEtagHeaderFilter()` bean | Hashes response bodies on GET; `If-None-Match` requests with the same ETag return 304 with no body. |
| [`interceptor/DeprecatedEndpoint.java`](./src/main/java/com/apidesign/interceptor/DeprecatedEndpoint.java) + [`interceptor/DeprecationInterceptor.java`](./src/main/java/com/apidesign/interceptor/DeprecationInterceptor.java) | RFC 8594 headers. Annotate any controller method with `@DeprecatedEndpoint(sunset="...", replacedBy="...")`. The interceptor sets `Deprecation: true`, `Sunset: <date>`, and two `Link:` headers. |
| [`config/OpenApiConfig.java`](./src/main/java/com/apidesign/config/OpenApiConfig.java) | Defines reusable `components.responses` (`NotFound`, `BadRequest`, `Unauthorized`, …) so controllers can reference them via `@ApiResponse(ref = "#/components/responses/NotFound")`. |
| Every `controller/*Controller.java` | `@Tag` + `@Operation` + `@ApiResponses` annotations make Swagger UI self-explanatory. |

**Try it:** Hit any GET endpoint twice with `-H "If-None-Match: <ETag-from-first-call>"`. Second call is a 304 with no body.

---

## WebSocket / STOMP push

> **TL;DR — Order events fan out to STOMP topic `/topic/orders/{userId}`.**

| File | What it does |
|---|---|
| [`config/WebSocketConfig.java`](./src/main/java/com/apidesign/config/WebSocketConfig.java) | `@EnableWebSocketMessageBroker`, STOMP `/ws` endpoint with SockJS, simple broker on `/topic`. |
| [`event/listener/WebSocketOrderBridge.java`](./src/main/java/com/apidesign/event/listener/WebSocketOrderBridge.java) | `@EventListener` (sync) that converts domain events into STOMP messages via `SimpMessagingTemplate`. |

**Try it:** Use a STOMP client (the Swagger UI WebSocket page or wscat) to subscribe to `/topic/orders/1`. Then `POST /orders` as user 1.

---

## Health indicators

> **TL;DR — Two custom probes that augment `/actuator/health` with domain-specific info.**

| File | What it does |
|---|---|
| [`health/RateLimitCacheHealthIndicator.java`](./src/main/java/com/apidesign/health/RateLimitCacheHealthIndicator.java) | DOWN if the rate-limit cache is > 95% full (close to evictions). |
| [`health/ExceptionAuditHealthIndicator.java`](./src/main/java/com/apidesign/health/ExceptionAuditHealthIndicator.java) | Always UP but exposes `recentExceptionCount` (last 5 minutes) — useful for alerting. |

**Try it:** `curl http://localhost:8080/api/v1/actuator/health?show-details=always` and look for the new components in the JSON.

---

## ArchUnit tests

> **TL;DR — Architecture-as-tests: layering rules + "no `System.out.println`".**

| File | What it does |
|---|---|
| [`arch/ArchitectureRulesTest.java`](./src/test/java/com/apidesign/arch/ArchitectureRulesTest.java) | Rules: controllers ⊥ repositories, entities ⊥ DTOs, services ⊥ controllers, repos `@Repository`-annotated, no `System.out`. |

**Try it:** Add a `System.out.println` somewhere and run `mvn test -Dtest=ArchitectureRulesTest`. It fails.

---

## Testcontainers integration test

> **TL;DR — Spin up a real Oracle in Docker, run the auth flow end-to-end.**

| File | What it does |
|---|---|
| [`integration/UserIntegrationTest.java`](./src/test/java/com/apidesign/integration/UserIntegrationTest.java) | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Container static OracleContainer` + `@DynamicPropertySource` wiring DB URL. Tests: register → login → refresh + reuse-detection. |

**Try it:** Make sure Docker is running, then `mvn test -Dtest=UserIntegrationTest`. Watch the container start, Flyway migrate, tests pass.

---

## Suggested reading order if you're new

If you're sitting down to learn this codebase from scratch, read in this order:

1. **Bootstrap & startup** — orient yourself.
2. **Exception handling & ProblemDetail** — see how every error response is shaped.
3. **Persistence — JPA + Hibernate** + **Flyway** — the data model.
4. **Spring Security & JWT** — auth path end to end.
5. **Distributed tracing + correlation** — how observability is wired.
6. **HATEOAS / HAL** — response shape for GETs.
7. **Rate limiter** — first non-trivial cross-cutting concern.
8. **Async events + `@TransactionalEventListener`** — second cross-cutting concern.
9. **Resilience4j** + **Saga** — fault tolerance at two levels.
10. **Idempotency-Key + ETag + Deprecation** — API polish.
11. **Scheduled jobs + ShedLock** + **Health indicators** + **WebSocket** — operational features.
12. **ArchUnit + Testcontainers** — how the tests stay honest.

After that, [`UPGRADE_GUIDE.md`](./UPGRADE_GUIDE.md) tells you what to add next.
