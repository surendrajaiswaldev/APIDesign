# Order Management System

A JSON-over-HTTP order management service: users, products, orders, with JWT auth,
RFC 7807 error responses, optimistic locking, and a token-bucket rate limiter.

## Tech stack

- Java 21, Spring Boot 3.3
- Spring Web MVC (servlet, virtual threads enabled)
- Spring Data JPA + Hibernate (Oracle in prod/dev, H2 in tests)
- Spring Security 6 (JWT, BCrypt password hashing)
- MapStruct 1.6 for DTO ↔ entity mapping
- Lombok for entity boilerplate (DTOs are records, no Lombok)
- Bucket4j 8.14 + Caffeine for rate limiting
- Micrometer Prometheus + OpenTelemetry OTLP for metrics and tracing
- SpringDoc OpenAPI for Swagger UI
- Maven (Spotless + Jacoco plugins configured)

## Architecture

Layered:

```
controller  -> service  -> repository  -> JPA / Oracle
                         \
                          \-> domain entities (BaseEntity audit fields)
```

- DTOs are records; mappers convert at the controller/service boundary.
- All write operations are transactional. Stock writes use atomic SQL (UPDATE WHERE
  qty >= ?) instead of read-modify-write.
- Errors surface as RFC 7807 `application/problem+json`. Success responses use a thin
  `ApiResponse<T>` envelope.

## Quickstart

```bash
# 1. Start Oracle locally (FREEPDB1 on 1521) however you prefer (docker, OracleXE, ...).
#    The local profile defaults to system/password for credentials.

# 2. Run the app — the local profile is the default for mvn spring-boot:run.
mvn spring-boot:run

# 3. Hit Swagger UI.
open http://localhost:8080/api/v1/swagger-ui/index.html

# 4. Register and login (see Authentication below).

# 5. Optional: copy .env.example to .env and override anything.
cp .env.example .env
```

Zero env vars required for local dev. The `local` profile activates automatically via
the Spring Boot Maven plugin configuration.

The `local` profile also disables two enforcement layers by default:

- **JWT authentication is OFF** (`app.security.jwt.enabled: false`). Every endpoint is
  callable without a token. The `/auth/token` endpoint still works and still issues a
  real JWT — flip the flag back to `true` to test authenticated flows.
- **Bean validation is OFF** (`app.validation.enabled: false`). `@Valid` / `@Validated`
  annotations stay on the source but produce no errors. Useful for iterating on shapes;
  not safe outside `local`.

Both flags are `true` in `dev` and `prod`.

## Configuration

All credentials are env-var driven with sensible defaults only in the `local` profile.

| Variable                  | Required (dev/prod) | Default (local) | Description                       |
|--------------------------|---------------------|-----------------|-----------------------------------|
| `SPRING_PROFILES_ACTIVE` | recommended         | `local`         | Profile to activate               |
| `DB_URL`                 | yes                 | localhost FREEPDB1 | JDBC URL                        |
| `DB_USERNAME`            | yes                 | `system`        | Database user                     |
| `DB_PASSWORD`            | yes                 | `password`      | Database password                 |
| `JWT_SECRET`             | yes                 | dev placeholder | HMAC-SHA256 signing key (>= 32B)  |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | no             | localhost:4318  | OTLP receiver for traces          |
| `SSL_KEYSTORE_PATH`      | prod only           | -               | PKCS12 keystore                   |
| `SSL_KEYSTORE_PASSWORD`  | prod only           | -               | Keystore password                 |

See `.env.example`. In `dev` and `prod` profiles the credentials have no default and
Spring will fail fast at startup if they're missing.

### Profiles

- `local` — committed defaults. Flyway owns schema; `ddl-auto: validate`. Verbose SQL logging.
- `dev`   — env vars required. `ddl-auto: validate`.
- `prod`  — env vars required. `ddl-auto: validate`. SSL on. Swagger UI off.

### Secrets

`.env`, `application-local-secrets.yml`, and `*.local-secrets.yml` are gitignored. Do
not commit credentials.

## Authentication

JWT bearer tokens, HS256. Register → log in → use token.

> **Note:** the `local` profile defaults to `app.security.jwt.enabled=false`, so every
> endpoint is open. The examples below assume the JWT flag is on (`dev`/`prod` default,
> or override locally with `--app.security.jwt.enabled=true`).

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "firstName":"Ada","lastName":"Lovelace","email":"ada@example.com",
    "password":"correct horse battery staple","phoneNumber":"1234567890"
  }'

# Login (returns ApiResponse envelope)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"correct horse battery staple"}' \
  | jq -r '.data.accessToken')

# Or: explicit "getJWT" endpoint — bare TokenResponse body, no envelope, no validation.
# Always callable, even when JWT enforcement is off.
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"ada@example.com","password":"correct horse battery staple"}' \
  | jq -r '.accessToken')

# Call a protected endpoint
curl http://localhost:8080/api/v1/users/1 -H "Authorization: Bearer $TOKEN"
```

Roles: `USER` (default), `ADMIN`. Admin-only endpoints include user deletion, product
create/update/delete, low-stock listing, order-status overrides, and listing orders
by status. Use `@PreAuthorize("hasRole('ADMIN')")` to extend this set.

To make a user an admin, set `roles` to `{"ADMIN","USER"}` directly in the database
for now (no admin-promotion endpoint is exposed; add one in your application code if
you need it).

## API reference

Base path: `/api/v1`. Full schema in Swagger UI.

| Method | Path                            | Roles needed         |
|-------:|---------------------------------|----------------------|
| POST   | `/auth/register`                | (public)             |
| POST   | `/auth/login`                   | (public)             |
| POST   | `/auth/token`                   | (public, no validation) |
| GET    | `/users`                        | USER                 |
| GET    | `/users/{id}`                   | USER                 |
| GET    | `/users/email/{email}`          | USER                 |
| PUT    | `/users/{id}`                   | USER                 |
| DELETE | `/users/{id}`                   | ADMIN                |
| POST   | `/users/{id}/deactivate`        | USER                 |
| POST   | `/products`                     | ADMIN                |
| GET    | `/products`                     | USER                 |
| GET    | `/products/{id}`                | USER                 |
| GET    | `/products/category/{category}` | USER                 |
| GET    | `/products/search`              | USER                 |
| GET    | `/products/lowstock`            | ADMIN                |
| PUT    | `/products/{id}`                | ADMIN                |
| DELETE | `/products/{id}`                | ADMIN                |
| POST   | `/orders`                       | USER                 |
| GET    | `/orders/{id}`                  | USER                 |
| GET    | `/orders/number/{orderNumber}`  | USER                 |
| GET    | `/orders/user/{userId}`         | USER                 |
| GET    | `/orders/by-status/{status}`    | ADMIN                |
| PUT    | `/orders/{id}/status`           | ADMIN                |
| POST   | `/orders/{id}/cancel`           | USER                 |
| GET    | `/rate-limit/status`            | (public)             |
| GET    | `/admin/rate-limit/buckets`     | ADMIN                |

Success envelope:

```json
{"timestamp":"...","status":200,"message":"...","data":{...}}
```

Error responses use bare RFC 7807 `application/problem+json`:

```json
{
  "type":"https://example.com/probs/validation-failed",
  "title":"Validation failed",
  "status":400,
  "detail":"One or more fields failed validation",
  "instance":"/api/v1/users",
  "errorCode":"VAL-001",
  "correlationId":"...",
  "fieldErrors":[{"field":"email","message":"Email should be valid","rejectedValue":"x"}]
}
```

## Implemented learning features

The fourth pass added a layer of "production-grade Spring" features. Each one is a
self-contained learning surface — touch the file paths, read the canonical patterns,
flex it with the local curl command.

| # | Feature | What it teaches | Key file(s) | Try it locally |
|---|---|---|---|---|
| 1 | **Flyway migrations** | DB-schema-as-code, versioned migrations, env-portable DDL. JPA `validate` instead of `ddl-auto`. | `src/main/resources/db/migration/V1..V4__*.sql`, `application.yml` (`spring.flyway.*`) | `mvn spring-boot:run` — Flyway runs on boot, see `flyway_schema_history` populate. |
| 2 | **Async events + listeners** | Domain events as records, `@TransactionalEventListener(AFTER_COMMIT)`, custom executor for `@Async`. | `event/`, `event/listener/NotificationListener.java`, `config/AsyncConfig.java`, wired in `service/OrderService.java` | `curl -X POST .../orders -d '...'` — watch `event-1` thread log "Notification: order N created". |
| 3 | **Spring Cache + Caffeine** | `@Cacheable` / `@CacheEvict`, cache spec tuning, key SpEL, `recordStats()` for metrics. | `config/CacheConfig.java`, `service/ProductService.java` | `curl .../products/1` twice — second call skips the repository (DEBUG log shows it). |
| 4 | **@Scheduled + ShedLock** | Cron-driven jobs, distributed locking so multi-instance deployments don't double-run. | `config/SchedulingConfig.java`, `job/PendingOrderCleanupJob.java`, `V4__shedlock.sql` | Top of every hour the job sweeps PENDING orders > 24h old. Manually invoke by lowering the cron in dev. |
| 5 | **Refresh-token rotation** | Production JWT flow: short-lived access + long-lived refresh, hash-only storage, family-level revoke on reuse-after-revoke. | `entity/RefreshToken.java`, `repository/RefreshTokenRepository.java`, `service/AuthService.refresh(...)`, `security/JwtService.hashToken`, `controller/AuthController.java` | `curl -X POST .../auth/refresh -d '{"refreshToken":"..."}'` to rotate; reuse the old one to see 401 + family-wide revoke. |
| 6 | **Testcontainers integration test** | Real Oracle in Docker, `@DynamicPropertySource`, `@SpringBootTest` end-to-end. | `src/test/java/com/apidesign/integration/UserIntegrationTest.java` | `mvn test -Dtest=UserIntegrationTest` (Docker must be running). |
| 7 | **Resilience4j** | Declarative `@CircuitBreaker` / `@Retry` / `@Bulkhead` + fallback method. | `service/PaymentService.java`, `controller/PaymentController.java`, `application.yml` (`resilience4j.*`) | Loop `POST /payments/charge` — flip from real refs to `DEGRADED:order-N` once breaker opens. |
| 8 | **Custom health indicators** | `AbstractHealthIndicator`, informational vs probing health, surfacing infrastructure to `/actuator/health`. | `health/RateLimitCacheHealthIndicator.java`, `health/ExceptionAuditHealthIndicator.java` | `curl .../actuator/health` (when authorized, full details — components include `rateLimitCache` and `exceptionAudit`). |
| 9 | **ArchUnit rules** | Static architectural invariants enforced at test time. | `src/test/java/com/apidesign/arch/ArchitectureRulesTest.java` | `mvn test -Dtest=ArchitectureRulesTest`. |
| 10 | **WebSocket (STOMP)** | Live server-push, broker config, listener-as-bridge from domain events to topics. | `config/WebSocketConfig.java`, `event/listener/WebSocketOrderBridge.java` | Connect a SockJS client to `ws://localhost:8080/api/v1/ws`, SUBSCRIBE to `/topic/orders/<userId>`, then create an order. (Note: JWT-on-handshake is out of scope — see Future scope.) |

### One-time DB cleanup when switching from `ddl-auto` to Flyway

If you previously ran the app under `ddl-auto: create-drop` or `update`, the schema
exists but Flyway has never logged it. The base config sets
`spring.flyway.baseline-on-migrate: false` deliberately — Flyway will refuse to run
against a non-empty schema rather than silently skip V1 and leave Hibernate validate
chasing missing columns. Drop everything and let Flyway apply V1..V4 cleanly:

```sql
-- Connect to your local Oracle as the application user and run:
-- Order matters: drop children before parents. CASCADE CONSTRAINTS handles the rest.
DROP TABLE ORDER_ITEMS         CASCADE CONSTRAINTS;
DROP TABLE ORDERS              CASCADE CONSTRAINTS;
DROP TABLE USER_ROLES          CASCADE CONSTRAINTS;
DROP TABLE REFRESH_TOKENS      CASCADE CONSTRAINTS;
DROP TABLE PRODUCTS            CASCADE CONSTRAINTS;
DROP TABLE "USERS"             CASCADE CONSTRAINTS;
DROP TABLE APPLICATION_EXCEPTION CASCADE CONSTRAINTS;
DROP TABLE SHEDLOCK            CASCADE CONSTRAINTS;
-- Flyway history MUST also go — Flyway treats a populated history table as a
-- managed schema and will not re-run prior versions, even if the tables are gone.
DROP TABLE FLYWAY_SCHEMA_HISTORY CASCADE CONSTRAINTS;

DROP SEQUENCE USER_SEQ;
DROP SEQUENCE PRODUCT_SEQ;
DROP SEQUENCE ORDER_SEQ;
DROP SEQUENCE ORDER_ITEM_SEQ;
DROP SEQUENCE APP_EXCEPTION_SEQ;
DROP SEQUENCE REFRESH_TOKEN_SEQ;
-- Then start the app — Flyway will apply V1..V4 from scratch.
```

Each `DROP` is independent — if an object doesn't exist (because a previous attempt
already dropped it), Oracle returns `ORA-00942: table or view does not exist` /
`ORA-02289: sequence does not exist`; both are safe to ignore. Run each statement
individually, or wrap them in a PL/SQL block that swallows those error codes.

**Adopt an existing schema** (only if you cannot drop data): set
`spring.flyway.baseline-on-migrate=true` *once* via an env var or CLI flag, point
`spring.flyway.baseline-version` at the migration that matches your current state
(e.g. `4` if all four V1..V4 are reflected in the tables), boot, then remove the
override. Do not leave `baseline-on-migrate: true` in committed config — it hides
schema drift.

## Feature flags

Four runtime flags govern the major cross-cutting layers. Defaults are tuned for
production safety; the `local` profile relaxes two of them for developer ergonomics.

| Flag                                | `application.yml` (base) | `local` | `dev` | `prod` | Effect when `false` |
|------------------------------------|--------------------------|---------|-------|--------|---------------------|
| `app.security.jwt.enabled`         | `true`                   | `false` | `true`| `true` | JWT filter not registered; every endpoint `permitAll()`. Method security (`@PreAuthorize`) not activated. |
| `app.validation.enabled`           | `true`                   | `false` | `true`| `true` | `@Valid` / `@Validated` become no-ops. Source annotations stay; runtime swallows them. |
| `app.rate-limit.enabled`           | `true`                   | `true`  | `true`| `true` | `RateLimitFilter` passes through without consuming bucket tokens. |
| `app.audit.exceptions.enabled`     | `true`                   | `true`  | `true`| `true` | `ExceptionAuditService.record(...)` returns immediately; no rows in `APPLICATION_EXCEPTION`. Request log lines unaffected. |

Override per run with `--app.security.jwt.enabled=true` on the command line, environment
variable `APP_SECURITY_JWT_ENABLED=true`, or a profile-specific YAML.

## Exception logging

Every uncaught exception (and every response with status >= 500) is persisted to the
`APPLICATION_EXCEPTION` Oracle table by `ExceptionAuditService`. The pipeline:

1. `RequestCachingFilter` wraps body-carrying requests in a `ContentCachingRequestWrapper`
   so the body can be re-read after the controller has consumed the input stream.
2. `RequestInterceptor` records start/end + duration per handler, and on `afterCompletion`
   calls the audit service whenever an exception leaked out or the response status >= 500.
3. `GlobalExceptionHandler` calls the audit service from each `@ExceptionHandler` so 4xx
   responses (which never throw past Spring's advice) still get a row.
4. A request-scoped attribute `exception.recorded` prevents double-recording when the
   handler and the interceptor would both fire for the same request.

Table schema (Hibernate `ddl-auto: update` creates it on first boot):

| Column             | Type           | Nullable | Notes                                       |
|--------------------|----------------|----------|---------------------------------------------|
| `id`               | `NUMBER`       | no       | `APP_EXCEPTION_SEQ` (allocationSize=50)     |
| `correlation_id`   | `VARCHAR2(64)` | yes      | From MDC; matches `X-Correlation-Id` header |
| `http_method`      | `VARCHAR2(10)` | yes      |                                             |
| `request_path`     | `VARCHAR2(512)`| yes      | `request.getRequestURI()`                   |
| `query_string`     | `VARCHAR2(2048)`| yes     |                                             |
| `request_body`     | `CLOB`         | yes      | Truncated to 4096 chars                     |
| `response_status`  | `NUMBER`       | yes      |                                             |
| `exception_class`  | `VARCHAR2(256)`| yes      | Fully qualified class name                  |
| `exception_message`| `CLOB`         | yes      | Truncated to 2048 chars                     |
| `stack_trace`      | `CLOB`         | yes      | Truncated to 8192 chars                     |
| `user_id`          | `VARCHAR2(128)`| yes      | From SecurityContext when present           |
| `client_ip`        | `VARCHAR2(64)` | yes      | Honors `X-Forwarded-For`                    |
| `duration_ms`      | `NUMBER`       | yes      | From the interceptor; `0` if not measured   |
| audit columns      | -              | -        | `created_at` / `updated_at` / `created_by`  |

Query failures by correlation ID:

```sql
SELECT id, response_status, exception_class, SUBSTR(exception_message, 1, 200)
FROM application_exception
WHERE correlation_id = '<id>'
ORDER BY created_at DESC;
```

Or via `ApplicationExceptionRepository.findByCorrelationIdOrderByCreatedAtDesc(...)` in
code. Persistence runs in `REQUIRES_NEW` so audit succeeds even if the calling transaction
is rolling back; failures during the audit write itself are logged at ERROR and swallowed
so they can never break the response.

Toggle off with `app.audit.exceptions.enabled=false` to skip persistence (request logging
remains).

## Rate limiting

Token bucket per principal (auth) or IP (unauth), keyed via Bucket4j with a Caffeine
cache (capacity 100k, TTL 10m).

Defaults in `application.yml`:

```yaml
app:
  rate-limit:
    enabled: true
    default-requests-per-minute: 60
    default-burst: 10
    authenticated-multiplier: 5.0
    endpoints: {}
```

Per-endpoint overrides:

```yaml
app:
  rate-limit:
    endpoints:
      productSearch:
        path-pattern: "/products/search"
        requests-per-minute: 30
        burst: 5
```

Skipped paths: `/auth/login`, `/auth/register`, `/actuator/health`, `/actuator/info`,
`/swagger-ui/**`, `/api-docs/**`, `/rate-limit/status`, `/admin/rate-limit/**`.

When a request is rejected the response is HTTP 429 with `Retry-After`,
`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers and a
ProblemDetail body whose `type` is `https://example.com/probs/rate-limited`.

### Observability

Two introspection endpoints and three Micrometer meters surface what the limiter is
doing in production. Both endpoints are in the skip list so calling them never burns a
token.

| Endpoint | Auth | Returns |
|---|---|---|
| `GET /api/v1/rate-limit/status` | any (anon = IP-keyed) | the caller's own bucket: `key`, `keyType`, `limit`, `available`, `resetInSeconds` — no token consumed |
| `GET /api/v1/admin/rate-limit/buckets?page=0&size=50` | `ADMIN` | paginated list of all cached buckets, sorted by `available` ASC (most-exhausted first) |

Metrics exposed at `/api/v1/actuator/prometheus`:

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `rate_limit_cache_size` | gauge | (none) | Live size of the Caffeine bucket cache (`buckets.estimatedSize()`) |
| `rate_limit_requests_total` | counter | `key_type={user,ip}`, `outcome={allowed,rejected,skipped}`, `endpoint={<pathPattern>,default}` | One increment per filter invocation |
| `rate_limit_rejections_total` | counter | `key_type={user,ip}`, `endpoint={<pathPattern>,default}` | Only incremented on 429; separate from the `requests_total` counter for cleaner alerting rules |

Example calls:

```bash
# Self-inspection (always reachable; no token even when JWT is on)
curl http://localhost:8080/api/v1/rate-limit/status

# Admin debugging — most-exhausted buckets first
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/admin/rate-limit/buckets?page=0&size=50'
```

## Observability

- Prometheus scrape: `GET /api/v1/actuator/prometheus`
- Health probes: `/actuator/health/liveness`, `/actuator/health/readiness`
- Info / loggers / metrics exposed
- Tracing via Micrometer → OpenTelemetry OTLP. Set `OTEL_EXPORTER_OTLP_ENDPOINT`
  to your collector (default `http://localhost:4318/v1/traces`).
- Every request gets an `X-Correlation-Id` (echoed back, generated if missing).
  MDC variables `correlationId`, `traceId`, `spanId` appear in log lines:

```
2026-05-28 11:00:00.000 INFO  [a1b2…] [trace-id/span-id] c.a.x.Y - ...
```

## Data model

- `users` (id, first_name, last_name, email UNIQUE, password_hash, phone_number, …,
  is_active, user_type, created_at, updated_at, created_by, updated_by)
- `user_roles` (user_id, role)
- `products` (id, version, sku UNIQUE, name, description, price, stock_quantity,
  min_stock_level, category, is_available, supplier, audit columns)
- `orders` (id, version, order_number UNIQUE, user_id FK, order_status, total_amount,
  shipping_address, notes, estimated_delivery, audit columns)
- `order_items` (id, order_id FK, product_id FK, product_name snapshot,
  product_sku snapshot, unit_price snapshot, quantity, discount, notes, audit columns)

Sequences (each `allocationSize=50`): `USER_SEQ`, `PRODUCT_SEQ`, `ORDER_SEQ`,
`ORDER_ITEM_SEQ`.

## Testing

```bash
mvn test               # unit + slice tests (H2 in-memory)
mvn verify             # tests + Jacoco coverage report at target/site/jacoco/
mvn spotless:apply     # format
```

Repository tests are `@DataJpaTest` and run against H2. Service tests are pure
Mockito. No Oracle is required for `mvn test`.

## Future scope

The items below were considered for the fourth pass but deliberately deferred — each
would have been a significant standalone learning beat in its own right. Grouped into
the same tiers as the original brief.

### Tier 1 — Architectural patterns

- **Spring Modulith** — split the monolith into verified modules (orders, products, users)
  with package-scope enforcement and event-published-between-modules. Teaches modular
  monolith decomposition and `@ApplicationModuleTest` slice isolation.
- **Hexagonal restructure** — convert package layout to `domain` / `application` /
  `infrastructure` / `adapter`. Teaches dependency-inversion at the architecture level
  (controllers and repositories both become adapters around a domain core).
- **CQRS / Domain events as aggregates** — separate write models (commands) from read
  models (queries / projections), persist domain events alongside aggregate writes.
  Teaches event sourcing fundamentals without going full ES.

### Tier 2 — Integration patterns

- **@HttpExchange declarative HTTP clients** — Spring 6's typed REST client via
  interface + annotations (Feign-style, no Feign). Teaches the modern Spring way to
  consume external APIs.
- **Outbox pattern + Kafka** — write events to a DB `outbox` table inside the same
  transaction as the entity, then a poller publishes to Kafka. Teaches reliable
  at-least-once delivery without distributed transactions. (User noted "no Kafka" but
  the pattern itself is the lesson; could implement with an in-memory broker.)
- **gRPC alternative endpoint** — expose the same domain via gRPC alongside REST.
  Teaches protobuf, generated stubs, streaming RPCs.

### Tier 3 — Querying

- **Querydsl / Specifications** — type-safe dynamic queries. Replace string JPQL in
  `searchProducts(...)` with Querydsl `BooleanBuilder` or Spring Data `Specification`
  composition. Teaches building dynamic predicates safely.

### Tier 4 — Native/AI

- **GraalVM native image** — `mvn -Pnative native:compile`. Teaches AOT compilation
  trade-offs, reflection registration, runtime hints.
- **Spring AI integration** — wire `ChatClient` against a local LLM (Ollama). Teaches
  the Spring AI abstraction and prompt-as-a-template patterns.

### Tier 5 — Observability + quality

- **Structured JSON logging (logstash-logback-encoder)** — switch the console pattern
  to JSON so log aggregators (Loki, ELK, Cloud Logging) can parse fields like
  `correlationId` natively instead of via regex. Teaches structured logging.
- **Local observability stack via docker-compose** — Prometheus + Grafana + Loki +
  Tempo containers reading from the existing actuator endpoints. Teaches end-to-end
  observability wiring.
- **Spring REST Docs** — generate API docs from passing tests rather than annotations.
  Teaches doc-as-test workflow.
- **Mutation testing (PIT)** — run `mvn org.pitest:pitest-maven:mutationCoverage` to
  see which mutations your tests fail to detect. Teaches the gap between coverage and
  effectiveness.
- **OWASP Dependency-Check** — `mvn org.owasp:dependency-check-maven:check` against
  the NVD. Teaches supply-chain security.

### Tier 6 — Delivery

- **GitHub Actions CI** — workflow for `mvn verify`, Spotless check, image build.
  Teaches the GitHub-native CI primitives.
- **Dockerfile + Buildpacks** — `mvn spring-boot:build-image` for OCI image production.
  Teaches the modern non-Dockerfile container build path.
- **Helm chart** — package the app for Kubernetes deployment. Teaches templating and
  values files. (User noted "no K8s" — listed for completeness.)

### Other deferrals from the fourth pass scope

- **WebSocket JWT-on-handshake** — production deployments authenticate the STOMP
  CONNECT frame, not just the HTTP upgrade. Out of scope here; the bridge is in
  `event/listener/WebSocketOrderBridge.java` and broadcasts to anonymous topics.
- **Refresh-token blacklist on logout** — the rotation flow handles theft detection,
  but explicit logout requires marking the family revoked on demand. Trivially adds
  to `AuthService` as `revoke(String refreshToken)`.

## Troubleshooting

- **App won't start: Flyway validation error or Hibernate `Schema-validation: missing
  table/column`** — usually the schema is in a half-migrated state. Either previous
  `ddl-auto` runs left tables that don't match V1..V4, or a previous boot dropped
  app tables but left `FLYWAY_SCHEMA_HISTORY` behind. The boot path is "Flyway runs
  → Hibernate validates"; both must agree. Run the cleanup SQL in
  [One-time DB cleanup](#one-time-db-cleanup-when-switching-from-ddl-auto-to-flyway)
  (specifically including `FLYWAY_SCHEMA_HISTORY`) and restart.
- **App won't start, complains about `DB_PASSWORD`** — you're on dev/prod without
  setting the env var. Either set it or switch to the `local` profile.
- **HTTP 401 on protected endpoints** — token missing/expired. Re-login and pass
  `Authorization: Bearer <token>`.
- **HTTP 409 on `POST /orders`** — concurrent orders against the same product
  exhausted stock between attempts. Retry; the service already retries 3 times on
  `DataIntegrityViolationException`. Persistent 409 means actual integrity issue.
- **HTTP 429 with `Retry-After`** — rate-limited. Either back off or raise
  `app.rate-limit.*` in `application.yml`.
- **`OptimisticLockingFailureException` → 409** — another request modified the
  same row. Refetch and retry with the latest version.
