export type UUID = string;
export type Instant = string;
export interface PageQuery { page?: number; size?: number }
export interface RangeQuery { from: Instant; to: Instant }
export interface Page<T> { items: T[]; page: number; size: number; hasNext: boolean }
export interface Credentials { tenantSlug: string; email: string; password: string }
export interface WorkspaceInput { slug: string; name: string; email: string; password: string }
export interface IssuedToken { accessToken: string; tokenType: 'Bearer'; expiresIn: number }
export interface Actor { userId: UUID; tenantId: UUID; role: 'ADMIN' | 'PROVIDER' | 'CUSTOMER' | 'GUARDIAN'; sessionId: UUID }
export interface User { id: UUID; email: string; role: Actor['role']; displayName: string }
export interface Settings { bookingHorizonDays: number; minimumNoticeMinutes: number; customerSignupEnabled: boolean }
export interface ProviderInput { name: string; timeZone: string }
export interface Provider extends ProviderInput { id: UUID }
export interface ServiceInput { name: string; durationMinutes: number; cancellationHours: number }
export interface Service extends ServiceInput { id: UUID }
export interface LocationInput { name: string; address: string; timeZone: string }
export interface Location extends LocationInput { id: UUID }
export interface RoomInput { name: string; capacity: number }
export interface Room extends RoomInput { id: UUID; locationId: UUID }
export interface WindowInput { startsAt: Instant; endsAt: Instant }
export interface Window extends WindowInput { id: UUID }
export interface BookingInput { providerId: UUID; serviceId: UUID; startsAt: Instant; roomId?: UUID | null }
export interface Booking extends BookingInput { id: UUID; customerId: UUID; endsAt: Instant; cancellationHours: number; status: 'CONFIRMED' | 'CANCELLED'; sessionId: UUID | null }
export interface SessionInput extends BookingInput { capacity: number }
export interface SeriesInput extends SessionInput { occurrences: number; intervalWeeks: number }
export interface LessonSession extends SessionInput { id: UUID; endsAt: Instant; bookedSeats: number; status: 'OPEN' | 'CANCELLED'; seriesId: UUID | null }
export interface InviteInput { email: string; role: 'PROVIDER' | 'CUSTOMER' | 'GUARDIAN'; providerId?: UUID }
export interface IssuedInvite { id: UUID; token: string; expiresAt: Instant }
export interface Invite extends InviteInput { id: UUID; status: 'PENDING' | 'ACCEPTED' | 'REVOKED'; expiresAt: Instant }
export interface Notification { id: UUID; bookingId: UUID; eventType: string; createdAt: Instant; readAt: Instant | null }
export interface AuditEvent { id: UUID; bookingId: UUID; eventType: string; actorId: UUID; createdAt: Instant }
export interface Problem { status?: number; detail?: string; code?: string; errors?: { field: string; message: string }[]; reference?: string }
export class ApiError extends Error { status: number; code: string; problem: Problem; retryAfterSeconds?: number; constructor(status: number, problem: Problem, retryAfterSeconds?: number) }
export class TransportError extends Error { idempotencyKey?: string; constructor(message: string, idempotencyKey?: string) }
export function idempotencyKey(): string;
export interface ClientOptions { baseUrl: string; accessToken?: string | null; timeoutMs?: number; fetch?: typeof fetch; manageCookies?: boolean }
export interface RequestOptions { body?: unknown; query?: Record<string, string | number | boolean | undefined>; idempotencyKey?: string; signal?: AbortSignal }
export class BookingClient {
 constructor(options: ClientOptions);
 setAccessToken(token: string | null): void;
 request<T>(method: string, path: string, options?: RequestOptions): Promise<T>;
 csrf(): Promise<{ token: string; headerName: string }>;
 createWorkspace(input: WorkspaceInput): Promise<{ tenantId: UUID; userId: UUID }>;
 register(input: Credentials): Promise<{ userId: UUID }>;
 login(input: Credentials): Promise<IssuedToken>;
 loginWithCookie(input: Credentials): Promise<{ expiresIn: number }>;
 logout(allSessions?: boolean): Promise<void>;
 me(): Promise<Actor>;
 capabilities(): Promise<{ version: string; features: string[]; legacyRouteAliases: false; externalPaymentsEnabled: false }>;
 settings(): Promise<Settings>;
 updateSettings(input: Settings): Promise<Settings>;
 profile(): Promise<User>;
 updateProfile(input: { displayName: string }): Promise<User>;
 changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>;
 users(query?: PageQuery): Promise<Page<User>>;
 providers(query?: PageQuery): Promise<Provider[]>;
 createProvider(input: ProviderInput): Promise<Provider>;
 services(query?: PageQuery): Promise<Service[]>;
 createService(input: ServiceInput): Promise<Service>;
 assignServices(providerId: UUID, serviceIds: UUID[]): Promise<{ serviceIds: UUID[] }>;
 locations(query?: PageQuery): Promise<Location[]>;
 createLocation(input: LocationInput): Promise<Location>;
 updateLocation(id: UUID, input: LocationInput): Promise<Location>;
 rooms(locationId: UUID, query?: PageQuery): Promise<Room[]>;
 createRoom(locationId: UUID, input: RoomInput): Promise<Room>;
 availability(providerId: UUID, query: RangeQuery): Promise<Window[]>;
 publishAvailability(providerId: UUID, input: WindowInput): Promise<Window>;
 deleteAvailability(providerId: UUID, id: UUID): Promise<void>;
 slots(providerId: UUID, query: RangeQuery & { serviceId: UUID }): Promise<Instant[]>;
 blockouts(providerId: UUID, query: RangeQuery): Promise<Window[]>;
 createBlockout(providerId: UUID, input: WindowInput): Promise<Window>;
 deleteBlockout(providerId: UUID, id: UUID): Promise<void>;
 bookings(query?: PageQuery): Promise<Page<Booking>>;
 booking(id: UUID): Promise<Booking>;
 createBooking(input: BookingInput, key: string): Promise<Booking>;
 bookBatch(inputs: BookingInput[], key: string): Promise<Booking[]>;
 reschedule(id: UUID, startsAt: Instant, key: string): Promise<Booking>;
 cancelBooking(id: UUID): Promise<Booking>;
 sessions(query: RangeQuery & PageQuery): Promise<Page<LessonSession>>;
 session(id: UUID): Promise<LessonSession>;
 createSession(input: SessionInput, key: string): Promise<LessonSession>;
 createSessionSeries(input: SeriesInput, key: string): Promise<LessonSession[]>;
 enrol(sessionId: UUID, key: string): Promise<Booking>;
 cancelSession(id: UUID): Promise<LessonSession>;
 invite(input: InviteInput): Promise<IssuedInvite>;
 invitations(query?: PageQuery): Promise<Page<Invite>>;
 acceptInvitation(input: { token: string; password: string; displayName: string }): Promise<{ userId: UUID }>;
 revokeInvitation(id: UUID): Promise<void>;
 notifications(query?: PageQuery): Promise<Page<Notification>>;
 markNotificationRead(id: UUID): Promise<void>;
 bookingEvents(query?: PageQuery): Promise<Page<AuditEvent>>;
}
