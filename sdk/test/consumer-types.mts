import { BookingClient, ApiError, idempotencyKey, type Booking, type LessonSession } from '@rahul-yadav/booking-client';
const api = new BookingClient({ baseUrl: 'http://localhost:8080' });
async function consume(): Promise<{ booking: Booking; session: LessonSession }> {
  await api.login({ tenantSlug: 'demo', email: 'customer@example.test', password: 'a-long-example-password' });
  const providers = await api.providers({ page: 0, size: 10 });
  const services = await api.services();
  const starts = await api.slots(providers[0].id, { serviceId: services[0].id, from: '2027-01-01T09:00:00Z', to: '2027-01-02T09:00:00Z' });
  const booking = await api.createBooking({ providerId: providers[0].id, serviceId: services[0].id, startsAt: starts[0] }, idempotencyKey());
  const sessions = await api.sessions({ from: starts[0], to: '2027-01-02T09:00:00Z' });
  return { booking, session: sessions.items[0] };
}
function describe(error: unknown): string { return error instanceof ApiError ? error.code : 'UNKNOWN'; }
void consume; void describe;
