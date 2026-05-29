# Upgrade Guide

What's been added, what's planned, and how much each upgrade costs in time + thinking. Pairs with [`LEARNING_ROADMAP.md`](./LEARNING_ROADMAP.md) (status comparison across both projects) and [`CODE_TOUR.md`](./CODE_TOUR.md) (how to read the code by topic).

## Most recent upgrade pass — Sixth pass

**Theme:** API design polish + HATEOAS/HAL re-implementation + proper orchestrated saga + timeout coverage.

| Item | Where it lives | Brief |
|---|---|---|
| ETag on every GET | `config/WebConfig.java` (`ShallowEtagHeaderFilter` bean) | Body-hash ETag emitted automatically; `If-None-Match` returns 304. Zero controller change. |
| Idempotency-Key on POST/PATCH | `filter/IdempotencyKeyFilter`, `entity/IdempotencyKey`, `V5__idempotency_keys.sql`, flag `app.idempotency.enabled` | Header `Idempotency-Key`. First 2xx response cached; same key replays with `Idempotent-Replay: true`; same key + different body → 422 conflict. |
| Deprecation / Sunset headers | `@DeprecatedEndpoint` annotation + `DeprecationInterceptor` | Annotate a method; `Deprecation: true`, `Sunset: <date>`, and two `Link:` headers appear on every call. Demo: `UserController.getUserByEmail`. |
| OpenAPI metadata | `@Tag` / `@Operation` / `@ApiResponses` across every controller; reusable responses in `OpenApiConfig` | Swagger UI now describes every endpoint with proper responses + parameters + examples. |
| JPA query timeout (5 s) | `application.yml` — `spring.jpa.properties.jakarta.persistence.query.timeout` | Caps every JPA query at 5 s globally. |
| `@Transactional` timeouts | `OrderService.createOrder` (30 s), `OrderService.cancelOrder` (20 s), `PendingOrderCleanupJob` (240 s) | Bounded transaction lifetimes; cleanup job timeout chosen < ShedLock `lockAtMostFor`. |
| Async back-pressure | `AsyncConfig` — `CallerRunsPolicy` + bounded queue 100 | When the event-listener pool is saturated, callers run the task themselves instead of OOM. |
| HATEOAS / HAL (proper) | `assembler/UserModelAssembler`, `assembler/ProductModelAssembler`, `assembler/OrderModelAssembler`; GET methods return `EntityModel<T>` / `PagedModel<EntityModel<T>>`; `MediaTypes.HAL_JSON_VALUE` | Resources include `_links` (`self`, `users`, `orders`, `user-orders`, `cancel`, …). No more `ApiResponse`-wrapping-EntityModel hybrid. |
| Saga pattern (orchestration) | `saga/OrderSagaOrchestrator`, `saga/SagaState`, `saga/SagaStep`, `entity/OrderSaga`, `repository/OrderSagaRepository`, `service/ShippingService` (stub), `V6__order_saga.sql` | Four steps: `RESERVE_STOCK` → `CHARGE_PAYMENT` → `SCHEDULE_SHIPPING` → `COMPLETE_ORDER`. Each step + state transition in `REQUIRES_NEW`. Compensations: restore stock, refund, cancel shipment. State persisted in `ORDER_SAGA`. |
| Saga unit tests | `saga/OrderSagaOrchestratorTest` | Happy path + payment-fails compensation + shipping-fails compensation. |

`mvn -q -DskipTests compile` and `mvn -q test-compile` both clean. 17 new files, 19 modified.

---

## Implementation status — by topic

Effort: **S** ≈ half a day · **M** ≈ 1-3 days · **L** ≈ a week+.
Complexity: **🟢** mechanical / additive · **🟡** touches several files / requires design decisions · **🔴** architectural / cross-cutting / requires understanding multiple subsystems.

### Done — production-grade patterns

| Capability | Status | Effort spent | Complexity | Why it matters |
|---|:---:|:---:|:---:|---|
| Spring Security + JWT (HS256) | ✅ | M | 🟡 | Standard stateless auth |
| BCrypt password hashing | ✅ | S | 🟢 | Industry baseline |
| Refresh tokens with family-revoke-on-reuse | ✅ | M | 🟡 | Hardened token lifecycle |
| Bean Validation with feature flag | ✅ | M | 🟡 | Dev ergonomics + prod safety |
| RFC 7807 ProblemDetail errors | ✅ | M | 🟡 | Standard error contract |
| Springdoc OpenAPI + Swagger UI | ✅ | S | 🟢 | Auto-generated API docs |
| OpenAPI metadata (`@Tag`/`@Operation`) | ✅ | S | 🟢 | Self-describing endpoints |
| Rate limiter (Bucket4j + Caffeine) | ✅ | M | 🟡 | Token bucket; per-endpoint overrides |
| Rate-limit observability (status, admin, metrics) | ✅ | M | 🟡 | Operational visibility |
| Correlation IDs via filter + MDC | ✅ | S | 🟢 | Request tracing primitive |
| OpenTelemetry + Tempo OTLP exporter | ✅ | M | 🟡 | Distributed tracing |
| Micrometer + Prometheus | ✅ | S | 🟢 | Metrics scrape |
| Actuator endpoints | ✅ | S | 🟢 | Health, info, loggers |
| Custom health indicators | ✅ | S | 🟢 | Domain probes (rate-limit cache, audit) |
| Exception audit table (persistent) | ✅ | M | 🟡 | Failure replay |
| Flyway versioned migrations (idempotent PL/SQL) | ✅ | M | 🟡 | Schema-as-code |
| Per-entity sequences + `@Version` | ✅ | S | 🟢 | Concurrency control |
| Atomic stock decrement (conditional UPDATE) | ✅ | S | 🟢 | Race-free mutation |
| JPA auditing (`@CreatedDate`/`@CreatedBy`) | ✅ | S | 🟢 | Standard audit columns |
| Java 21 records + sealed exceptions | ✅ | S | 🟢 | Modern language features |
| Virtual threads | ✅ | S | 🟢 | Project Loom |
| Spring Cache + Caffeine (`@Cacheable`) | ✅ | S | 🟢 | Read-through caching |
| Async events + `@TransactionalEventListener(AFTER_COMMIT)` | ✅ | M | 🟡 | Decoupled in-process events |
| `@Scheduled` + ShedLock JDBC | ✅ | S | 🟡 | Cluster-safe cron |
| Resilience4j (CircuitBreaker + Retry + Bulkhead) | ✅ | M | 🟡 | Fault tolerance |
| WebSocket / STOMP push | ✅ | S | 🟡 | Real-time |
| ArchUnit layering tests | ✅ | S | 🟢 | Architecture-as-tests |
| Testcontainers Oracle Free integration test | ✅ | S | 🟢 | Production-like tests |
| ETag via ShallowEtagHeaderFilter | ✅ | S | 🟢 | HTTP-layer caching |
| Idempotency-Key header support | ✅ | M | 🟡 | Safe retries |
| Deprecation / Sunset headers | ✅ | S | 🟢 | RFC 8594 / API lifecycle |
| HATEOAS / HAL on GETs | ✅ | M | 🟡 | Hypermedia API |
| Saga (orchestration) | ✅ | L | 🔴 | Distributed-transaction substitute |
| Timeouts: JPA query / Tx / Scheduled / Async | ✅ | S | 🟢 | Bounded latency |
| Bucket4j rate-limit + Caffeine + metrics | ✅ | M | 🟡 | Throttling + observability |

### Not yet done — recommended upgrades

Ranked by **learning value × industry-real-world frequency** ÷ **effort**.

| # | Capability | Effort | Complexity | Priority | What it teaches |
|---|---|:---:|:---:|:---:|---|
| 1 | **GitHub Actions CI** | S | 🟢 | ⭐⭐⭐ | Every modern team has CI; learn workflow syntax + caching |
| 2 | **OWASP Dependency-Check + Dependabot** | S | 🟢 | ⭐⭐⭐ | Supply-chain hygiene |
| 3 | **JWKS + RS256 asymmetric JWT** | M | 🟡 | ⭐⭐⭐ | What real OAuth providers do |
| 4 | **K8s Helm chart for monolith deploy** | M | 🟡 | ⭐⭐⭐ | K8s primitives: Deployment, Service, ConfigMap, Secret, Ingress |
| 5 | **API gateway (Spring Cloud Gateway)** | M | 🟡 | ⭐⭐⭐ | Centralized auth, rate limit, routing |
| 6 | **Outbox pattern + Kafka** | L | 🔴 | ⭐⭐⭐ | Reliable event publishing |
| 7 | **Spring Modulith verification** | S | 🟡 | ⭐⭐⭐ | Modular monolith pattern |
| 8 | **Redis-backed cache + rate limiter** | M | 🟡 | ⭐⭐ | Multi-instance shared state |
| 9 | **GraalVM native image** | M | 🟡 | ⭐⭐ | AOT + reflection limits |
| 10 | **Spring REST Docs from MockMvc tests** | S | 🟢 | ⭐⭐ | Always-in-sync API docs |
| 11 | **Mutation testing (PIT)** | S | 🟢 | ⭐⭐ | Test-quality gate |
| 12 | **Contract tests (Pact / Spring Cloud Contract)** | M | 🟡 | ⭐⭐ | Consumer-driven contracts |
| 13 | **Hexagonal refactor on Order aggregate** | M | 🔴 | ⭐⭐ | Domain-driven architecture |
| 14 | **CQRS (lightweight, separate read service)** | M | 🔴 | ⭐⭐ | Read-model materialization |
| 15 | **HashiCorp Vault for secrets** | M | 🟡 | ⭐⭐ | Real secret management |
| 16 | **Feature flags via Unleash / LaunchDarkly** | S | 🟢 | ⭐⭐ | Controlled rollout |
| 17 | **Spring AI (Claude) for product descriptions** | S | 🟢 | ⭐⭐ | AI integration; tool calls; structured output |
| 18 | **Multi-tenant data isolation** | M | 🔴 | ⭐⭐ | SaaS basics |
| 19 | **OTel sampling + tail-based traces** | M | 🟡 | ⭐⭐ | Production trace cost control |
| 20 | **WireMock-recorded contracts** | S | 🟢 | ⭐⭐ | Reusable HTTP test doubles |
| 21 | **Performance testing (k6 / JMeter)** | M | 🟡 | ⭐ | SLO baselines |
| 22 | **Chaos Monkey for Spring Boot** | S | 🟢 | ⭐ | Resilience verification |
| 23 | **Querydsl / JPA Specifications** | M | 🟡 | ⭐ | Type-safe dynamic queries |
| 24 | **Read replicas + DataSource routing** | M | 🔴 | ⭐ | Read/write split |
| 25 | **GDPR / PII redaction in logs** | S | 🟢 | ⭐ | Compliance |
| 26 | **gRPC alternative for one endpoint** | M | 🟡 | ⭐ | Contract-first binary protocol |
| 27 | **Sparse fieldsets** (`?fields=id,name`) | M | 🟡 | ⭐ | Bandwidth optimisation |
| 28 | **RSQL or query-DSL filtering** | M | 🟡 | ⭐ | Composable query language |
| 29 | **Bulk endpoints** (`POST /orders/bulk`) | M | 🟡 | ⭐ | Batch API |
| 30 | **Saga crash recovery** | M | 🔴 | ⭐ | Saga state restoration on restart |

---

## Suggested next sprint — 5 days

If you have a week to spend, this gets you the most learning per hour:

| Day | Task | Outcome |
|---|---|---|
| 1 | #1 GitHub Actions CI + #2 dependency scanning | Every push validated; CVEs surfaced |
| 2 | #3 JWKS + RS256 (auth-service) | Public-key validation; what Auth0/Keycloak do |
| 3 | #4 K8s Helm chart for monolith | Deploy on local Kind / Minikube |
| 4 | #7 Spring Modulith verification | Module-boundary tests at architecture level |
| 5 | #5 API Gateway in front of monolith (Spring Cloud Gateway) | Single entry-point with centralized JWT + rate-limit + routing |

After day 5 you'd have hit four of the five most-asked-about patterns in senior-engineer interviews.

## Why some things are NOT in this list

Things I deliberately excluded from the upgrade list:

- **Migrating Oracle → Postgres in the monolith.** Lots of work; the microservices project already shows PostgreSQL + R2DBC. Apples-to-apples comparison stays useful.
- **Replacing MVC with WebFlux in the monolith.** Same reason: the microservices project is the WebFlux teaching ground.
- **Adding a frontend to the monolith.** Microservices project already has the React UI demo.
- **Service mesh (Istio / Linkerd).** Out of scope for a learning project — its value only appears at scale.
- **Event sourcing.** Distinct from Outbox; teaches a different mental model. Worth a separate dedicated learning project.

## How to track your progress

When you complete an upgrade:
1. Flip the row in this file from "not yet done" to "done" with effort actually spent.
2. Append a paragraph to `CLAUDE_ENHANCEMENTS.md` describing what you did and any divergences from the suggested approach.
3. Add a corresponding section to `CODE_TOUR.md` so future-you can re-learn it by reading the code.

This way the three docs stay in sync: roadmap → upgrade → code tour.
