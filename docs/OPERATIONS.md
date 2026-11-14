# Operations

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| DATABASE_URL | jdbc:postgresql://localhost:5432/booking | JDBC PostgreSQL URL |
| DATABASE_USER | booking | DB user |
| DATABASE_PASSWORD | required | Secret; do not commit |
| DATABASE_POOL_SIZE | 10 | Per-replica connection limit |
| REDIS_HOST / REDIS_PORT | localhost / 6379 | Redis endpoint |
| REDIS_PASSWORD | empty | Redis authentication where configured |
| COOKIE_SECURE | true | Secure session and CSRF cookies |
| ALLOWED_ORIGINS | http://localhost:3000 | Comma-separated exact frontend origins |
| API_DOCS_ENABLED | false | Exposes Swagger and API schema |
| RATE_LIMIT_ENABLED | true | Redis distributed limits |
| CACHE_ENABLED | true | Catalogue cache only |

Compose supplies insecure local cookies and enabled docs for local development and binds published ports to loopback. Do not treat this Compose configuration as a production deployment. Use managed secrets, HTTPS, private authenticated/TLS PostgreSQL/Redis connections and separate migration privileges in deployment. Flyway currently runs at startup; preinstall `btree_gist` or grant extension creation rights to a dedicated migration role. Runtime JDBC only requires application table privileges once migrations are applied.

## Request budgets

Rate limits use atomic Redis increment/expire counters shared by replicas: 15 auth POSTs per minute per direct remote address and 300 other API requests per minute per direct remote address. These are separate budgets. Boundaries are fixed 60-second windows starting at the first request; `Retry-After: 60` is conservative. CSRF GETs use the normal API budget.

The service ignores forwarded headers. Behind a load balancer all users may appear to share the proxy address. Configure a trusted ingress to enforce per-client limits and connection/body timeouts before production. Do not blindly trust arbitrary X-Forwarded-For from the internet. Email/account-specific abuse protection is still planned.

The API enforces a 64 KiB request-body cap for unsafe API requests, including unknown-length bodies, before JSON parsing. Also enforce bounded request duration/connections at ingress; the Tomcat form cap alone does not bound JSON. Configure TLS and CORS for the final deployment; a separate registrable domain requires bearer authentication in this release.

## Database and contention

The pool is capped at 10 connections by default. Every pooled connection sets a five-second PostgreSQL statement timeout. Long waits result in a retryable 503 where Spring classifies the timeout/lock failure. Do not retry forever. Keep the same command idempotency key and exact body when a response is unknown. Session cleanup runs hourly and is safe on multiple replicas; large session tables need batched retention work later.

Preserve the exclusion constraints on PostgreSQL. H2/SQLite substitutes cannot validate the same concurrency semantics. Availability/scheduling writes and audit inserts commit together where relevant.

## Health and telemetry

Public health responses hide dependency details. `/actuator/health/liveness` and `/actuator/health/readiness` are available; configure the orchestrator's readiness group to include `db,redis` if desired. Default readiness state alone does not guarantee dependency readiness. Other actuator endpoints require authentication and are not exposed by configuration.

Spring Boot records HTTP metrics internally, but a metrics export backend, distributed tracing and dashboards are not configured. Unexpected application errors carry a random reference and log a server stack trace. Do not enable request body/token/password logging. No performance result is claimed until measured with representative data.

## Load test template

`performance/booking.js` is a k6 template for competing unique booking attempts. Use a disposable fixture with a future available start time and a valid short-lived token. It intentionally produces 409 conflicts after the first booking. Record successful and conflicting operations separately; this is a contention test, not a general throughput benchmark. Set up distinct providers/customers and realistic datasets for throughput testing.

## Recovery

Back up PostgreSQL and validate restore on an isolated instance. Redis holds no booking truth; losing it clears rate limits and cache entries. A Redis outage currently rejects API traffic because the distributed limiter fails closed, even though catalogue cache itself falls back. Restore Redis connectivity before re-enabling traffic. Never disable overlap constraints or expose internal database/Redis ports as an outage workaround.
