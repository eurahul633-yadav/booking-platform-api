import { BookingClient } from '../sdk/index.mjs';
const { BOOKING_API_URL = 'http://localhost:8080', TENANT_SLUG, API_EMAIL, API_PASSWORD } = process.env;
if (!TENANT_SLUG || !API_EMAIL || !API_PASSWORD) throw new Error('Set TENANT_SLUG, API_EMAIL and API_PASSWORD for a development account');
const api = new BookingClient({ baseUrl: BOOKING_API_URL });
await api.login({ tenantSlug: TENANT_SLUG, email: API_EMAIL, password: API_PASSWORD });
console.log(JSON.stringify({ capabilities: await api.capabilities(), providers: await api.providers(), services: await api.services() }, null, 2));
await api.logout();
