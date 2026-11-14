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

  public BookingService(JdbcClient db, CatalogService catalog) {
    this.db = db;
    this.catalog = catalog;
  }

  private static Booking map(ResultSet rs) throws SQLException {
    return new Booking(
        rs.getObject("id", UUID.class),
        rs.getObject("provider_id", UUID.class),
        rs.getObject("service_id", UUID.class),
        rs.getObject("customer_id", UUID.class),
        rs.getTimestamp("starts_at").toInstant(),
        rs.getTimestamp("ends_at").toInstant(),
        rs.getString("status"),
        rs.getInt("cancellation_hours"));
  }

  private Optional<Booking> existing(Actor actor, String key) {
    return db.sql(
            "SELECT * FROM bookings WHERE tenant_id=:tenant AND customer_id=:customer AND"
                + " idempotency_key=:key")
        .param("tenant", actor.tenantId())
        .param("customer", actor.userId())
        .param("key", key)
        .query((rs, n) -> map(rs))
        .optional();
  }

  @Transactional
  public Booking create(Actor actor, BookingRequest r, String key) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}"))
      throw ApiFailure.invalid("Idempotency-Key must contain 8–100 letters, digits, or . _ : -");
    if (r.startsAt().getNano() % 1000 != 0)
      throw ApiFailure.invalid("Timestamps must use at most microsecond precision");
    // Serializes same-customer retries across different providers before inspecting the key.
    db.sql("SELECT id FROM users WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", actor.tenantId())
        .param("id", actor.userId())
        .query(UUID.class)
        .single();
    var old = existing(actor, key);
    if (old.isPresent()) {
      var b = old.get();
      if (!b.providerId().equals(r.providerId())
          || !b.serviceId().equals(r.serviceId())
          || !b.startsAt().equals(r.startsAt()))
        throw ApiFailure.conflict("Idempotency key was already used for a different request");
      return b;
    }
    catalog.lockProvider(actor, r.providerId());
    var service = catalog.service(actor, r.serviceId());
    if (!r.startsAt().isAfter(Instant.now())
        || r.startsAt().isAfter(Instant.now().plus(Duration.ofDays(366))))
      throw ApiFailure.invalid("Booking must start within the next 366 days");
    Instant end = r.startsAt().plus(Duration.ofMinutes(service.durationMinutes()));
    var fits =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM availability WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND starts_at<=:start AND ends_at>=:end)")
            .param("tenant", actor.tenantId())
            .param("provider", r.providerId())
            .param("start", utc(r.startsAt()))
            .param("end", utc(end))
            .query(Boolean.class)
            .single();
    if (!fits) throw ApiFailure.conflict("Requested time is outside provider availability");
    var id = UUID.randomUUID();
    db.sql(
            "INSERT INTO"
                + " bookings(id,tenant_id,provider_id,service_id,customer_id,starts_at,ends_at,cancellation_hours,status,idempotency_key)"
                + " VALUES(:id,:tenant,:provider,:service,:customer,:start,:end,:cancel,'CONFIRMED',:key)")
        .param("id", id)
        .param("tenant", actor.tenantId())
        .param("provider", r.providerId())
        .param("service", r.serviceId())
        .param("customer", actor.userId())
        .param("start", utc(r.startsAt()))
        .param("end", utc(end))
        .param("cancel", service.cancellationHours())
        .param("key", key)
        .update();
    event(actor, id, "BOOKING_CONFIRMED");
    return get(actor, id);
  }

  public Booking get(Actor actor, UUID id) {
    return db.sql(
            "SELECT * FROM bookings WHERE tenant_id=:tenant AND id=:id AND (:admin OR"
                + " customer_id=:customer)")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .param("admin", actor.admin())
        .param("customer", actor.userId())
        .query((rs, n) -> map(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Page<Booking> list(Actor actor, int page, int size) {
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT * FROM bookings WHERE tenant_id=:tenant AND (:admin OR"
                    + " customer_id=:customer) ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET"
                    + " :offset")
            .param("tenant", actor.tenantId())
            .param("admin", actor.admin())
            .param("customer", actor.userId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query((rs, n) -> map(rs))
            .list();
    return new Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  @Transactional
  public Booking cancel(Actor actor, UUID id) {
    // Row lock makes repeated and concurrent cancellation idempotent.
    db.sql(
            "SELECT id FROM bookings WHERE tenant_id=:tenant AND id=:id AND (:admin OR"
                + " customer_id=:customer) FOR UPDATE")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .param("admin", actor.admin())
        .param("customer", actor.userId())
        .query(UUID.class)
        .optional()
        .orElseThrow(ApiFailure::notFound);
    var booking = get(actor, id);
    if ("CANCELLED".equals(booking.status())) return booking;
    if (!Instant.now()
        .isBefore(booking.startsAt().minus(Duration.ofHours(booking.cancellationHours()))))
      throw ApiFailure.conflict("Cancellation deadline has passed");
    db.sql(
            "UPDATE bookings SET status='CANCELLED',cancelled_at=now() WHERE tenant_id=:tenant AND"
                + " id=:id")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .update();
    event(actor, id, "BOOKING_CANCELLED");
    return get(actor, id);
  }

  public List<Instant> slots(Actor actor, UUID provider, UUID serviceId, Instant from, Instant to) {
    validateRange(from, to, Duration.ofDays(7));
    var service = catalog.service(actor, serviceId);
    var windows = catalog.windows(actor, provider, from, to);
    var busy =
        db.sql(
                "SELECT starts_at,ends_at FROM bookings WHERE tenant_id=:tenant AND"
                    + " provider_id=:provider AND status='CONFIRMED' AND starts_at<:end AND"
                    + " ends_at>:start")
            .param("tenant", actor.tenantId())
            .param("provider", provider)
            .param("start", utc(from))
            .param("end", utc(to))
            .query(
                (rs, n) ->
                    new Window(
                        null,
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant()))
            .list();
    var result = new ArrayList<Instant>();
    Duration duration = Duration.ofMinutes(service.durationMinutes());
    Instant now = Instant.now();
    for (var window : windows) {
      for (Instant start = window.startsAt();
          !start.plus(duration).isAfter(window.endsAt()) && start.isBefore(to);
          start = start.plus(duration)) {
        if (start.isBefore(from) || !start.isAfter(now)) continue;
        Instant candidate = start;
        Instant end = start.plus(duration);
        if (end.isAfter(to)) continue;
        if (busy.stream()
            .noneMatch(b -> candidate.isBefore(b.endsAt()) && end.isAfter(b.startsAt())))
          result.add(start);
      }
    }
    return result;
  }

  private void event(Actor actor, UUID id, String type) {
    db.sql(
            "INSERT INTO booking_events(id,tenant_id,booking_id,event_type,actor_id)"
                + " VALUES(:id,:tenant,:booking,:type,:actor)")
        .param("id", UUID.randomUUID())
        .param("tenant", actor.tenantId())
        .param("booking", id)
        .param("type", type)
        .param("actor", actor.userId())
        .update();
  }
}
