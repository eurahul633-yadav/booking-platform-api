package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.Models.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private final JdbcClient db;
  private final CatalogService catalog;
  private final BusinessService business;
  private final Commands commands;
  private final NotificationService notifications;

  public BookingService(
      JdbcClient db,
      CatalogService catalog,
      BusinessService business,
      Commands commands,
      NotificationService notifications) {
    this.db = db;
    this.catalog = catalog;
    this.business = business;
    this.commands = commands;
    this.notifications = notifications;
  }

  static Booking map(ResultSet rs) throws SQLException {
    return new Booking(
        rs.getObject("id", UUID.class),
        rs.getObject("provider_id", UUID.class),
        rs.getObject("service_id", UUID.class),
        rs.getObject("customer_id", UUID.class),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getTimestamp("ends_at").toInstant(),
        rs.getString("status"),
        rs.getInt("cancellation_hours"),
        rs.getObject("room_id", UUID.class),
        rs.getObject("session_id", UUID.class));
  }

  @Transactional
  public Booking create(Actor a, BookingRequest r, String key) {
    Commands.key(key);
    commands.lock(a);
    // Original request fields survive rescheduling, so a late create retry never creates another
    // booking.
    var old =
        db.sql(
                "SELECT * FROM bookings WHERE tenant_id=:tenant AND customer_id=:customer AND"
                    + " idempotency_key=:key")
            .param("tenant", a.tenantId())
            .param("customer", a.userId())
            .param("key", key)
            .query(
                (rs, n) -> {
                  if (rs.getObject("session_id") != null
                      || !r.providerId().equals(rs.getObject("provider_id", UUID.class))
                      || !r.serviceId().equals(rs.getObject("service_id", UUID.class))
                      || !r.startsAt().equals(rs.getTimestamp("request_starts_at").toInstant())
                      || !Objects.equals(r.roomId(), rs.getObject("request_room_id", UUID.class)))
                    throw ApiFailure.conflict(
                        "Idempotency key was already used for a different request");
                  return map(rs);
                })
            .optional();
    if (old.isPresent()) return old.get();
    catalog.lockProvider(a, r.providerId());
    var service = catalog.service(a, r.serviceId());
    business.requireServiceAllowed(a, r.providerId(), r.serviceId());
    business.validateStart(a, r.startsAt());
    if (r.roomId() != null) business.room(a, r.roomId());
    Instant end = r.startsAt().plus(Duration.ofMinutes(service.durationMinutes()));
    fits(a, r.providerId(), r.startsAt(), end);
    UUID id = UUID.randomUUID();
    insert(
        a,
        id,
        r.providerId(),
        r.serviceId(),
        r.roomId(),
        null,
        r.startsAt(),
        end,
        service.cancellationHours(),
        key);
    notifications.event(a, id, a.userId(), r.providerId(), "BOOKING_CONFIRMED");
    return get(a, id);
  }

  void fits(Actor a, UUID provider, Instant start, Instant end) {
    boolean fits =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM availability WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND starts_at<=:start AND ends_at>=:end)")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("start", utc(start))
            .param("end", utc(end))
            .query(Boolean.class)
            .single();
    if (!fits) throw ApiFailure.conflict("Requested time is outside provider availability");
  }

  void insert(
      Actor a,
      UUID id,
      UUID provider,
      UUID service,
      UUID room,
      UUID session,
      Instant start,
      Instant end,
      int cancellation,
      String key) {
    db.sql(
            "INSERT INTO"
                + " bookings(id,tenant_id,provider_id,service_id,customer_id,room_id,session_id,starts_at,ends_at,request_starts_at,request_room_id,cancellation_hours,status,idempotency_key)"
                + " VALUES(:id,:tenant,:provider,:service,:customer,:room,:session,:start,:end,:start,:room,:cancel,'CONFIRMED',:key)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("service", service)
        .param("customer", a.userId())
        .param("room", room, Types.OTHER)
        .param("session", session, Types.OTHER)
        .param("start", utc(start))
        .param("end", utc(end))
        .param("cancel", cancellation)
        .param("key", key)
        .update();
  }

  private static final String ACCESS =
      "b.tenant_id=:tenant AND (:admin OR b.customer_id=:user OR EXISTS(SELECT 1 FROM providers p"
          + " WHERE p.id=b.provider_id AND p.tenant_id=b.tenant_id AND p.user_id=:user))";

  public Booking get(Actor a, UUID id) {
    return db.sql("SELECT b.* FROM bookings b WHERE " + ACCESS + " AND b.id=:id")
        .param("tenant", a.tenantId())
        .param("admin", a.admin())
        .param("user", a.userId())
        .param("id", id)
        .query((rs, n) -> map(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Page<Booking> list(Actor a, int page, int size) {
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT b.* FROM bookings b WHERE "
                    + ACCESS
                    + " ORDER BY b.created_at DESC,b.id DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("admin", a.admin())
            .param("user", a.userId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query((rs, n) -> map(rs))
            .list();
    return new Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  private Booking lock(Actor a, UUID id) {
    commands.lock(a);
    var current = get(a, id);
    catalog.lockProvider(a, current.providerId());
    db.sql("SELECT id FROM bookings WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(UUID.class)
        .single();
    return get(a, id);
  }

  @Transactional
  public Booking cancel(Actor a, UUID id) {
    var b = lock(a, id);
    if ("CANCELLED".equals(b.status())) return b;
    deadline(b);
    db.sql(
            "UPDATE bookings SET status='CANCELLED',cancelled_at=now() WHERE tenant_id=:tenant AND"
                + " id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    notifications.event(a, id, b.customerId(), b.providerId(), "BOOKING_CANCELLED");
    return get(a, id);
  }

  private void deadline(Booking b) {
    if (!Instant.now().isBefore(b.startsAt().minus(Duration.ofHours(b.cancellationHours()))))
      throw ApiFailure.conflict("Cancellation/rescheduling deadline has passed");
  }

  @Transactional
  public Booking reschedule(Actor a, UUID id, PlatformModels.Reschedule r, String key) {
    String op = "RESCHEDULE:" + id;
    var replay = commands.find(a, op, key, r);
    if (replay.isPresent()) return get(a, id);
    var b = lock(a, id);
    if (!"CONFIRMED".equals(b.status()) || b.sessionId() != null)
      throw ApiFailure.conflict(
          "Only confirmed private bookings can be rescheduled; cancel and re-enrol group bookings");
    deadline(b);
    business.validateStart(a, r.startsAt());
    Instant end = r.startsAt().plus(Duration.between(b.startsAt(), b.endsAt()));
    fits(a, b.providerId(), r.startsAt(), end);

    db.sql("UPDATE bookings SET starts_at=:start,ends_at=:end WHERE tenant_id=:tenant AND id=:id")
        .param("start", utc(r.startsAt()))
        .param("end", utc(end))
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    notifications.event(a, id, b.customerId(), b.providerId(), "BOOKING_RESCHEDULED");
    commands.save(a, op, key, r, List.of(id));
    return get(a, id);
  }

  @Transactional
  public List<Booking> batch(Actor a, PlatformModels.Batch request, String key) {
    var replay = commands.find(a, "BATCH_PRIVATE", key, request);
    if (replay.isPresent()) return replay.get().stream().map(id -> get(a, id)).toList();
    request.bookings().stream()
        .map(BookingRequest::providerId)
        .distinct()
        .sorted()
        .forEach(id -> catalog.lockProvider(a, id));
    var result = new ArrayList<Booking>();
    for (int i = 0; i < request.bookings().size(); i++)
      result.add(
          create(a, request.bookings().get(i), "batch-" + SessionService.hash(key) + ":" + i));
    commands.save(a, "BATCH_PRIVATE", key, request, result.stream().map(Booking::id).toList());
    return result;
  }

  public List<Instant> slots(Actor a, UUID provider, UUID serviceId, Instant from, Instant to) {
    validateRange(from, to, Duration.ofDays(7));
    var service = catalog.service(a, serviceId);
    business.requireServiceAllowed(a, provider, serviceId);
    var windows = catalog.windows(a, provider, from, to);
    var busy =
        db.sql(
                "SELECT starts_at,ends_at FROM resource_occupancy WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND starts_at<:end AND ends_at>:start")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("start", utc(from))
            .param("end", utc(to))
            .query(
                (rs, n) ->
                    new Window(
                        null, rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()))
            .list();
    var result = new ArrayList<Instant>();
    var settings = business.settings(a.tenantId());
    Instant minimum = Instant.now().plus(Duration.ofMinutes(settings.minimumNoticeMinutes()));
    Instant maximum = Instant.now().plus(Duration.ofDays(settings.bookingHorizonDays()));
    Duration duration = Duration.ofMinutes(service.durationMinutes());
    for (var window : windows)
      for (Instant start = window.startsAt();
          !start.plus(duration).isAfter(window.endsAt()) && start.isBefore(to);
          start = start.plus(duration)) {
        if (start.isBefore(from) || !start.isAfter(minimum) || start.isAfter(maximum)) continue;
        Instant candidate = start, end = start.plus(duration);
        if (end.isAfter(to)) continue;
        if (busy.stream()
            .noneMatch(b -> candidate.isBefore(b.endsAt()) && end.isAfter(b.startsAt())))
          result.add(start);
      }
    return result;
  }
}
