package dev.rahul.booking;

import static dev.rahul.booking.Models.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final JdbcClient db;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final boolean cache;
  private final BusinessService business;

  public CatalogService(
      JdbcClient db,
      StringRedisTemplate redis,
      ObjectMapper json,
      @Value("${app.cache-enabled}") boolean cache,
      BusinessService business) {
    this.db = db;
    this.redis = redis;
    this.json = json;
    this.cache = cache;
    this.business = business;
  }

  public Provider provider(Actor actor, UUID id) {
    return db.sql("SELECT id,name,time_zone FROM providers WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .query(
            (rs, n) ->
                new Provider(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("time_zone")))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Provider addProvider(Actor actor, ProviderRequest r) {
    actor.requireAdmin();
    try {
      ZoneId.of(r.timeZone());
    } catch (DateTimeException e) {
      throw ApiFailure.invalid("Use an IANA time zone, for example Europe/Vilnius");
    }
    var id = UUID.randomUUID();
    db.sql("INSERT INTO providers(id,tenant_id,name,time_zone) VALUES(:id,:tenant,:name,:zone)")
        .param("id", id)
        .param("tenant", actor.tenantId())
        .param("name", r.name().trim())
        .param("zone", r.timeZone())
        .update();
    return new Provider(id, r.name().trim(), r.timeZone());
  }

  public List<Provider> providers(Actor actor, int page, int size) {
    bounds(page, size);
    return db.sql(
            "SELECT id,name,time_zone FROM providers WHERE tenant_id=:tenant ORDER BY id LIMIT"
                + " :limit OFFSET :offset")
        .param("tenant", actor.tenantId())
        .param("limit", size)
        .param("offset", page * size)
        .query(
            (rs, n) ->
                new Provider(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("time_zone")))
        .list();
  }

  public Models.Service addService(Actor actor, ServiceRequest r) {
    actor.requireAdmin();
    var id = UUID.randomUUID();
    db.sql(
            "INSERT INTO services(id,tenant_id,name,duration_minutes,cancellation_hours)"
                + " VALUES(:id,:tenant,:name,:duration,:cancel)")
        .param("id", id)
        .param("tenant", actor.tenantId())
        .param("name", r.name().trim())
        .param("duration", r.durationMinutes())
        .param("cancel", r.cancellationHours())
        .update();
    return new Models.Service(id, r.name().trim(), r.durationMinutes(), r.cancellationHours());
  }

  public List<Models.Service> services(Actor actor, int page, int size) {
    bounds(page, size);
    String key = "booking:catalog:v1:" + actor.tenantId() + ":" + page + ":" + size;
    if (cache)
      try {
        var value = redis.opsForValue().get(key);
        if (value != null)
          return json.readValue(value, new TypeReference<List<Models.Service>>() {});
      } catch (Exception e) {
        /* Database fallback; availability is never cached. */
      }
    var result =
        db.sql(
                "SELECT id,name,duration_minutes,cancellation_hours FROM services WHERE"
                    + " tenant_id=:tenant ORDER BY id LIMIT :limit OFFSET :offset")
            .param("tenant", actor.tenantId())
            .param("limit", size)
            .param("offset", page * size)
            .query((rs, n) -> mapService(rs))
            .list();
    if (cache)
      try {
        redis.opsForValue().set(key, json.writeValueAsString(result), Duration.ofSeconds(30));
      } catch (Exception e) {
        /* Cache is optional for catalogue reads. */
      }
    return result;
  }

  Models.Service service(Actor actor, UUID id) {
    return db.sql(
            "SELECT id,name,duration_minutes,cancellation_hours FROM services WHERE"
                + " tenant_id=:tenant AND id=:id")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .query((rs, n) -> mapService(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  private static Models.Service mapService(ResultSet rs) throws SQLException {
    return new Models.Service(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getInt("duration_minutes"),
        rs.getInt("cancellation_hours"));
  }

  void lockProvider(Actor actor, UUID id) {
    db.sql("SELECT id FROM providers WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", actor.tenantId())
        .param("id", id)
        .query(UUID.class)
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  @Transactional
  public Window addWindow(Actor actor, UUID provider, AvailabilityRequest r) {
    business.requireProviderManager(actor, provider);
    lockProvider(actor, provider);
    validateRange(r.startsAt(), r.endsAt(), Duration.ofDays(7));
    if (!r.startsAt().isAfter(Instant.now()))
      throw ApiFailure.invalid("Availability must be in the future");
    var id = UUID.randomUUID();
    db.sql(
            "INSERT INTO availability(id,tenant_id,provider_id,starts_at,ends_at)"
                + " VALUES(:id,:tenant,:provider,:start,:end)")
        .param("id", id)
        .param("tenant", actor.tenantId())
        .param("provider", provider)
        .param("start", utc(r.startsAt()))
        .param("end", utc(r.endsAt()))
        .update();
    return new Window(id, r.startsAt(), r.endsAt());
  }

  public List<Window> windows(Actor actor, UUID provider, Instant from, Instant to) {
    provider(actor, provider);
    validateRange(from, to, Duration.ofDays(31));
    return db.sql(
            "SELECT id,starts_at,ends_at FROM availability WHERE tenant_id=:tenant AND"
                + " provider_id=:provider AND starts_at<:end AND ends_at>:start ORDER BY starts_at"
                + " LIMIT 1000")
        .param("tenant", actor.tenantId())
        .param("provider", provider)
        .param("start", utc(from))
        .param("end", utc(to))
        .query(
            (rs, n) ->
                new Window(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at").toInstant()))
        .list();
  }

  public static void bounds(int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100)
      throw ApiFailure.invalid("page must be 0–10000 and size 1–100");
  }

  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  static void validateRange(Instant from, Instant to, Duration max) {
    if (!to.isAfter(from) || Duration.between(from, to).compareTo(max) > 0)
      throw ApiFailure.invalid("Invalid or excessive time range");
  }
}
