# Booking Platform API

A reusable Java 21 / Spring Boot booking backend for web and mobile applications. Built by Rahul Yadav as an independent portfolio project inspired by appointment scheduling experience. This repository contains original implementation and synthetic examples, with no former employer source code, credentials, branding, or customer data.

**Status: 0.1 booking core.** Private development repository; public release is planned after the roadmap and release review. This is a working core, not a complete implementation of every feature in a commercial booking platform. No production traffic, latency, or capacity numbers are claimed.

## What works

- Workspaces (tenants), owner registration, customer registration, ADMIN/CUSTOMER permissions.
- Provider catalogue, service duration/cancellation policy, explicit UTC availability windows and provider IANA time zones.
- Slot discovery, booking creation, booking detail/list, cancellation deadlines and repeat-safe cancellation.
- Provider and customer overlap protection enforced by PostgreSQL exclusion constraints; adjacent appointments are allowed.
- Idempotent booking requests, including concurrent retries with the same key. Reusing a key with another payload returns 409.
- Expiring opaque bearer tokens (1 hour), browser HttpOnly cookies (8 hours), CSRF validation, current-session and all-session revocation.
- BCrypt password hashes, SHA-256 token hashes, tenant-scoped database queries and composite foreign keys.
- Redis distributed request limits, optional service catalogue cache with 30-second TTL and database fallback.
- Validation, RFC 9457 Problem Details, bounded list pagination, Flyway schema migrations, transactional booking audit events.
- OpenAPI/Swagger, health probes, Docker Compose and GitHub Actions verification.

## Run locally

Install Docker with Compose. Copy the sample configuration and set a local database password:

```sh
cp .env.example .env
# Edit DATABASE_PASSWORD in .env; never commit .env.
docker compose up --build
```

API: http://localhost:8080. Swagger: http://localhost:8080/swagger-ui/index.html. OpenAPI JSON: http://localhost:8080/v3/api-docs. Health: http://localhost:8080/actuator/health.

For Java development, start only dependencies and run with a JDK 21:

```sh
docker compose up -d postgres redis
# Export DATABASE_PASSWORD from your local .env, then:
COOKIE_SECURE=false API_DOCS_ENABLED=true ./mvnw spring-boot:run
```

PowerShell: use `mvnw.cmd` and set the same environment variables with `$env:NAME="value"`. Never set `COOKIE_SECURE=false` on a deployed HTTPS environment.

Run the complete synthetic walkthrough:

```sh
python3 examples/walkthrough.py
```

It creates a uniquely named workspace and customer, authenticates, creates a provider/service/window, discovers slots, books, retries the booking, and cancels it. Run only against your development database; it creates persistent test records.

## Test

```sh
./mvnw verify
```

Tests start a real embedded PostgreSQL process using binaries resolved from Maven Central. Docker is not required for the test database. Run as a non-root user on a supported Linux/macOS architecture. JDK 21, process execution permission and dependency download access are required. Integration tests cover concurrent booking conflicts, same-key races, tenant/customer authorization, cancellation deadlines, CSRF, cookie revocation, token expiry and OpenAPI generation. Cache/rate-limit behaviour is separately exercised with Redis client mocks; Redis transport and deployment behaviour require environment testing.

## API conventions

All application endpoints use `/api/v1`. Authentication determines the tenant; clients cannot choose a tenant with a header. Identifiers use UUIDs. Timestamps use ISO 8601 UTC/offset values, stored as PostgreSQL `timestamptz`; at most microsecond precision. Treat displayed slots as suggestions: a booking may return 409 if someone else books first.

| Method | Path | Access / behaviour |
| --- | --- | --- |
| GET | `/auth/csrf` | Public; returns token and sets CSRF cookie |
| POST | `/auth/workspaces` | Public; creates tenant and its ADMIN |
| POST | `/auth/register` | Public; registers CUSTOMER in an existing tenant |
| POST | `/auth/token` | Credentials → opaque bearer access token |
| POST | `/auth/login` | Credentials → HttpOnly browser session cookie |
| GET | `/auth/me` | Current user/tenant/role/session identifiers |
| POST | `/auth/logout` | Revokes current session |
| POST | `/auth/logout-all` | Revokes all sessions for this tenant user |
| GET, POST | `/providers` | Read: tenant users; create: ADMIN |
| GET, POST | `/services` | Read: tenant users; create: ADMIN |
| GET, POST | `/providers/{id}/availability` | Read: tenant users; create: ADMIN |
| GET | `/providers/{id}/slots` | `serviceId`, `from`, `to`; max 7 days |
| GET, POST | `/bookings` | Scoped list / idempotent create |
| GET | `/bookings/{id}` | Booking owner or tenant ADMIN |
| POST | `/bookings/{id}/cancel` | Owner or ADMIN; cancellation deadline applies |

Paths above are relative to `/api/v1`. Lists accept `page=0&size=20`, size 1–100. Booking list returns `{items,page,size,hasNext}`; catalogue lists return arrays. Availability queries allow 31 days; individual windows may span at most 7 days. Booking starts must be within 366 days.

### Browser cookie authentication

1. `GET /auth/csrf` with credentials included. Retain the returned token and cookies.
2. POST credentials to `/auth/login` with `X-XSRF-TOKEN` set to that token and cookies included.
3. Include `credentials: 'include'` on subsequent requests and `X-XSRF-TOKEN` on unsafe requests.
4. On 401, return the user to sign-in. On 403, distinguish authorization failure from a missing/expired CSRF cookie by retrieving a new CSRF token and checking role; do not loop automatically.

Cookies use HttpOnly for the session, SameSite=Strict, host-only scope and Secure by default. Configure exact allowed frontend origins. This cookie flow is intended for same-site applications; use bearer auth for native apps or cross-site clients. Initial credential/token/signup POSTs also require the CSRF cookie/header pair; the example handles this automatically.

### Bearer clients

Obtain an access token using the CSRF bootstrap and `/auth/token`, then send `Authorization: Bearer <accessToken>`. Valid bearer authentication does not require CSRF. An invalid bearer header is rejected and never falls back to a browser session. Keep tokens in memory or OS secure storage. Logout revokes tokens immediately. Token expiry requires sign-in again; refresh-token rotation, JWT/OIDC and API service-account keys are future modules.

### Booking and retries

```http
POST /api/v1/bookings
Authorization: Bearer <accessToken>
Idempotency-Key: booking-request-123
Content-Type: application/json

{"providerId":"<uuid>","serviceId":"<uuid>","startsAt":"2026-12-01T09:00:00Z"}
```

Reuse the **same key and payload** after an unknown result/network timeout. Successful replay returns the original booking (201), including its current state if later cancelled. Keys remain associated with the booking; there is no TTL. Use a new key for a new intended booking.

| Status | Meaning | Client behaviour |
| --- | --- | --- |
| 400 | Invalid fields, time range, pagination or key | Show actionable field feedback |
| 401 | Missing/invalid/expired authentication | Sign in again |
| 403 | Permission or CSRF failure | Check role and CSRF state |
| 404 | Missing or inaccessible resource | Show unavailable; no tenant details disclosed |
| 409 | Overlap, availability, duplicate data, key mismatch or cancellation deadline | Refresh availability or correct request |
| 429 | Request limit | Respect `Retry-After` |
| 503 | Dependency/lock timeout | Back off; preserve booking idempotency key |
| 500 | Unexpected error | Report returned reference; preserve retry key |

The backend returns JSON; client applications own spinners/loading state, cancelable fetches, pending-button disabling and displaying errors. A client timeout is not proof that a booking failed.

## Design and extension

See [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), [operations](docs/OPERATIONS.md), [security](SECURITY.md) and [validation](docs/VALIDATION.md).

The initial persistence layer uses Spring JDBC deliberately: SQL locks, tenant predicates and overlap constraints are visible and testable. It does not currently use JPA/Hibernate. The HTTP contract is independent of this choice; a JPA adapter can be introduced with equivalent transaction/concurrency tests.

## License and publication

The implementation is provided under the [MIT license](LICENSE), so others can use and adapt it when you publish it. Keep the repository private during development. Before public release, review dependencies and credentials, complete the release gates in the roadmap, and add release notes. Licensing does not change repository visibility.

## Get the source

The repository is private during development. With access to the repository:

```sh
git clone https://github.com/eurahul633-yadav/booking-platform-api.git
cd booking-platform-api
```

Then follow the local setup instructions above. Public release will follow the roadmap and release checks.
