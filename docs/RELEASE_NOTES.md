# 0.2.0 development preview

## Added

- Provider/guardian roles, provider-account invitations, own profiles, tenant user discovery and password changes that revoke all sessions.
- Tenant booking notice/horizon/signup configuration.
- Locations/rooms and shared provider/room occupancy across private bookings, sessions and blockouts.
- Provider-service assignment, guarded availability deletion and explicit blockouts.
- Atomic private rescheduling and batches; original-create replay safety after rescheduling.
- Capacity-based sessions, guarded enrolment, weekly series and whole-session cancellation.
- Database triggers for reservation/capacity consistency and V1 migration/backfill coverage.
- Transactional in-app notifications/read state and booking audit events.
- A bounded 64 KiB body filter, including bodies without Content-Length.
- Consumer SDK with TypeScript declarations, OpenAPI snapshot, Postman collection and live examples.
- Complete inventory of 273 supplied unique legacy paths, with explicit feature coverage and remaining requirements.

## Compatibility

V1 HTTP paths remain `/api/v1`. Existing booking requests may omit roomId. Booking responses add roomId and sessionId. Authentication adds PROVIDER/GUARDIAN role values. Flyway V2 migrates V1 rows and backfills existing confirmed reservations; do not edit applied migration files in an already-deployed environment.

Cookie login now rotates CSRF; consumers must bootstrap a new token after login (the SDK does this). Stale browser cookies no longer prevent credential/bootstrap requests.

The private appointment overlap constraint is replaced by a shared reservation model so sessions can accept multiple seats safely. Customer overlap enforcement remains in place. No legacy `/plm/api/v5` or `/wc/v2` aliases were registered.

## Not included

Managed dependants, instructor/student relationships, cart/payment holds, dedicated semi-private pricing/partner workflow, monetary ledger/credits, Stripe payments/payouts/refunds, app-store entitlements, curriculum/progress, uploads/waivers, OTP/password recovery, external notifications, working-hours recurrence and hosted production deployment. These must not be advertised as supported by the current release.
