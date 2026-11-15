# Bookora source review

The supplied PHP archive contains 10 source files, 46,992 lines and 446 function declarations. It is used as a business-rule reference for an independent Java implementation. PHP code, uploaded archives and credentials are not committed to this repository.

## Confirmed rules

- A managed student belongs to a guardian. The source supports profiles without a login and a separate operation to enable or remove login access.
- Wallet operations for a managed student resolve to the guardian account. The source stores separate balances per instructor; location/platform credits are separate concerns.
- Cash and wallet balances interact in the source. Some branches represent cash debt as a negative wallet balance. The Java design will distinguish spendable credit from cash outstanding, calculate prices on the server and preserve an immutable transaction history.
- Cancellation, booking status and wallet changes are separate legacy operations. The Java implementation needs atomic booking/payment updates and idempotent refunds limited to amounts actually settled.
- The archive contains subscription, lesson-plan, file, location and scheduling handlers. These still require detailed rule mapping and implementation.

## Trace points

| Concern | Sanitized reference |
| --- | --- |
| Wallet ownership and adjustment | `common_api.php`: `v5_getcoachwalletamount`, `v5_updatecoachwalletamount` |
| Managed profile creation and login removal | `api.php`: managed-student creation/removal handlers |
| Booking cancellation | `studentModuleApi.php`: cancellation handler |
| Android subscription and wallet history variants | `test_apis.php` |

These trace points establish observed behavior, not a claim of complete compatibility. The reference calls external CMS, appointment, payment and subscription services whose full runtime configuration and schema are not included.

## Sanitization

The reference copy uses Bookora branding, nonfunctional credential placeholders and reserved example domains. Personal email addresses, internal links and macOS resource-fork metadata were removed or replaced. Automated credential detection is a review aid and does not prove that every possible secret format has been found.

The reference ZIP is documentation material, not a deployable or executable component of Bookora. Production credentials must be supplied through environment configuration or a secret manager.

## Implementation status

The main branch remains the documented 0.2 development preview. Managed-student and wallet/checkout work is under development and is not represented as a verified release. External card payments, app-store subscriptions, lesson-plan/progress records and file storage remain planned.
