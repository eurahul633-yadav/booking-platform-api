# Architecture

## Modules

A single Spring Boot deployable with explicit service boundaries. PostgreSQL is the source of truth. Redis holds expendable catalogue cache and distributed request counters. The persistence adapter is Spring JDBC; no JPA/Hibernate is used.

| Component | Responsibility |
| --- | --- |
| SessionService / SecurityConfig | Authentication, token/session revocation, CSRF, CORS and filter chain |
| IdentityService | Profiles, password changes, users and provider/customer/guardian invitations |
| BusinessService | Tenant policy, locations/rooms, ownership and service-assignment checks |
| CatalogService | Provider/service catalogue and explicit availability |
| BookingService / Commands | Private appointments, atomic batches, rescheduling, scoped command idempotency |
| SchedulingService | Shared-capacity sessions, weekly expansion, enrolment, blockouts and guarded window removal |
| NotificationService | Transactional booking events and local notification records |
| RequestBodyLimitFilter / RateLimitFilter | Bounded body allocation and shared request budgets |
| SDK / OpenAPI / Postman | Consumer interface, schemas and examples |

Classes currently share one base package. Package/module extraction can follow these service boundaries as the next domains grow. External payment and delivery workers are not part of this release.

## Resource and seat invariants

`resource_occupancy` has one row per confirmed private booking, open group session or blockout. GiST exclusion constraints reject overlapping provider ranges and overlapping non-null room ranges. Half-open `[start,end)` ranges allow adjacent appointments.

Flyway V2 backfills occupancy for V1 confirmed appointments. Database triggers synchronize private-booking/session reservations on inserts and updates, so another internal writer cannot accidentally bypass resource conflicts by omitting an explicit Java reservation call. Customer range exclusions still apply to every confirmed booking, including group enrolments. The session enrolment trigger locks the session, checks capacity and verifies that each enrolment matches its session's resources and times.

A private booking is one customer/one resource reservation. A group session is one reservation with multiple customer bookings. Cancellation of one group booking releases a seat but retains the shared reservation. Cancelling the whole session updates every active booking, records notifications/events and releases resources in one transaction.

## Transaction and locking

Commands lock the actor row for a stable per-user idempotency namespace. Scheduling writes then lock the provider; enrolment also locks the session. Multi-provider private batches pre-lock distinct providers in sorted UUID order. Booking cancellation/rescheduling use the same actor/provider order before locking the booking. Database constraints remain the final invariant for different providers competing for one room.

A rejected overlap/capacity/customer constraint rolls back all writes, including audit/notification records. Lock or statement timeouts/deadlocks return retryable failures where classified by Spring. They do not leave partially booked batches or series.

Rescheduling updates the current booking while retaining original request fields for safe late create retries. Command request hashes are derived from validated canonical records; replay checks the body before returning existing resource IDs. Command records are not a payment idempotency ledger and should not be reused as one.

## Permissions and tenancy

Every service scopes resources to the authenticated tenant. Composite tenant/resource foreign keys prevent cross-tenant associations. ADMIN controls business configuration and catalogue. PROVIDER accounts are linked to one provider by an accepted hashed-token invitation; they manage only that provider's scheduling resources and see its bookings. CUSTOMER/GUARDIAN currently manage only their own bookings. Guarded user/profile endpoints do not expose password/token hashes.

Custom opaque-token authentication uses explicit session handling, rather than Spring's automatic session authentication strategy. Cookie login rotates CSRF explicitly; bootstrap and credential endpoints ignore stale browser authentication cookies while still requiring CSRF for unsafe requests. Invalid bearer headers remain rejected.

A user belongs to one tenant. Provider/account relationships are distinct from future instructor/student or guardian/dependant relationships. Public signup is configurable. Invitation acceptance creates a new user; merging an invitation into an existing account is not implemented.

## Time and recurrence

Single bookings/windows are explicit instants. Weekly group-session expansion derives provider-local date/time, expands by whole weeks and resolves each date using IANA zone rules. A gap or overlap rejects the whole expansion. All occurrences must fit published windows and current policy. Series expansion is bounded; there is no unbounded recurrence generation.

Availability remains explicit rather than a working-hours rule engine. Slot suggestions use a duration-based grid. Provider/location assignment, buffer times, recurring blockouts and working-hour exceptions are planned extensions.

## Cache, events and scaling

Only service-list reads are cached for 30 seconds, keyed by tenant/page/size. Booking, identity and availability/capacity checks use PostgreSQL. New catalogue entries may take 30 seconds to appear in a cached list. Cache failure falls back to PostgreSQL; the Redis-backed request limiter fails closed.

Audit events and local notifications commit alongside booking state. There is no claim that these records were delivered externally: no email, SMS or push request is sent. A future transactional outbox needs delivery state, leases, retries, backoff and dead-letter handling before adding integrations.

Multiple replicas can share PostgreSQL/Redis without local sessions/locks. Capacity, latency and fairness must still be measured. Provider-level serialization trades throughput for simple correctness; query plans, contention tests and connection budgets should guide later changes. These mechanisms are not proof of a particular production scale.
