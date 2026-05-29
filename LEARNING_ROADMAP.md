# Learning Roadmap

A side-by-side inventory of what's implemented across the two paired projects, and a prioritized list of what to add next for industry-practice learning.

## The two projects

| Project | Repo | What it demonstrates |
|---|---|---|
| **APIDesign monolith** | [surendrajaiswaldev/APIDesign](https://github.com/surendrajaiswaldev/APIDesign) (branch `feature/claude-modernization`) | Spring MVC + JPA + Oracle, single deployable. Full security, audit, rate-limit, async events, refresh tokens, observability, and dev utility scripts. |
| **APIDesign microservices** | [cardinal-mentoroid/apidesign-microservices](https://github.com/cardinal-mentoroid/apidesign-microservices) | WebFlux + R2DBC + PostgreSQL, 4 services + React UI + full observability stack. JWT propagation, `@HttpExchange` declarative clients, saga-style compensation. |

The two projects are **deliberately paired**: same domain (orders / users / products) implemented twice at different scales so you can compare blocking-JPA-monolith vs reactive-R2DBC-microservices side by side.

---

## Status legend

**✅** implemented · **🟡** partial · **❌** not yet · **n/a** doesn't apply to that stack

---

## What's implemented

### Core architecture

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Spring Boot 3.3 + Java 21 baseline | ✅ | ✅ | Modern Spring foundations |
| Spring MVC + Tomcat (servlet stack) | ✅ | ❌ | Classic blocking I/O model |
| Spring WebFlux + Netty (reactive stack) | ❌ | ✅ | Non-blocking, Reactor operators |
| Multi-module Maven project | ❌ | ✅ | Module boundaries, build orchestration |
| Records for DTOs | ✅ | ✅ | Java 21 immutability |
| Sealed exception hierarchy | ✅ | ❌ | Java 21 pattern matching surface |
| Virtual threads | ✅ | n/a (WebFlux native) | Project Loom |

### Persistence

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| JPA + Hibernate (blocking) | ✅ Oracle | ❌ | Classic ORM patterns |
| Spring Data R2DBC (reactive) | ❌ | ✅ PostgreSQL | Reactive DB access |
| Flyway versioned migrations | ✅ | ✅ | Schema-as-code |
| Idempotent migrations (PL/SQL guards) | ✅ | ❌ | Re-runnable schema scripts |
| Per-entity sequences with optimistic locking (`@Version`) | ✅ | 🟡 | Concurrency control |
| Atomic stock decrement via conditional UPDATE | ✅ | ✅ | Race-condition-free mutation |
| JPA auditing (`@CreatedDate` / `@CreatedBy` + `AuditorAware`) | ✅ | ❌ | Standard audit columns |
| Dedicated schema (`apiservice` / per-service DB) | ✅ | ✅ | Schema isolation |

### Security & auth

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Spring Security baseline | ✅ | ✅ | Filter chains, authorization |
| BCrypt password hashing (strength 12) | ✅ | ✅ | Credential storage |
| JWT (HS256) issuance + validation | ✅ | ✅ | Stateless auth |
| Refresh token rotation | ✅ | ✅ | Token lifecycle |
| Family-revoke-on-reuse (theft detection) | ✅ | ✅ | Hardened JWT pattern |
| Flag-controlled auth (`app.security.jwt.enabled`) | ✅ | ❌ | Dev ergonomics |
| `@PreAuthorize` method security | ✅ | ✅ | Declarative auth |
| Service-to-service JWT propagation | n/a | ✅ | Inter-service trust |
| OAuth 2.0 / OIDC delegation (Auth0/Keycloak) | ❌ | ❌ | External IdP integration |
| Asymmetric JWT (RS256 + JWKS) | ❌ | ❌ | Token-issuer separation |

### API design

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| RFC 7807 `ProblemDetail` error responses | ✅ | ✅ | Standard error contract |
| Custom envelope (`ApiResponse<T>`) for success | ✅ | ❌ | Trade-off vs bare responses |
| Springdoc OpenAPI / Swagger UI | ✅ | ❌ | Auto-generated docs |
| `@HttpExchange` declarative HTTP clients | ❌ | ✅ | Spring 6 typed clients |
| `Mono.zip` parallel fan-out across services | n/a | ✅ | Reactive orchestration |
| Saga-style compensation (restore on failure) | ❌ | ✅ | Distributed transaction substitute |
| HATEOAS / HAL links | ❌ (removed) | ❌ | Hypermedia (not chosen) |
| Idempotency keys on POST | ❌ | ❌ | Safe retry semantics |

### Cross-cutting concerns

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Correlation ID filter | ✅ `OncePerRequestFilter` | ✅ reactive `WebFilter` | Request tracing primitive |
| MDC + Reactor context propagation | ✅ | ✅ | Async logging context |
| `HandlerInterceptor` for request logging + audit | ✅ | n/a | Spring MVC lifecycle |
| Persisted exception audit table | ✅ | ❌ | Failure-replay capability |
| Flag-controlled bean validation (`app.validation.enabled`) | ✅ | ❌ | Dev ergonomics |
| AOP method timing / logging | ✅ | ❌ | Cross-cutting via aspects |

### Resilience

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Resilience4j circuit breaker | ✅ (stub `PaymentService`) | ✅ (every cross-service call) | Fault tolerance pattern |
| Resilience4j retry | ✅ | ✅ (idempotent calls only) | Transient-failure handling |
| Resilience4j bulkhead | ✅ | ❌ | Resource isolation |
| Timeouts on outbound calls | ❌ | ✅ | Bounded latency contract |
| Bucket4j rate limiter (Caffeine backend) | ✅ | ❌ | Token bucket algorithm |
| Per-endpoint rate-limit overrides | ✅ | ❌ | Granular throttling |

### Observability

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Actuator endpoints (`health` / `info` / `metrics` / `prometheus`) | ✅ | ✅ | Operational visibility |
| Micrometer Prometheus registry | ✅ | ✅ | Metrics emission |
| Micrometer Tracing + OpenTelemetry OTLP | ✅ | ✅ | Distributed tracing |
| Loki + Promtail + Grafana + Tempo + Prometheus (docker-compose) | ❌ | ✅ | Unified observability |
| Pre-built Grafana dashboards (JVM + RED) | ❌ | ✅ | Standard SRE dashboards |
| Structured JSON logging (`logstash-logback-encoder`) | ❌ | ✅ | Parseable logs at scale |
| Custom health indicators | ✅ rate-limit + audit | ❌ | Domain-specific probes |
| Trace-to-logs correlation via `traceId` | ✅ | ✅ | Cross-signal debugging |

### Persistence patterns (advanced)

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Spring Cache + Caffeine (declarative `@Cacheable`) | ✅ | ❌ | Read-through caching |
| `@Scheduled` jobs | ✅ | ❌ | Cron-like work |
| ShedLock JDBC distributed lock | ✅ | ❌ | Cluster-safe scheduling |
| `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` | ✅ | ❌ | Decoupled in-process events |
| WebSocket / STOMP push | ✅ | ❌ | Real-time push |
| Outbox pattern + message broker (Kafka/RabbitMQ) | ❌ | ❌ | Reliable event publishing |

### Testing

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Unit tests (JUnit 5 + Mockito) | ✅ | ✅ | Test isolation |
| Testcontainers integration (real DB) | ✅ Oracle Free | ✅ PostgreSQL | Production-like tests |
| WireMock for cross-service stubs | n/a | ✅ | HTTP contract simulation |
| ArchUnit layering rules | ✅ | ❌ | Architecture-as-tests |
| `@DataJpaTest` / repository slice | 🟡 | n/a | Sliced Spring tests |
| MockMvc / WebTestClient | ✅ | ✅ | HTTP-layer tests |
| Contract tests (Pact / Spring Cloud Contract) | ❌ | ❌ | Provider/consumer contracts |
| Mutation testing (PIT) | ❌ | ❌ | Test-quality gate |

### Build & ops

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| Maven (single source of truth) | ✅ | ✅ | Standard JVM build |
| Spotless / formatting | ✅ | ❌ | Style enforcement |
| Jacoco coverage report | ✅ | ❌ | Coverage as a metric |
| Spring Boot layered-jar Dockerfile | ❌ | ✅ | Container layering, cache-friendly images |
| docker-compose for the whole stack | ❌ | ✅ (full stack) | Local orchestration |
| Two run modes (host vs full Docker) | ❌ | ✅ | Dev productivity choices |
| `.env.example` + externalized secrets | ✅ | ✅ | 12-factor config |

### Frontend

| Capability | Monolith | Microservices | What it teaches |
|---|:---:|:---:|---|
| React + Vite + TypeScript + Tailwind UI | ❌ | ✅ | Modern SPA development |
| JWT refresh-on-401 axios interceptor | n/a | ✅ | Auth flow on the client |
| Client-side cart with `localStorage` | n/a | ✅ | UI state persistence |
| Toast / Context provider patterns | n/a | ✅ | React composition |

### Documentation

| Artifact | Monolith | Microservices |
|---|:---:|:---:|
| README (concise top-level) | ✅ | ✅ |
| `GETTING_STARTED.md` (newcomer guide) | ❌ | ✅ |
| `CLAUDE_ENHANCEMENTS.md` (change log) | ✅ | ✅ |
| `LEARNING_ROADMAP.md` (this file) | ✅ | ❌ |
| Dev utility scripts (`scripts/`) | ✅ | ❌ |
| Pre-built Grafana dashboards | ❌ | ✅ |

---

## What to add next — prioritized

Ranked by **learning value × industry-real-world frequency** ÷ **effort**. Top items are highest ROI.

Effort: **S** ≈ half a day · **M** ≈ 1-3 days · **L** ≈ a week+.
Priority: ⭐⭐⭐ must-do for senior-engineer fluency · ⭐⭐ strong learning · ⭐ niche but interesting.

| # | Capability | Add to | Effort | Priority | What it teaches |
|---|---|:---:|:---:|:---:|---|
| 1 | **GitHub Actions CI** — build + test + dependency scan + image push | Both | S | ⭐⭐⭐ | The single most ubiquitous practice in industry |
| 2 | **OWASP Dependency-Check + Dependabot** | Both | S | ⭐⭐⭐ | Supply-chain hygiene; CVE tracking |
| 3 | **Spring Cache + Caffeine on read-heavy endpoints** | Microservices | S | ⭐⭐⭐ | `@Cacheable`, eviction, TTL — fundamental performance pattern |
| 4 | **Custom health indicators** (DB latency, downstream service ping) | Microservices | S | ⭐⭐⭐ | Beyond default `UP`/`DOWN`; informs K8s readiness |
| 5 | **API gateway** (Spring Cloud Gateway) | Microservices | M | ⭐⭐⭐ | Single entrypoint, JWT validation centralized, routing |
| 6 | **Outbox pattern + Kafka** | Microservices | L | ⭐⭐⭐ | Reliable event publishing, eventual consistency in practice |
| 7 | **Spring Modulith verification tests** | Both | S | ⭐⭐⭐ | Modular monolith; runtime + test-time module boundaries |
| 8 | **Idempotency keys on POST endpoints** | Both | M | ⭐⭐⭐ | Safe retries in real distributed systems |
| 9 | **K8s Helm chart + deployment manifests** | Microservices | M | ⭐⭐⭐ | K8s is universal; teaches Service/Deployment/ConfigMap/Secret/Ingress |
| 10 | **JWKS endpoint + asymmetric JWT (RS256)** | Microservices (auth-service) | M | ⭐⭐⭐ | Proper public-key validation; what Auth0/Keycloak actually do |
| 11 | **Redis-backed cache + rate limiter** | Both | M | ⭐⭐ | Replaces Caffeine when you scale past 1 instance |
| 12 | **GraalVM native image** (`mvn -Pnative spring-boot:build-image`) | One service | M | ⭐⭐ | Sub-second startup, ~10× less memory; teaches AOT/reflection limits |
| 13 | **Spring REST Docs** generated from MockMvc tests | Both | S | ⭐⭐ | Always-in-sync API docs |
| 14 | **Mutation testing (PIT)** | Both | S | ⭐⭐ | Discovers tests that pass without actually testing |
| 15 | **Contract tests with Pact** | Microservices | M | ⭐⭐ | Consumer-driven contracts; how teams safely evolve APIs |
| 16 | **Hexagonal / Ports & Adapters refactor on one aggregate** | Monolith | M | ⭐⭐ | Domain-driven architecture proper |
| 17 | **CQRS (lightweight): separate read/write services** | Either | M | ⭐⭐ | Read-model materialization |
| 18 | **WireMock-recorded contract for external services** | Microservices | S | ⭐⭐ | Reusable HTTP test doubles |
| 19 | **Multi-tenant database isolation pattern** | Monolith | M | ⭐⭐ | SaaS basics |
| 20 | **OpenTelemetry sampling + tail-based traces** | Microservices | M | ⭐⭐ | Production observability cost control |
| 21 | **HashiCorp Vault for secrets** | Both | M | ⭐⭐ | Real secret management; never committed |
| 22 | **Feature flags (LaunchDarkly / Unleash)** | Both | S | ⭐⭐ | Controlled rollout, A/B testing |
| 23 | **Spring AI with Claude** for product description generation | Either | S | ⭐⭐ | Modern AI integration; tool calls, structured output |
| 24 | **WebSocket / STOMP order updates across services** | Microservices | M | ⭐ | Already in monolith; cross-service push is harder |
| 25 | **gRPC alternative for one service** | Microservices | M | ⭐ | Contract-first, binary protocol |
| 26 | **Performance testing (k6 / JMeter)** | Either | M | ⭐ | Establish SLO baselines |
| 27 | **Chaos engineering (Chaos Monkey for Spring Boot)** | Microservices | S | ⭐ | Resilience verification |
| 28 | **Querydsl or JPA Specifications** | Monolith | M | ⭐ | Type-safe dynamic queries |
| 29 | **Read replicas + DataSource routing** | Monolith | M | ⭐ | Read/write split |
| 30 | **GDPR / PII redaction layer in logs** | Both | S | ⭐ | Compliance-by-design |

---

## Recommended next 2 weeks

If I were planning a focused learning sprint, I'd pick these six in this exact order — each builds on the previous:

1. **GitHub Actions CI** on both projects — you get build + test + container image on every push. Biggest professional uplift, lowest effort.
2. **Spring Cache + Caffeine on microservices product-service** — fills the only Tier 1 fundamental still missing on the microservices side.
3. **API gateway (Spring Cloud Gateway)** in front of the 4 microservices — moves JWT validation, rate limiting, and CORS to the gateway. Cleans up each service.
4. **JWKS + RS256 JWT** in auth-service — what real OAuth providers do. Other services validate via JWKS endpoint, no shared secret.
5. **K8s Helm chart** for the microservices stack — deploy the same `docker-compose` stack to a local K8s (Kind / Minikube). Teaches Deployment / Service / Ingress / ConfigMap / Secret.
6. **Outbox pattern with Kafka** — wire an `order_outbox` table in order-service, a poller publishes events to Kafka, an auth/product/order consumer reacts. The single most important distributed-systems pattern after circuit breakers.

After those six, you've touched effectively every pattern that shows up in senior-engineer interviews and production systems.

---

## How to use this document

- Pick an item from the "What to add next" table — match the **priority + effort** to how much time you have.
- The "What it teaches" column is the actual learning artifact. If something there isn't a concept you already understand cold, do that one.
- Each item is sized to be a self-contained learning unit. You don't need to do them in order (except where dependencies are obvious — e.g., K8s Helm chart needs Dockerfiles to exist).
- When you complete an item, flip its row in the "What's implemented" tables from ❌ to ✅ and note any divergences from the standard pattern.
