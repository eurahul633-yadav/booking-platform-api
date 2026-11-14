# Validation — 0.2.0 development preview

Observed on 14 November 2026. These are correctness checks, not a production certification or a capacity/latency benchmark.

| Check | Observed result |
| --- | --- |
| Build/runtime | Java 21.0.13, Spring Boot 3.5.16, Maven 3.9.11 |
| Java verification | Maven verify: BUILD SUCCESS; 32 tests passed, no failures/errors/skips |
| API integration | 24 tests against real embedded PostgreSQL 14.19 |
| Migration upgrade | V1 database with an existing booking upgraded to V2; reservation/request fields backfilled; direct conflicting insert rejected |
| Recurrence | 2 checks for local-time preservation and DST gap/overlap rejection |
| Request body | Oversized JSON and unknown-length body checks pass |
| Redis behaviour | 4 controlled client-mock checks; not a real Redis transport/failover test |
| Consumer client | 8 Node.js fetch/cookie/error/idempotency checks pass |
| TypeScript | TypeScript 5.9.3 strict declaration/consumer compile check passes |
| Live consumer walkthrough | Passed over real local HTTP: cookie and bearer auth, provider invitation/permissions, group seats, cancellation, rescheduling/replay, batch booking, blockouts, notifications and audit |
| Original consumer walkthrough | Python example still passes over real local HTTP; create/retry/cancel and token revocation |
| API contract | OpenAPI generated from running application: 41 paths / 52 HTTP operations |
| Postman | 52 requests generated from current OpenAPI; JSON structure and operation coverage checked |
| Legacy inventory | 273 unique supplied paths represented exactly once; every mapped canonical target resolves to a real operation |
| Formatting | Google Java Format 1.28.0 applied; git diff whitespace check passes |

The live walkthroughs disable Redis request limiting/catalogue caching so they can run with the embedded database alone. They do not validate Redis transport or a public deployment. The JavaScript cookie flow is verified through Node.js 24; browser CORS/cookie policy and the CI Node.js 22 job still require their own environment verification.

## Specific failure cases exercised

- Simultaneous provider conflict, last-seat competition and same-key retries.
- Customer overlap across providers, shared-room conflicts, adjacent bookings and cancellation release.
- Atomic batch/series rollback and successful replay.
- Failed reschedule preserves the original appointment; successful reschedule retains original-create retry safety.
- Tenant/customer/provider permissions, cross-tenant resource associations and guarded availability deletion.
- Session cancellation updates all enrolled bookings and creates local notifications.
- Invitation token hashing, acceptance/replay, revocation, role restrictions and linked provider permissions.
- Password changes revoke all sessions; expired tokens and invalid bearer authentication are rejected.
- Stale/revoked browser cookies do not block credential login; login rotates CSRF and the SDK refreshes it.
- Concurrent SDK writes share one CSRF bootstrap; unknown write failures retain their idempotency key.
- Business booking horizon, public signup controls and explicit provider-service assignment.

## Not verified or not implemented

- Docker/Compose execution: no container runtime was available; Compose targets PostgreSQL 17, while integration tests use PostgreSQL 14.19.
- Live Redis transport, rate-limit behaviour across deployed replicas, failover and TLS.
- Production browser origins, Kubernetes deployment, backup/restore, penetration testing or dependency vulnerability audit.
- Load/latency targets; the load script is a template, not measured evidence.
- Financial integrations, wallets, app-store receipts, managed dependants, learning records, private file storage and external notifications: see the explicit coverage map/roadmap.
- Remote GitHub Actions outcomes are separate from the local results above.

All walkthroughs and fixtures use synthetic data. No live payment, email, SMS or push action was performed.
