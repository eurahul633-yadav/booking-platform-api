# Roadmap and remaining contracts

## Delivered

| Release | Working scope |
| --- | --- |
| 0.1 | Tenant isolation, owner/customer authentication, catalogue, explicit availability, private booking/cancellation, basic cache/limits and OpenAPI |
| 0.2 | Provider accounts/invitations, profiles/password changes, settings, locations/rooms, provider-service assignment, blockouts, guarded window removal, atomic rescheduling/batches, capacity-safe sessions/weekly series, in-app notifications/audit, SDK/Postman and legacy inventory |

The complete [273-path inventory](ENDPOINT_COVERAGE.md) records feature references. AVAILABLE means a related canonical operation exists, not that old requests or full legacy semantics are reproduced. PARTIAL and PLANNED rows remain outstanding.

## Next implementation work

| Domain | Required implementation / decisions | Evidence required |
| --- | --- | --- |
| Guardians and managed students | Distinguish login users from dependants; guardian authority, adult managed access, invitations and booking on behalf | Relationship permissions, attendee conflicts, guardian revocation and access tests |
| Instructor relationships | Requests, approval/decline/relink, location memberships, contact visibility and notes | State transitions, ownership, duplicate requests and relationship removal |
| Availability lifecycle | Local working hours, date exceptions, recurring blockouts, buffers, provider/location restrictions, schedule edit/split and archival | DST cases, existing booking effects, concurrent edits |
| Semi-private/cart checkout | Partner invitations, seat pricing, persisted carts, multi-lesson holds, expiry and atomic checkout semantics | Last-seat/hold-expiry races, partial failure rules |
| Payments and orders | Provider interface and Stripe adapter; charge ownership, fees, connected accounts, signed webhooks, refunds and payout schedules | Test-mode integrations, duplicate/out-of-order webhooks and reconciliation |
| Wallets/credits | Immutable balanced ledger, currencies/amounts, funding sources, credit expiry and settlement | Balanced ledger, concurrent spending, idempotent adjustments and refunds |
| Subscriptions | Apple/Google/Stripe lifecycle, verified receipts, renewals, refunds and entitlements | Signed notifications, replay handling, revocation and reconciliation |
| Learning records | Lesson plans/categories, instructor/student assignments, progress/skill reports, read state and completion | Role visibility, editing/version policy and assignment ownership |
| Files and waivers | Private storage, allowed sizes/types, signed access, signatures and retention/deletion policy | Access controls, file validation and lifecycle tests |
| External communication | Transactional outbox, email/SMS/push ports, preferences and notification areas | Retry/backoff, delivery state/dead letters and user opt-out |
| Identity recovery | Verification, OTP, password recovery, account merge/deletion, refresh families and service-account keys | Anti-enumeration, replay/revocation and abuse controls |
| Reporting | Date filters, attendance/payment/fee summaries and dashboards | Query correctness, query plans and bounded pagination |

Endpoint names cannot establish charge/refund rules, guardian authority, payload shapes or provider credentials. Add sanitized request/response examples and permission/business rules before declaring legacy parity.

## Public release gates

Keep the repository private until publication is explicitly requested. Complete the intended public scope, preserve the MIT license, review dependency notices/history/fixtures, run dependency and secret checks, verify HTTPS/CORS/CSRF in the real browser deployment, test Redis transport/failover and backup restore, and measure representative latency/error rates with stated hardware/data/load. Add a support and API compatibility policy before a stable public release.
