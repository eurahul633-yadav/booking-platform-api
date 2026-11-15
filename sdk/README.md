# Booking client

Dependency-free JavaScript client with TypeScript declarations for the implemented BookingCore API. Supports modern browsers and Node.js 22+. This package is private and has not been published to npm.

Install from a local checkout using `npm install /path/to/booking-platform-api/sdk`, or import `./sdk/index.mjs` in a Node application. Browser applications should use their normal bundler.

```ts
import { BookingClient, idempotencyKey, ApiError } from '@rahul-yadav/booking-client';

const api = new BookingClient({ baseUrl: 'http://localhost:8080' });
await api.login({ tenantSlug: 'demo', email: 'customer@example.test', password: 'your-password' });
const available = await api.slots(providerId, { serviceId, from, to });
const key = idempotencyKey(); // Retain this key for this intended booking.
try {
  const booking = await api.createBooking({ providerId, serviceId, startsAt: available[0] }, key);
} catch (error) {
  if (error instanceof ApiError && error.status === 409) {
    // Refresh availability and let the user choose another slot.
  }
  throw error;
}
```

Every method corresponds to a working canonical API endpoint. `request<T>` supports additional endpoints without waiting for a wrapper method. It uses paths relative to `/api/v1`, e.g. `request('GET', '/users/{id}')`.

The client bootstraps CSRF before unauthenticated/cookie-authenticated writes. Browser cookies use the browser cookie store. Node.js retains only this API's two host-only cookies in memory and respects Secure and Max-Age; no generic third-party cookie jar is exposed. Set `manageCookies=false` when an injected fetch implementation manages cookies itself. Use a separate client instance per user on application servers; never share one authenticated instance between users.

Access tokens remain in the client instance, with no localStorage persistence. The SDK does not perform automatic write retries. Preserve the same key and body when retrying an unknown write outcome. `TransportError.idempotencyKey` carries the request key; `ApiError` exposes status, code, problem details and Retry-After seconds. A timeout/cancelled request may already have committed on the server.

Raw `request` supports an AbortSignal. All calls have a configurable timeout (15 seconds by default), no-store fetch mode and redirect rejection. Exact CORS origins must be configured on the API; cookies across different sites are not supported by the server's SameSite=Strict policy.

Run client checks with `node --test sdk/test/*.test.mjs` from the repository root. To check the TypeScript declarations and consumer example, run `npm ci --prefix sdk` and `npm run check:types --prefix sdk`. TypeScript is a development-only dependency. These checks use controlled fetch responses. The separate `examples/consumer.mjs` example is intended for testing against a running API.
