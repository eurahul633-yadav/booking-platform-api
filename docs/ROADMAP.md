# Roadmap and release gates

The complete booking-platform scope is larger than the current booking core. Each phase must add working endpoints, migrations, OpenAPI documentation, negative tests and concurrency tests where applicable. These are planned, not implemented.

| Phase | Deliverables | Acceptance evidence |
| --- | --- | --- |
| 0.1 core | Tenant isolation, ADMIN/CUSTOMER, auth sessions/tokens, provider windows, private appointment booking, cancellation, safe retries, cache, rate limits | Real PostgreSQL API/concurrency/security tests; initial delivery |
| 0.2 scheduling | Provider login and assignments, locations/rooms, working hours, date exceptions/blockouts, buffers, minimum notice, rescheduling | Room/provider/customer conflicts; DST edge cases; atomic reschedule rollback |
| 0.3 lessons | Private/semi-private/group seat capacity, guardian/managed-student relationships, recurring series, predefined lessons and bulk booking | Last-seat race, guardian permissions, series partial-failure policy, bounded recurrence expansion |
| 0.4 payments | Payment provider port and Stripe adapter, signed/idempotent webhook inbox, booking holds with expiry, refunds/cancellation policies | Test-mode payment confirmation, duplicate/out-of-order webhooks, hold expiry race, refund reconciliation |
| 0.5 wallet/subscriptions | Immutable double-entry ledger, currency/amount handling, subscription entitlements, cash/hybrid payment policies | Balanced ledger invariant, duplicate charge protection, concurrent spend, reconciliation |
| 0.6 integrations | Transactional outbox worker, email/push/SMS ports, delivery retries, tenant webhooks, object storage, opt-in files | Retry/dead-letter visibility, signature verification, upload permissions, no external call inside transaction |
| 0.7 identity | Email verification/reset, invitations, account abuse prevention, OIDC/JWT adapter, rotating refresh families, service-account API keys | Replay detection, session fixation tests, password-reset revocation, least-privilege scopes |
| 1.0 release | Stable API spec, SDK examples, migrations/upgrade policy, permissive license, deployment guide, contribution guide | Full security/dependency review, Redis integration tests, reproducible load results, backup restore and failure drills |

Payment states must never be simulated as paid in a production-facing adapter. Group bookings require a capacity model distinct from the current exclusion of any provider overlap. Do not simply remove the overlap constraint and count rows without serialized capacity enforcement.

## Public release checklist

- Complete the intended public feature scope and label experimental modules.
- Retain the MIT license and review dependency notices for redistribution.
- Review all history and fixtures for secrets, customer records and employer code.
- Run dependency vulnerability checks, secret scanning and the full verification pipeline.
- Verify HTTPS cookies/CORS/CSRF in the actual browser deployment.
- Test Redis concurrency/failover and PostgreSQL backup/restore.
- Measure representative latency percentiles/error rate at declared load and record hardware, data volume and query plans.
- Add support policy and known limitations to the release notes.
- Change GitHub visibility only when explicitly requested; private is the development default.
