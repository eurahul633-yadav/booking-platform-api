package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.PlatformModels.*;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingService {
  private final JdbcClient db;
  private final CatalogService catalog;
  private final BusinessService business;
  private final BookingService bookings;
  private final Commands commands;
  private final NotificationService notifications;

  public SchedulingService(
      JdbcClient db,
      CatalogService catalog,
      BusinessService business,
      BookingService bookings,
      Commands commands,
      NotificationService notifications) {
    this.db = db;
    this.catalog = catalog;
    this.business = business;
    this.bookings = bookings;
    this.commands = commands;
    this.notifications = notifications;
  }

  @Transactional
  public Assignment assign(Actor a, UUID provider, Assignment r) {
    a.requireAdmin();
    catalog.lockProvider(a, provider);
    for (UUID id : r.serviceIds()) catalog.service(a, id);
    db.sql("DELETE FROM provider_services WHERE tenant_id=:tenant AND provider_id=:provider")
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .update();
    for (UUID id : r.serviceIds())
      db.sql(
              "INSERT INTO provider_services(tenant_id,provider_id,service_id)"
                  + " VALUES(:tenant,:provider,:service)")
          .param("tenant", a.tenantId())
          .param("provider", provider)
          .param("service", id)
          .update();
    db.sql("UPDATE providers SET assigned_services_only=true WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", provider)
        .update();
    return r;
  }

  @Transactional
  public void deleteWindow(Actor a, UUID provider, UUID id) {
    business.requireProviderManager(a, provider);
    catalog.lockProvider(a, provider);
    var window =
        db.sql(
                "SELECT id,starts_at,ends_at FROM availability WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND id=:id")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("id", id)
            .query(
                (rs, n) ->
                    new Models.Window(
                        id, rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant()))
            .optional()
            .orElseThrow(ApiFailure::notFound);
    boolean used =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM resource_occupancy WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND starts_at<:end AND ends_at>:start AND"
                    + " kind<>'BLOCKOUT')")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("start", utc(window.startsAt()))
            .param("end", utc(window.endsAt()))
            .query(Boolean.class)
            .single();
    if (used) throw ApiFailure.conflict("Availability contains active bookings or sessions");
    db.sql("DELETE FROM availability WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
  }

  @Transactional
  public Blockout block(Actor a, UUID provider, Models.AvailabilityRequest r) {
    business.requireProviderManager(a, provider);
    catalog.lockProvider(a, provider);
    validateRange(r.startsAt(), r.endsAt(), Duration.ofDays(7));
    business.validateStart(a, r.startsAt());
    var id = UUID.randomUUID();
    business.reserve(a, id, provider, null, "BLOCKOUT", r.startsAt(), r.endsAt());
    return new Blockout(id, r.startsAt(), r.endsAt());
  }

  public List<Blockout> blocks(Actor a, UUID provider, Instant from, Instant to) {
    catalog.provider(a, provider);
    validateRange(from, to, Duration.ofDays(31));
    return db.sql(
            "SELECT id,starts_at,ends_at FROM resource_occupancy WHERE tenant_id=:tenant AND"
                + " provider_id=:provider AND kind='BLOCKOUT' AND starts_at<:end AND ends_at>:start"
                + " ORDER BY starts_at LIMIT 1000")
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("start", utc(from))
        .param("end", utc(to))
        .query(
            (rs, n) ->
                new Blockout(
                    rs.getObject(1, UUID.class),
                    rs.getTimestamp(2).toInstant(),
                    rs.getTimestamp(3).toInstant()))
        .list();
  }

  @Transactional
  public void unblock(Actor a, UUID provider, UUID id) {
    business.requireProviderManager(a, provider);
    catalog.lockProvider(a, provider);
    if (db.sql(
                "DELETE FROM resource_occupancy WHERE tenant_id=:tenant AND provider_id=:provider"
                    + " AND id=:id AND kind='BLOCKOUT'")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("id", id)
            .update()
        == 0) throw ApiFailure.notFound();
  }

  private LessonSession map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new LessonSession(
        rs.getObject("id", UUID.class),
        rs.getObject("provider_id", UUID.class),
        rs.getObject("service_id", UUID.class),
        rs.getObject("room_id", UUID.class),
        rs.getObject("series_id", UUID.class),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getTimestamp("ends_at").toInstant(),
        rs.getInt("capacity"),
        rs.getInt("booked_seats"),
        rs.getString("status"));
  }

  private static final String SELECT =
      "SELECT s.*, (SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND"
          + " b.status='CONFIRMED') AS booked_seats FROM lesson_sessions s ";

  public LessonSession session(Actor a, UUID id) {
    return db.sql(SELECT + "WHERE s.tenant_id=:tenant AND s.id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query((rs, n) -> map(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Models.Page<LessonSession> sessions(
      Actor a, Instant from, Instant to, int page, int size) {
    validateRange(from, to, Duration.ofDays(31));
    bounds(page, size);
    var rows =
        db.sql(
                SELECT
                    + "WHERE s.tenant_id=:tenant AND s.starts_at>=:start AND s.starts_at<:end ORDER"
                    + " BY s.starts_at,s.id LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("start", utc(from))
            .param("end", utc(to))
            .param("limit", size + 1)
            .param("offset", page * size)
            .query((rs, n) -> map(rs))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  @Transactional
  public LessonSession create(Actor a, SessionInput r, String key) {
    business.requireProviderManager(a, r.providerId());
    var replay = commands.find(a, "SESSION_CREATE", key, r);
    if (replay.isPresent()) return session(a, replay.get().getFirst());
    catalog.lockProvider(a, r.providerId());
    var result = insertSession(a, r, null);
    commands.save(a, "SESSION_CREATE", key, r, List.of(result.id()));
    return result;
  }

  private LessonSession insertSession(Actor a, SessionInput r, UUID series) {
    var service = catalog.service(a, r.serviceId());
    business.validateStart(a, r.startsAt());
    business.requireServiceAllowed(a, r.providerId(), r.serviceId());
    if (r.roomId() != null && business.room(a, r.roomId()).capacity() < r.capacity())
      throw ApiFailure.conflict("Session capacity exceeds room capacity");
    Instant end = r.startsAt().plus(Duration.ofMinutes(service.durationMinutes()));
    bookings.fits(a, r.providerId(), r.startsAt(), end);
    UUID id = UUID.randomUUID();
    db.sql(
            "INSERT INTO"
                + " lesson_sessions(id,tenant_id,provider_id,service_id,room_id,series_id,starts_at,ends_at,capacity,cancellation_hours,status)"
                + " VALUES(:id,:tenant,:provider,:service,:room,:series,:start,:end,:capacity,:cancel,'OPEN')")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("provider", r.providerId())
        .param("service", r.serviceId())
        .param("room", r.roomId(), java.sql.Types.OTHER)
        .param("series", series, java.sql.Types.OTHER)
        .param("start", utc(r.startsAt()))
        .param("end", utc(end))
        .param("capacity", r.capacity())
        .param("cancel", service.cancellationHours())
        .update();
    return session(a, id);
  }

  static List<Instant> weekly(Instant start, String timeZone, int count, int interval) {
    var zone = ZoneId.of(timeZone);
    var local = start.atZone(zone).toLocalDateTime();
    var result = new ArrayList<Instant>();
    for (int i = 0; i < count; i++) {
      var next = local.plusWeeks((long) i * interval);
      var offsets = zone.getRules().getValidOffsets(next);
      if (offsets.size() != 1)
        throw ApiFailure.invalid(
            "Recurring local time is ambiguous or missing during a daylight-saving transition;"
                + " choose another time");
      result.add(next.toInstant(offsets.getFirst()));
    }
    return result;
  }

  @Transactional
  public List<LessonSession> series(Actor a, SeriesInput r, String key) {
    business.requireProviderManager(a, r.providerId());
    var replay = commands.find(a, "SESSION_SERIES", key, r);
    if (replay.isPresent()) return replay.get().stream().map(id -> session(a, id)).toList();
    catalog.lockProvider(a, r.providerId());
    var provider = catalog.provider(a, r.providerId());
    var result = new ArrayList<LessonSession>();
    UUID series = UUID.randomUUID();
    for (var start : weekly(r.startsAt(), provider.timeZone(), r.occurrences(), r.intervalWeeks()))
      result.add(
          insertSession(
              a,
              new SessionInput(r.providerId(), r.serviceId(), r.roomId(), start, r.capacity()),
              series));
    commands.save(a, "SESSION_SERIES", key, r, result.stream().map(LessonSession::id).toList());
    return result;
  }

  @Transactional
  public Models.Booking enrol(Actor a, UUID id, String key) {
    var replay = commands.find(a, "SESSION_ENROL", key, Map.of("sessionId", id));
    if (replay.isPresent()) return bookings.get(a, replay.get().getFirst());
    var s = session(a, id);
    catalog.lockProvider(a, s.providerId());
    db.sql("SELECT id FROM lesson_sessions WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(UUID.class)
        .single();
    s = session(a, id);
    if (!"OPEN".equals(s.status())) throw ApiFailure.conflict("Session is cancelled");
    business.validateStart(a, s.startsAt());
    if (s.bookedSeats() >= s.capacity()) throw ApiFailure.conflict("Session is full");
    int cancellation =
        db.sql("SELECT cancellation_hours FROM lesson_sessions WHERE tenant_id=:tenant AND id=:id")
            .param("tenant", a.tenantId())
            .param("id", id)
            .query(Integer.class)
            .single();
    UUID booking = UUID.randomUUID();
    bookings.insert(
        a,
        booking,
        s.providerId(),
        s.serviceId(),
        s.roomId(),
        id,
        s.startsAt(),
        s.endsAt(),
        cancellation,
        key);
    notifications.event(a, booking, a.userId(), s.providerId(), "BOOKING_CONFIRMED");
    commands.save(a, "SESSION_ENROL", key, Map.of("sessionId", id), List.of(booking));
    return bookings.get(a, booking);
  }

  @Transactional
  public LessonSession cancelSession(Actor a, UUID id) {
    commands.lock(a);
    var s = session(a, id);
    business.requireProviderManager(a, s.providerId());
    catalog.lockProvider(a, s.providerId());
    db.sql("SELECT id FROM lesson_sessions WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(UUID.class)
        .single();
    s = session(a, id);
    if ("CANCELLED".equals(s.status())) return s;
    if (!s.startsAt().isAfter(Instant.now()))
      throw ApiFailure.conflict("Cannot cancel a started session");
    var active =
        db.sql(
                "SELECT * FROM bookings WHERE tenant_id=:tenant AND session_id=:id AND"
                    + " status='CONFIRMED' ORDER BY id FOR UPDATE")
            .param("tenant", a.tenantId())
            .param("id", id)
            .query((rs, n) -> BookingService.map(rs))
            .list();
    for (var b : active) {
      db.sql(
              "UPDATE bookings SET status='CANCELLED',cancelled_at=now() WHERE tenant_id=:tenant"
                  + " AND id=:id")
          .param("tenant", a.tenantId())
          .param("id", b.id())
          .update();
      notifications.event(a, b.id(), b.customerId(), b.providerId(), "SESSION_CANCELLED");
    }
    db.sql("UPDATE lesson_sessions SET status='CANCELLED' WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    return session(a, id);
  }
}
