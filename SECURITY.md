# Security

This is an early portfolio release. No external audit or production-readiness claim is made.

Please report vulnerabilities privately through the repository owner's preferred private contact or GitHub private vulnerability reporting when enabled. Do not include usable credentials or customer data in public issues.

Implemented protections include BCrypt password storage, opaque token hashing/expiry/revocation, separate browser/bearer session types, CSRF for cookie requests, exact-origin credentialed CORS, a 64 KiB request-body bound, tenant/customer/provider authorization and database-level resource/capacity constraints. No default admin credentials are shipped.

Before an internet-facing deployment, complete email verification/password reset, signup abuse controls, ingress connection/time limits, trusted-proxy rate handling, TLS/secrets management, dependency checks and operational recovery testing. Public signup permits anyone who knows a tenant slug to register as CUSTOMER by default; ADMIN can disable it through business settings and issue invitations instead. Customer credentials are tenant-local; accounts are not automatically linked across businesses.

Session cookies are host-only, HttpOnly, SameSite=Strict and Secure by default. Local `COOKIE_SECURE=false` is for localhost only. Tokens and passwords must never be logged. Bearer tokens require no browser CSRF token but must be kept out of URLs and persistent unprotected storage. Current tokens do not have a refresh flow; reauthenticate after expiry.

A future wallet/payment implementation needs separate threat modelling, webhook signature verification, idempotent reconciliation and immutable ledger tests before handling money.
