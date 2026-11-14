# Architecture

## Boundary

A modular monolith with one deployable API and PostgreSQL as the source of truth. Redis holds expendable catalogue cache and distributed request counters. There is no frontend, payment processor, queue worker or Kubernetes deployment in 0.1.

| Component | Responsibility |
| --- | --- |
| `AuthController`, `SessionService` | Workspace/customer signup, credential verification, token/session lifecycle |
| `SecurityConfig`, `RateLimitFilter` | Authentication, cookie CSRF, CORS, distributed request budgets |
| `CatalogService` | Tenant-scoped providers/services, provider window validation, catalogue cache |
| `BookingService` | Booking transaction, idempotency, cancellation, slot discovery, audit event |
| `BookingController` | Validated HTTP input/output |
| `Errors` | Sanitized failures and Problem Details |
| Flyway SQL | Relational invariants, constraints and indexes |

Classes currently share one base package. Split by feature into auth, tenancy, catalogue, scheduling, payments and notifications as the modules grow; do not begin with distributed services merely to present a larger architecture.

## Booking transaction

1. Lock the authenticated customer's row, serializing their retries and changes that compete on the same idempotency namespace.
2. Look up the tenant/customer/key. Return the existing booking if provider, service and exact start match; reject a mismatched payload.
3. Lock the tenant-scoped provider row; read the service duration and cancellation policy.
4. Validate the future range and containment in a published availability window.
5. Insert the booking. PostgreSQL GiST exclusion constraints reject overlapping confirmed provider/customer ranges. Ranges are half open `[start,end)` so an appointment can start when the previous one ends.
6. Insert the booking event in the same transaction, then commit.

Row locks reduce contention races in application flows, while database exclusion constraints remain the final invariant even if another future code path writes bookings. A 409 rolls back the whole transaction. Provider UUIDs are globally unique; composite tenant/resource foreign keys prohibit cross-tenant associations.

## Availability and time

Version 0.1 stores explicit absolute windows. IANA provider zones are display/recurrence metadata; slot calculations operate on instants. Clients must convert local time with the intended offset and resolve ambiguous/nonexistent DST times before sending. Recurring local-time schedules require an explicit DST policy in a later module.

Slot suggestions are anchored at each window's start and advance by service duration. Direct booking accepts any start contained by a window; it is not restricted to suggested grid points. All tenant services are currently bookable with all tenant providers. Provider-to-service assignment is a planned constraint.

A window cannot overlap another window for its provider. Adjacent windows remain distinct; an appointment must fit within one window. Availability deletion/editing and blockouts are not exposed yet, avoiding undefined effects on confirmed appointments.

## Security model

A user belongs to one tenant and has ADMIN or CUSTOMER role. The ADMIN can configure the catalogue and see tenant bookings; CUSTOMER sees only their own. Neither can read another tenant. Workspace registration creates the first ADMIN; customer registration cannot select a role.

Opaque random tokens contain 256 bits of entropy, are stored only as SHA-256 hashes, and expire server-side. A database lookup per authenticated request provides immediate revocation and current roles without a JWT revocation layer. Session kinds are distinct: a browser token cannot be used as a bearer token and vice versa. BCrypt cost 12 is used for passwords. Passwords are limited to 72 UTF-8 bytes to avoid BCrypt truncation.

## Cache consistency

Only service-list catalogue reads are cached, keyed by tenant, page and page size. Entries expire after 30 seconds; newly created services may take that long to appear in a cached list. Booking and service-detail validation reads PostgreSQL directly. Availability, authentication and booking records are never cached. Cache read/write failures fall back to PostgreSQL. Request limiting fails closed with 503 if Redis is unavailable.

## Scaling path

Multiple API replicas share PostgreSQL and Redis; there is no in-process session or local lock. Connection budgets must account for replica count. Provider locks serialize one provider's competing writes; measure this before replacing them. Use an outbox and bounded worker pool for notifications/payment effects; do not call external services inside booking transactions. The existing booking_events table is an audit log, **not** a delivery outbox: it has no leases, retries or acknowledgement state.

Before large datasets, add keyset pagination, bounded availability materialization, retention/partitioning of audit data, and query-plan/load tests. Horizontal scaling and index design do not establish a capacity or latency guarantee.
