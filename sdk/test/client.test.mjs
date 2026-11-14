import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BookingClient, ApiError, TransportError } from '../index.mjs';
const reply = (body, status = 200, headers = {}) => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });

test('commands preserve keys, tokens and room metadata', async () => {
 let call;
 const client = new BookingClient({ baseUrl: 'http://localhost:8080', accessToken: 'secret-token', fetch: async (url, init) => { call = { url, init }; return reply({ id: 'booking' }); } });
 assert.equal((await client.createBooking({ providerId: 'p', serviceId: 's', startsAt: '2027-01-01T10:00:00Z', roomId: 'r' }, 'stable-key-123')).id, 'booking');
 assert.equal(call.url.pathname, '/api/v1/bookings'); assert.equal(call.init.headers['Idempotency-Key'], 'stable-key-123');
 assert.equal(call.init.headers.Authorization, 'Bearer secret-token'); assert.equal(JSON.parse(call.init.body).roomId, 'r');
 assert.throws(() => client.createBooking({}, ''), /idempotency key/);
});

test('node client bootstraps and retains CSRF cookie for credential POST', async () => {
 const calls = [];
 const client = new BookingClient({ baseUrl: 'http://localhost:8080', fetch: async (url, init) => {
  calls.push({ url, init });
  return url.pathname.endsWith('/csrf') ? reply({ token: 'csrf', headerName: 'X-XSRF-TOKEN' }, 200, { 'Set-Cookie': 'XSRF-TOKEN=csrf; Path=/; SameSite=Strict' }) : reply({ accessToken: 'access', tokenType: 'Bearer', expiresIn: 3600 });
 } });
 await client.login({ tenantSlug: 'demo', email: 'owner@example.test', password: 'long-password' });
 assert.equal(calls.length, 2); assert.equal(calls[1].init.headers.Cookie, 'XSRF-TOKEN=csrf'); assert.equal(calls[1].init.headers['X-XSRF-TOKEN'], 'csrf');
 await client.me(); assert.equal(calls[2].init.headers.Authorization, 'Bearer access');
});

test('secure cookies are not sent over HTTP', async () => {
 const calls = [];
 const client = new BookingClient({ baseUrl: 'http://localhost:8080', fetch: async (url, init) => {
  calls.push(init); return url.pathname.endsWith('/csrf') ? reply({ token: 'csrf' }, 200, { 'Set-Cookie': 'XSRF-TOKEN=csrf; Secure; Path=/' }) : reply({ accessToken: 'ok' });
 } });
 await client.login({ tenantSlug: 'demo', email: 'a@example.test', password: 'password' });
 assert.equal(calls[1].headers.Cookie, undefined);
});

test('problem details and Retry-After reach consumers without retrying writes', async () => {
 let calls = 0;
 const client = new BookingClient({ baseUrl: 'https://example.test', accessToken: 'token', fetch: async () => { calls++; return reply({ code: 'RATE_LIMITED', detail: 'Too many requests' }, 429, { 'Retry-After': '60' }); } });
 await assert.rejects(client.createBooking({}, 'retry-once-key'), error => error instanceof ApiError && error.status === 429 && error.retryAfterSeconds === 60);
 assert.equal(calls, 1);
});

test('unknown write outcomes expose the key for safe retry', async () => {
 const client = new BookingClient({ baseUrl: 'https://example.test', accessToken: 'token', fetch: async () => { throw new Error('offline'); } });
 await assert.rejects(client.createBooking({}, 'unknown-outcome'), error => error instanceof TransportError && error.idempotencyKey === 'unknown-outcome');
});

test('query values are encoded and empty responses are accepted', async () => {
 let url;
 const client = new BookingClient({ baseUrl: 'https://example.test', accessToken: 'token', fetch: async (u) => { url = u; return reply(undefined, 204); } });
 assert.equal(await client.request('GET', '/sessions', { query: { from: '2027-01-01T10:00:00+02:00' } }), undefined);
 assert.equal(url.searchParams.get('from'), '2027-01-01T10:00:00+02:00');
});


test('cookie login clears cached CSRF and bootstraps a new token before the next write', async () => {
 let bootstraps=0;const calls=[];
 const client=new BookingClient({baseUrl:'http://localhost:8080',fetch:async (url,init)=>{
  calls.push({url,init});
  if(url.pathname.endsWith('/csrf')) {bootstraps++;return reply({token:`csrf-${bootstraps}`},200,{'Set-Cookie':`XSRF-TOKEN=csrf-${bootstraps}; Path=/`});}
  if(url.pathname.endsWith('/login')) {const response=reply({expiresIn:28800},200,{'Set-Cookie':'BOOKING_SESSION=session; Path=/; Max-Age=28800'});response.headers.append('Set-Cookie','XSRF-TOKEN=; Path=/; Max-Age=0');return response;}
  assert.equal(init.headers['X-XSRF-TOKEN'],'csrf-2');assert.ok(init.headers.Cookie.includes('BOOKING_SESSION=session'));return reply({id:'booking'});
 }});
 await client.loginWithCookie({tenantSlug:'demo',email:'customer@example.test',password:'password'});
 await client.createBooking({},'rotated-csrf-key');
 assert.equal(bootstraps,2);
});


test('concurrent unauthenticated writes share one CSRF bootstrap', async () => {
 let bootstraps=0;
 const client=new BookingClient({baseUrl:'http://localhost:8080',fetch:async (url,init)=>{
  if(url.pathname.endsWith('/csrf')) {bootstraps++;await new Promise(resolve=>setTimeout(resolve,5));return reply({token:'shared-csrf'},200,{'Set-Cookie':'XSRF-TOKEN=shared-csrf; Path=/'});}
  assert.equal(init.headers['X-XSRF-TOKEN'],'shared-csrf');return reply({userId:'user'},201);
 }});
 await Promise.all([client.register({tenantSlug:'demo',email:'one@example.test',password:'password'}),client.register({tenantSlug:'demo',email:'two@example.test',password:'password'})]);
 assert.equal(bootstraps,1);
});
