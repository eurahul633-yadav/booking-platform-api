# Consumer interface

## Contract and scope

Use `/api/v1`; do not call the legacy `/bookora/api/v5` names. The supplied names are a requirements inventory, not a compatible wire contract. OpenAPI and the Postman collection contain actual implemented endpoints. `GET /api/v1/capabilities` reports enabled feature groups to an authenticated consumer. This backend must be run/deployed before an app can consume it.

The current scheduling flows do not charge money. Do not treat CONFIRMED as evidence of payment. There are no payment/wallet/subscription endpoints returning simulated financial success.

## Authentication

For initial signup or credential requests, obtain `GET /auth/csrf` first. Preserve its XSRF-TOKEN cookie, refresh it after cookie login, and send the returned token in X-XSRF-TOKEN for unsafe requests. The JavaScript client and Python example handle this.

- `POST /auth/token`: `{tenantSlug,email,password}` → opaque bearer token, 1-hour expiry. Send `Authorization: Bearer <token>` on subsequent requests.
- `POST /auth/login`: same input → HttpOnly browser session cookie, 8-hour expiry. Send credentials/cookies and CSRF on unsafe requests. Login rotates the CSRF cookie; bootstrap a fresh token after successful cookie login before the next write.
- `POST /auth/logout`: revoke current token/session.
- `POST /auth/logout-all`: revoke all sessions for this tenant user.
- `POST /me/password`: `{currentPassword,newPassword}`; on success every session is revoked and the user must log in again.

A valid bearer request does not need CSRF. Invalid bearer authentication never falls back to a cookie. No refresh-token or JWT/OIDC flow is implemented yet. Passwords must be at least 12 characters and at most 72 UTF-8 bytes. Cookie clients must be same-site with the API (SameSite=Strict); cross-site/native clients should use bearer authentication.

## Permissions

| Role | Access |
| --- | --- |
| ADMIN | Configure business/catalogue, invite accounts, manage all tenant providers/sessions, read all tenant bookings/users/audit events |
| PROVIDER | Manage availability, blockouts and sessions for the provider linked to that account; view that provider's bookings and own customer bookings |
| CUSTOMER | Read tenant catalogue/availability/sessions; create/manage own bookings and read own notifications/profile |
| GUARDIAN | Same current access as CUSTOMER; dependant profiles and booking on behalf of them remain planned |

Tenant identity comes from authentication, never from a client tenant header. Inaccessible bookings return 404. Providers are linked through an ADMIN-issued invitation, not by accepting a user-supplied provider ID at login.

Public customer registration is enabled by default. Disable it using PUT `/business/settings`; invited customer/provider/guardian acceptance remains available. Invitations expire in seven days, store hashed tokens, and expose the raw token only in the create response. The consuming application delivers that token securely to its intended recipient. Email/SMS delivery is not implemented by this release. Tokens must be submitted in the acceptance POST body, not placed in logs or query strings.

## Canonical resources

| Interface | Main operations |
| --- | --- |
| `/business/settings` | GET/PUT bookingHorizonDays (1–730), minimumNoticeMinutes (0–43200), customerSignupEnabled |
| `/providers`, `/services` | GET paginated catalogue arrays; POST creates |
| `/providers/{id}/services` | PUT `{serviceIds:[...]}`; switches provider to explicit assignment mode, including an empty list that permits no services |
| `/locations` | GET list, POST create; PUT `/locations/{id}` updates location details |
| `/locations/{id}/rooms` | GET list; POST `{name,capacity}` |
| `/providers/{id}/availability` | GET by from/to; POST `{startsAt,endsAt}` |
| `/providers/{id}/availability/{window}` | DELETE only if no active booking/session uses the window |
| `/providers/{id}/blockouts` | GET by from/to; POST `{startsAt,endsAt}`; DELETE child blockout resource |
| `/providers/{id}/slots` | GET by serviceId/from/to; private appointment suggestions |
| `/bookings` | GET scoped page; POST private booking |
| `/bookings/{id}` | GET scoped booking |
| `/bookings/{id}/cancel` | POST repeat-safe cancellation |
| `/bookings/{id}/reschedule` | POST `{startsAt}` for confirmed private appointments |
| `/bookings/batch` | POST `{bookings:[...]}`; max 50; all succeed or none commit |
| `/sessions` | GET by from/to and page/size; POST scheduled session |
| `/sessions/{id}` | GET capacity/bookedSeats/status |
| `/sessions/{id}/enrolments` | POST customer enrolment; returns a booking |
| `/sessions/{id}/cancel` | POST by manager; atomically cancels its bookings and releases resources |
| `/session-series` | POST bounded weekly scheduled-session series |
| `/me/profile` | GET/PUT displayName |
| `/users`, `/users/{id}` | ADMIN user discovery; a user can read their own record |
| `/invitations` | ADMIN POST/GET; POST child `/revoke` |
| `/auth/invitations/accept` | Public POST `{token,password,displayName}` |
| `/notifications` | GET own page; POST child `/read` |
| `/booking-events` | ADMIN GET transactional audit page |

Full field schemas and response shapes are in [OpenAPI](openapi.json). UUIDs identify resources. No delete/edit handler is implied by an item appearing in the legacy inventory.

## Private booking

POST `/bookings` with `Idempotency-Key`:

```json
{
  "providerId": "<uuid>",
  "serviceId": "<uuid>",
  "roomId": "<uuid-or-null>",
  "startsAt": "2026-12-01T09:00:00Z"
}
```

Duration and cancellation policy come from the service, not the client. A booking must fit within one availability window and satisfy business notice/horizon settings. Provider, room and customer conflicts return 409. Provider/service assignment is enforced when explicit assignment mode is enabled. By default, a provider with no configured assignment restriction can offer all tenant services.

Room reservation is optional. There is no provider-to-location membership restriction yet. Slots are suggestions, not holds: a later write can return 409. The current private slot grid is anchored at the availability start and steps by service duration; direct bookings may start at any instant that fits.

## Scheduled groups and recurrence

POST `/sessions` with `{providerId,serviceId,roomId?,startsAt,capacity}`. Managers publish a session inside provider availability. Capacity is 1–100 and cannot exceed room capacity. The provider/room are reserved once, even with multiple enrolled customers. Enrolment locks and database constraints protect the last seat and prevent one customer holding two active seats in the same session.

POST `/session-series` with those fields plus `{occurrences,intervalWeeks}`. Occurrences are 1–26; intervalWeeks is 1–12. Recurrence keeps the provider's local wall time through normal DST offset changes. Missing or ambiguous local times reject the entire series. Every occurrence must fit availability, business policy and resource constraints. A failed occurrence rolls back the entire expansion.

There is no split/edit recurring series endpoint. Cancel individual sessions through their IDs. Recurring availability and blockout expansion remain planned. Individual group bookings cannot be rescheduled directly: cancel and enrol in another session. That is two separate operations, not a guaranteed atomic seat transfer.

Customer cancellation honours the snapshotted service deadline. Whole-session cancellation is a manager operation that can cancel a future session and all seats, regardless of each customer's cancellation deadline. Once a session has started it cannot be cancelled through this endpoint. There are no refund effects in this release.

## Idempotency and unknown outcomes

Require a stable key of 8–100 allowed characters (`A-Z a-z 0-9 . _ : -`) for private booking, batch booking, rescheduling, session creation/series and enrolment. Keys are actor/tenant/operation scoped, with private/enrolment booking keys additionally unique within the customer's booking records. Use a fresh key for each intended action and retain it with the exact body for retries.

- Same operation/key/body returns the original resource IDs and their current state.
- Changed body with a used key returns 409.
- Successful creation replays return 201; reschedule replays return 200.
- Replaying a create after rescheduling returns the same booking with its new time.
- Replaying after cancellation returns the cancelled resource, rather than making a new one.
- Failed transactions retain no command result; the same request can be retried after correcting external availability/contention conditions.

A timeout or AbortSignal cancellation does not undo a committed server transaction. Never generate a new key merely because a response was lost. No automatic write retry is built into the client.

## Dates, pagination and errors

Send ISO 8601 instants/offset timestamps with at most microsecond precision. Data is stored as `timestamptz`. Clients format times in the provider/location/user's chosen IANA zone. Recurring local-time policy is described above; single absolute instants are unambiguous.

Lists accept page 0–10000 and size 1–100. User, invitation, booking, session, notification and audit lists return `{items,page,size,hasNext}`. The original provider/service lists and location/room lists return arrays. Session/availability query windows are bounded to 31 days; private slot queries to seven days; individual availability and blockout spans to seven days.

| Status | Meaning | Client response |
| --- | --- | --- |
| 400 | Invalid input, time policy, range, precision or key | Show field/policy feedback |
| 401 | Missing/expired/invalid credentials or token | Sign in again |
| 403 | Permission or CSRF problem | Check role and bootstrap/refresh CSRF; do not loop blindly |
| 404 | Missing or inaccessible record | Show unavailable |
| 409 | Overlap, full session, duplicate data/key mismatch or deadline | Refresh relevant data; let user resolve |
| 413 | Body exceeds 64 KiB, including unknown-length bodies | Reduce the request |
| 429 | Request budget exhausted | Respect Retry-After |
| 503 | Dependency or contention failure | Back off; preserve the command key |
| 500 | Unexpected error | Keep the returned reference; avoid exposing internals |

Application failures use Problem Details with a stable code, detail and optional field errors/reference. Framework-level malformed input responses may omit the custom code; clients must also handle HTTP status. UI applications own loading indicators, pending button state, cancellation and accessible error messages.

## Postman

Import `booking-platform.postman_collection.json`. Set baseUrl, credentials, tenant slug and relevant resource IDs. Run the CSRF request first; Postman stores its cookie and the request test saves csrfToken. Token login saves accessToken automatically. Public credential routes use no bearer authorization and include the CSRF header. Authenticated routes use the collection's token.

Update idempotencyKey for each new intended command, preserving it for retries of that same command. Initial future time variables are generated if empty; clear/update them when reusing the collection later. Example payloads are synthetic and may need IDs/values from earlier responses. This collection is an exploration tool, not a full automated platform provisioning script; use the executable walkthrough for that.

## Remaining contract inputs

To implement the remaining legacy features faithfully, provide sanitized bodies/responses, error cases and permissions for: managed student/guardian links; instructor relationships; carts/semi-private booking; lesson plans/progress; Stripe fee/refund/payout rules; wallet credit/debit/expiry; app-store subscription lifecycle; and private documents/waivers. Endpoint names alone do not establish these rules.
