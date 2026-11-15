# Unverified guardian and commerce implementation

This branch is a work checkpoint, not a release. It introduces managed participants and optional login, participant-aware booking records, service prices, wallet ledger accounts, cash/wallet checkout and cancellation settlement. Main remains the documented 0.2 preview.

Java main and existing test sources compiled during this session. The Maven test run did not complete; it stopped while resolving a Surefire dependency. No new financial or managed-student integration tests have passed. Do not deploy this branch.

Before merging: validate migration upgrade/backfill and compound constraints; add ownership, session revocation, wallet/refund, ledger immutability, idempotency and concurrent-spend tests; close pricing-update races in free booking/group creation and checkout; update API capabilities, SDK, OpenAPI, Postman and consumer examples. External card payments, subscriptions, lesson plans and files are not implemented here.
