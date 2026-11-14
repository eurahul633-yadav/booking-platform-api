export class ApiError extends Error {
  constructor(status, problem, retryAfterSeconds) {
    super(problem.detail || `API request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code || 'HTTP_ERROR';
    this.problem = problem;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
export class TransportError extends Error {
  constructor(message, idempotencyKey) {
    super(message);
    this.name = 'TransportError';
    this.idempotencyKey = idempotencyKey;
  }
}
export function idempotencyKey() { return crypto.randomUUID(); }

export class BookingClient {
  #base; #fetch; #timeout; #token; #csrf = null; #csrfFlight = null; #cookies = new Map(); #manageCookies;
  constructor({ baseUrl, accessToken = null, timeoutMs = 15000, fetch: fetchImpl = globalThis.fetch,
    manageCookies = typeof process !== 'undefined' && !!process.versions?.node }) {
    const url = new URL(baseUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new TypeError('baseUrl must be an HTTP(S) API base URL without credentials, query or fragment');
    }
    if (!Number.isFinite(timeoutMs) || timeoutMs < 1) throw new TypeError('timeoutMs must be positive');
    this.#base = url.href.replace(/\/+$/, ''); this.#fetch = fetchImpl; this.#timeout = timeoutMs;
    this.#token = accessToken; this.#manageCookies = manageCookies;
  }
  setAccessToken(token) { this.#token = token; }
  async csrf() {
    if (this.#csrfFlight) return this.#csrfFlight;
    this.#csrfFlight = this.request('GET', '/auth/csrf').then(result => { this.#csrf = result.token; return result; }).finally(() => { this.#csrfFlight = null; });
    return this.#csrfFlight;
  }
  async request(method, path, { body, query, idempotencyKey: key, signal } = {}) {
    if (!path.startsWith('/') || path.startsWith('//') || path.includes('?') || path.includes('#')) throw new TypeError('Use a relative API path and the query option');
    const url = new URL(`${this.#base}/api/v1${path}`);
    for (const [name, value] of Object.entries(query || {})) if (value !== undefined && value !== null) url.searchParams.set(name, String(value));
    const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase());
    if (unsafe && !this.#token && typeof document !== 'undefined' && typeof location !== 'undefined' && location.hostname === url.hostname) {
      const cookie = document.cookie.split('; ').find(value => value.startsWith('XSRF-TOKEN='));
      this.#csrf = cookie ? decodeURIComponent(cookie.slice(11)) : null;
    }
    if (unsafe && !this.#token && !this.#csrf) await this.csrf();
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (this.#token) headers.Authorization = `Bearer ${this.#token}`;
    else if (unsafe) headers['X-XSRF-TOKEN'] = this.#csrf;
    if (key) headers['Idempotency-Key'] = key;
    if (this.#manageCookies) {
      const now = Date.now();
      const cookies = [...this.#cookies].filter(([, c]) => c.expires > now && (!c.secure || url.protocol === 'https:'));
      if (cookies.length) headers.Cookie = cookies.map(([name, c]) => `${name}=${c.value}`).join('; ');
    }
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (signal?.aborted) abort();
    signal?.addEventListener('abort', abort, { once: true });
    const timeout = setTimeout(abort, this.#timeout);
    try {
      const response = await this.#fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal, credentials: 'include', cache: 'no-store', redirect: 'error' });
      if (this.#manageCookies && typeof response.headers.getSetCookie === 'function') {
        for (const raw of response.headers.getSetCookie()) {
          const [pair, ...attributes] = raw.split(';'); const split = pair.indexOf('=');
          const name = pair.slice(0, split).trim(), value = pair.slice(split + 1);
          if (!['BOOKING_SESSION', 'XSRF-TOKEN'].includes(name)) continue;
          let expires = Infinity, secure = false;
          for (const attribute of attributes) {
            const [label, ...values] = attribute.trim().split('=');
            if (label.toLowerCase() === 'secure') secure = true;
            if (label.toLowerCase() === 'max-age') expires = Date.now() + Number(values.join('=')) * 1000;
          }
          if (expires <= Date.now()) {
            this.#cookies.delete(name); if (name === 'XSRF-TOKEN') this.#csrf = null;
          } else {
            this.#cookies.set(name, { value, expires, secure }); if (name === 'XSRF-TOKEN') this.#csrf = value;
          }
        }
      }
      const text = await response.text(); let result;
      try { result = text ? JSON.parse(text) : undefined; }
      catch { throw new ApiError(response.status, { code: 'INVALID_RESPONSE', detail: 'The API returned invalid JSON' }); }
      if (!response.ok) {
        const retry = response.headers.get('Retry-After');
        const seconds = retry === null ? undefined : /^\d+$/.test(retry) ? Number(retry) : Math.max(0, (Date.parse(retry) - Date.now()) / 1000);
        throw new ApiError(response.status, result || {}, seconds);
      }
      return result;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new TransportError(controller.signal.aborted ? 'Request timed out or was cancelled; the write outcome may be unknown' : 'Unable to reach the API; the write outcome may be unknown', key);
    } finally { clearTimeout(timeout); signal?.removeEventListener('abort', abort); }
  }
  #command(path, body, key) {
    if (!key || !/^[A-Za-z0-9._:-]{8,100}$/.test(key)) throw new TypeError('Supply a stable 8–100 character idempotency key');
    return this.request('POST', path, { body, idempotencyKey: key });
  }
  createWorkspace(body) { return this.request('POST', '/auth/workspaces', { body }); }
  register(body) { return this.request('POST', '/auth/register', { body }); }
  async login(body) {
    this.#token = null;
    const issued = await this.request('POST', '/auth/token', { body }); this.#token = issued.accessToken; return issued;
  }
  async loginWithCookie(body) { this.#token = null; const result = await this.request('POST', '/auth/login', { body }); this.#csrf = null; return result; }
  async logout(allSessions = false) {
    await this.request('POST', allSessions ? '/auth/logout-all' : '/auth/logout'); this.#token = null; this.#cookies.delete('BOOKING_SESSION');
  }
  me() { return this.request('GET', '/auth/me'); }
  capabilities() { return this.request('GET', '/capabilities'); }
  settings() { return this.request('GET', '/business/settings'); }
  updateSettings(body) { return this.request('PUT', '/business/settings', { body }); }
  profile() { return this.request('GET', '/me/profile'); }
  updateProfile(body) { return this.request('PUT', '/me/profile', { body }); }
  async changePassword(body) { await this.request('POST', '/me/password', { body }); this.#token = null; this.#cookies.delete('BOOKING_SESSION'); }
  users(query) { return this.request('GET', '/users', { query }); }
  providers(query) { return this.request('GET', '/providers', { query }); }
  createProvider(body) { return this.request('POST', '/providers', { body }); }
  services(query) { return this.request('GET', '/services', { query }); }
  createService(body) { return this.request('POST', '/services', { body }); }
  assignServices(providerId, serviceIds) { return this.request('PUT', `/providers/${providerId}/services`, { body: { serviceIds } }); }
  locations(query) { return this.request('GET', '/locations', { query }); }
  createLocation(body) { return this.request('POST', '/locations', { body }); }
  updateLocation(id, body) { return this.request('PUT', `/locations/${id}`, { body }); }
  rooms(locationId, query) { return this.request('GET', `/locations/${locationId}/rooms`, { query }); }
  createRoom(locationId, body) { return this.request('POST', `/locations/${locationId}/rooms`, { body }); }
  availability(providerId, query) { return this.request('GET', `/providers/${providerId}/availability`, { query }); }
  publishAvailability(providerId, body) { return this.request('POST', `/providers/${providerId}/availability`, { body }); }
  deleteAvailability(providerId, id) { return this.request('DELETE', `/providers/${providerId}/availability/${id}`); }
  slots(providerId, query) { return this.request('GET', `/providers/${providerId}/slots`, { query }); }
  blockouts(providerId, query) { return this.request('GET', `/providers/${providerId}/blockouts`, { query }); }
  createBlockout(providerId, body) { return this.request('POST', `/providers/${providerId}/blockouts`, { body }); }
  deleteBlockout(providerId, id) { return this.request('DELETE', `/providers/${providerId}/blockouts/${id}`); }
  bookings(query) { return this.request('GET', '/bookings', { query }); }
  booking(id) { return this.request('GET', `/bookings/${id}`); }
  createBooking(body, key) { return this.#command('/bookings', body, key); }
  bookBatch(bookings, key) { return this.#command('/bookings/batch', { bookings }, key); }
  reschedule(id, startsAt, key) { return this.#command(`/bookings/${id}/reschedule`, { startsAt }, key); }
  cancelBooking(id) { return this.request('POST', `/bookings/${id}/cancel`); }
  sessions(query) { return this.request('GET', '/sessions', { query }); }
  session(id) { return this.request('GET', `/sessions/${id}`); }
  createSession(body, key) { return this.#command('/sessions', body, key); }
  createSessionSeries(body, key) { return this.#command('/session-series', body, key); }
  enrol(sessionId, key) { return this.#command(`/sessions/${sessionId}/enrolments`, undefined, key); }
  cancelSession(id) { return this.request('POST', `/sessions/${id}/cancel`); }
  invite(body) { return this.request('POST', '/invitations', { body }); }
  invitations(query) { return this.request('GET', '/invitations', { query }); }
  acceptInvitation(body) { return this.request('POST', '/auth/invitations/accept', { body }); }
  revokeInvitation(id) { return this.request('POST', `/invitations/${id}/revoke`); }
  notifications(query) { return this.request('GET', '/notifications', { query }); }
  markNotificationRead(id) { return this.request('POST', `/notifications/${id}/read`); }
  bookingEvents(query) { return this.request('GET', '/booking-events', { query }); }
}
