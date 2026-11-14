# Booking Platform API

A reusable Java 21 / Spring Boot backend for building booking applications. Businesses configure providers, services, locations, rooms, availability and scheduled sessions through the API; web and mobile clients consume a versioned HTTP contract.

**Release: 0.2.0 development preview.** This is an independent implementation with synthetic examples. The repository remains private. It is not a complete recreation of every supplied legacy feature and has no claimed production capacity or latency target.

## Implemented

| Area | Working functionality |
| --- | --- |
| Identity | Workspace owner/customer signup, provider/guardian invitations, profiles, password changes, tenant user listing |
| Security | ADMIN/PROVIDER/CUSTOMER/GUARDIAN permissions, hashed expiring bearer/browser sessions, immediate revocation, CSRF, exact-origin CORS, 64 KiB request-body limit |
| Business configuration | Booking horizon, minimum notice, open/invite-only customer registration |
| Catalogue | Providers, services, provider-service assignment, locations, location updates and rooms |
| Scheduling | Explicit availability, guarded availability removal, blockouts, slot discovery |
| Private appointments | Booking, cancellation deadlines, atomic rescheduling, atomic batches of up to 50 bookings, request idempotency |
| Scheduled sessions | Capacity-based sessions, concurrent seat allocation, weekly series of up to 26 occurrences, whole-session cancellation |
| Operations | Transactional booking audit events, in-app notifications/read state, Redis request limits and short-lived catalogue cache |
| Consumer tools | OpenAPI snapshot/Swagger, Postman collection, JavaScript client with TypeScript declarations, executable examples, Docker and CI |

Group sessions support 1–100 seats. The dedicated semi-private partner/invitation/pricing workflow is still planned. GUARDIAN accounts currently have customer permissions; managed dependants and booking on their behalf are not implemented. Payments, wallets, app-store subscriptions, lesson-plan/progress records and external notifications remain planned.

The [coverage map](docs/ENDPOINT_COVERAGE.md) preserves all **273 supplied unique legacy paths** and maps their feature areas to the current interface. The old paths are not registered aliases; payload compatibility and full behavioural parity have not been established from endpoint names alone.

## Quick start

Install Docker with Compose:

```sh
git clone https://github.com/eurahul633-yadav/booking-platform-api.git
cd booking-platform-api
cp .env.example .env
# Set DATABASE_PASSWORD in .env. Do not commit it.
docker compose up --build
```

- API: http://localhost:8080/api/v1
- Swagger: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

The Compose configuration is for localhost. It enables documentation and disables Secure cookies for HTTP development. Deployment defaults require Secure cookies and disable documentation. The project supplies source and local runtime configuration; it is not a hosted API service.

For Java development, start `docker compose up -d postgres redis`, export `DATABASE_PASSWORD`, and run:

```sh
COOKIE_SECURE=false API_DOCS_ENABLED=true ./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` with a JDK 21 and set environment variables through PowerShell.

## Build an application against the API

1. Create a workspace with `POST /api/v1/auth/workspaces`; the initial user is ADMIN.
2. Authenticate with a bearer token or browser session. The client handles the CSRF bootstrap.
3. Create providers and services. Optionally restrict providers to assigned services.
4. Create locations/rooms and publish provider availability.
5. Register customers or issue invitations. Invite provider accounts to let instructors manage their own schedules.
6. Discover slots and create private bookings, or publish scheduled sessions and let customers enrol.
7. Display booking lists and in-app notifications; use cancellation/rescheduling endpoints for changes.

See the [consumer guide](docs/CONSUMER_GUIDE.md) for requests, roles, time handling, errors and retry rules. The [Postman collection](docs/booking-platform.postman_collection.json) and [OpenAPI snapshot](docs/openapi.json) describe working routes only.

### JavaScript / TypeScript client

Install the local package with `npm install /path/to/booking-platform-api/sdk` in your application. It is not published to npm.

```ts
import { BookingClient, idempotencyKey } from '@rahul-yadav/booking-client';

const api = new BookingClient({ baseUrl: 'http://localhost:8080' });
await api.login({ tenantSlug: 'demo', email: 'customer@example.test', password: 'your-password' });
const slots = await api.slots(providerId, { serviceId, from, to });
const key = idempotencyKey(); // Retain this key for this intended booking.
const booking = await api.createBooking({ providerId, serviceId, startsAt: slots[0] }, key);
```

The client uses fetch, keeps credentials in memory, handles CSRF/cookies, exposes structured errors and requires explicit idempotency keys for booking commands. It never automatically retries a write. Read the [client guide](sdk/README.md) before using it on a shared application server.

## Run examples

With the local API running:

```sh
python3 examples/walkthrough.py
node examples/platform-walkthrough.mjs
```

The second walkthrough exercises provider invitations/permissions, cookie authentication, shared group capacity, session cancellation, private booking/rescheduling/replay, batch booking, blockouts and notifications. Both create synthetic development records. Do not point them at a production database.

For an existing development account:

```sh
TENANT_SLUG=demo API_EMAIL=owner@example.test API_PASSWORD='your-password' node examples/consumer.mjs
```

## Verify

```sh
./mvnw verify
npm ci --prefix sdk
npm run check:types --prefix sdk
node --test sdk/test/*.test.mjs
```

Java tests use real embedded PostgreSQL binaries downloaded from Maven Central. Run as a non-root user on a supported Linux/macOS architecture with JDK 21. Redis cache/limiter tests use controlled client mocks; a live Redis integration/failover suite is still required before deployment. GitHub Actions runs Java and client checks in separate jobs.

See [validation evidence](docs/VALIDATION.md) for observed results and remaining environment checks. Docker, production load and external payment integrations are not represented as verified by unit/API tests.

## Design and extension

- PostgreSQL is the source of booking truth. Exclusion constraints prevent overlapping provider/room reservations and customer appointments.
- A group session reserves resources once; bookings consume its seat capacity. Database triggers preserve resource/capacity invariants, while application transactions and locks implement retry-safe commands.
- Spring JDBC keeps tenant predicates, locks and transactional writes explicit. JPA/Hibernate is not currently used.
- Redis caches only service lists for 30 seconds. Authentication, availability, capacity and booking validation use PostgreSQL.
- Flyway V2 upgrades the original V1 schema and backfills reservations for existing confirmed bookings.

Read [architecture](docs/ARCHITECTURE.md), [operations](docs/OPERATIONS.md), [security](SECURITY.md), [roadmap](docs/ROADMAP.md) and [release notes](docs/RELEASE_NOTES.md).

## License

[MIT](LICENSE). The repository remains private during development. Public release follows the roadmap and explicit publication approval.
