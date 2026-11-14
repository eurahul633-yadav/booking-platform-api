# Validation — 14 November 2026

This records observed checks for the initial booking core; it is not a production certification or performance benchmark.

| Check | Observed result |
| --- | --- |
| JDK | Eclipse Temurin 21.0.13 |
| Framework | Spring Boot 3.5.16 |
| Build tool | Maven 3.9.11; generated Maven wrapper also starts successfully |
| Maven verify | BUILD SUCCESS; executable Spring Boot JAR produced |
| API/database tests | 11 passed, 0 failures/errors/skips, real PostgreSQL 14.19 |
| Redis behaviour tests | 4 passed, 0 failures/errors/skips, mocked Redis client |
| Python example | Passed over real local HTTP with PostgreSQL; Redis limiting/cache disabled for this walkthrough |
| Example outcome | Workspace/customer created; slots discovered; booking created; retry returned original ID; cancellation completed; tokens revoked |
| Java formatting | Google Java Format 1.28.0 applied |

## Covered behaviours

- Two competing customers cannot book the same provider/time.
- Concurrent retries with the same key return the same booking.
- Reusing a key with a changed payload returns 409.
- A customer cannot have overlapping appointments with different providers.
- Adjacent appointments work and cancellation releases availability.
- Tenant, customer ownership and ADMIN permissions are enforced.
- Cancellation deadlines and input bounds are enforced.
- Tokens are hashed, expire and can be revoked individually/all at once.
- Browser session cookies require CSRF for writes; the real CSRF cookie/header bootstrap is exercised.
- Invalid bearer authentication never falls back to an otherwise valid cookie.
- Slot discovery excludes confirmed provider appointments.
- OpenAPI distinguishes public credential endpoints from authenticated booking endpoints.
- Rate limits return 429 with Retry-After and fail closed when Redis is unavailable.
- Cached catalogue keys include tenant and pagination; cache failure falls back to database access.

## Not verified here

- Docker/Compose execution: no container runtime was available. Compose targets PostgreSQL 17; integration tests used the embedded PostgreSQL 14.19 distribution.
- Real Redis transport, multi-replica rate-limit behaviour and failover.
- Kubernetes deployment, managed database/Redis TLS and production browser origins.
- Capacity/latency/load targets, external security audit, vulnerability scan or backup/restore drill.
- Planned payment, wallet, subscription, recurring/group lesson and notification modules.
- GitHub Actions execution is separate from this local verification; review the repository Actions tab after publication.

No real user data or employer source code is included in fixtures.
