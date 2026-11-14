import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 5, iterations: 20 };
export default function () {
  const response = http.post(`${__ENV.BASE_URL}/api/v1/bookings`, JSON.stringify({
    providerId: __ENV.PROVIDER_ID,
    serviceId: __ENV.SERVICE_ID,
    startsAt: __ENV.STARTS_AT,
  }), { headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`,
    'Idempotency-Key': `load-${__VU}-${__ITER}-${Date.now()}`,
  }, tags: { operation: 'booking-contention' } });
  check(response, { 'created or conflict': (r) => r.status === 201 || r.status === 409 });
}
