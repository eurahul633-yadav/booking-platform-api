package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.PlatformModels.*;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {
  private final JdbcClient db;

  public BusinessService(JdbcClient db) {
    this.db = db;
  }

  public Settings settings(UUID tenant) {
    return db.sql(
            "SELECT booking_horizon_days,minimum_notice_minutes,customer_signup_enabled FROM"
                + " tenant_settings WHERE tenant_id=:tenant")
        .param("tenant", tenant)
        .query((rs, n) -> new Settings(rs.getInt(1), rs.getInt(2), rs.getBoolean(3)))
        .optional()
        .orElse(new Settings(366, 0, true));
  }

  @Transactional
  public Settings configure(Actor a, Settings r) {
    a.requireAdmin();
    db.sql(
            "INSERT INTO"
                + " tenant_settings(tenant_id,booking_horizon_days,minimum_notice_minutes,customer_signup_enabled)"
                + " VALUES(:tenant,:horizon,:notice,:signup) ON CONFLICT(tenant_id) DO UPDATE SET"
                + " booking_horizon_days=excluded.booking_horizon_days,minimum_notice_minutes=excluded.minimum_notice_minutes,customer_signup_enabled=excluded.customer_signup_enabled")
        .param("tenant", a.tenantId())
        .param("horizon", r.bookingHorizonDays())
        .param("notice", r.minimumNoticeMinutes())
        .param("signup", r.customerSignupEnabled())
        .update();
    return r;
  }

  public void validateStart(Actor a, Instant start) {
    if (start.getNano() % 1000 != 0)
      throw ApiFailure.invalid("Timestamps must use at most microsecond precision");
    var s = settings(a.tenantId());
    Instant now = Instant.now();
    if (!start.isAfter(now.plus(Duration.ofMinutes(s.minimumNoticeMinutes())))
        || start.isAfter(now.plus(Duration.ofDays(s.bookingHorizonDays()))))
      throw ApiFailure.invalid("Requested start violates minimum notice or booking horizon");
  }

  static void zone(String zone) {
    if (!ZoneId.getAvailableZoneIds().contains(zone) && !"UTC".equals(zone))
      throw ApiFailure.invalid("Use a valid IANA time zone");
  }

  public List<Location> locations(Actor a, int page, int size) {
    bounds(page, size);
    return db.sql(
            "SELECT * FROM locations WHERE tenant_id=:tenant ORDER BY id LIMIT :limit OFFSET"
                + " :offset")
        .param("tenant", a.tenantId())
        .param("limit", size)
        .param("offset", page * size)
        .query(
            (rs, n) ->
                new Location(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("time_zone")))
        .list();
  }

  public Location location(Actor a, UUID id) {
    return db.sql("SELECT * FROM locations WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(
            (rs, n) ->
                new Location(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("time_zone")))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Location createLocation(Actor a, LocationInput r) {
    a.requireAdmin();
    zone(r.timeZone());
    UUID id = UUID.randomUUID();
    db.sql(
            "INSERT INTO locations(id,tenant_id,name,address,time_zone)"
                + " VALUES(:id,:tenant,:name,:address,:zone)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("name", r.name().trim())
        .param("address", r.address().trim())
        .param("zone", r.timeZone())
        .update();
    return location(a, id);
  }

  public Location updateLocation(Actor a, UUID id, LocationInput r) {
    a.requireAdmin();
    zone(r.timeZone());
    if (db.sql(
                "UPDATE locations SET name=:name,address=:address,time_zone=:zone WHERE"
                    + " tenant_id=:tenant AND id=:id")
            .param("id", id)
            .param("tenant", a.tenantId())
            .param("name", r.name().trim())
            .param("address", r.address().trim())
            .param("zone", r.timeZone())
            .update()
        == 0) throw ApiFailure.notFound();
    return location(a, id);
  }

  public List<Room> rooms(Actor a, UUID location, int page, int size) {
    location(a, location);
    bounds(page, size);
    return db.sql(
            "SELECT * FROM rooms WHERE tenant_id=:tenant AND location_id=:location ORDER BY id"
                + " LIMIT :limit OFFSET :offset")
        .param("tenant", a.tenantId())
        .param("location", location)
        .param("limit", size)
        .param("offset", page * size)
        .query(
            (rs, n) ->
                new Room(
                    rs.getObject("id", UUID.class),
                    rs.getObject("location_id", UUID.class),
                    rs.getString("name"),
                    rs.getInt("capacity")))
        .list();
  }

  public Room room(Actor a, UUID id) {
    return db.sql("SELECT * FROM rooms WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(
            (rs, n) ->
                new Room(
                    rs.getObject("id", UUID.class),
                    rs.getObject("location_id", UUID.class),
                    rs.getString("name"),
                    rs.getInt("capacity")))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Room createRoom(Actor a, UUID location, RoomInput r) {
    a.requireAdmin();
    location(a, location);
    UUID id = UUID.randomUUID();
    db.sql(
            "INSERT INTO rooms(id,tenant_id,location_id,name,capacity)"
                + " VALUES(:id,:tenant,:location,:name,:capacity)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("location", location)
        .param("name", r.name().trim())
        .param("capacity", r.capacity())
        .update();
    return room(a, id);
  }

  public void requireProviderManager(Actor a, UUID provider) {
    if (a.admin()) return;
    boolean own =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM providers WHERE tenant_id=:tenant AND id=:provider AND"
                    + " user_id=:user)")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("user", a.userId())
            .query(Boolean.class)
            .single();
    if (!own) throw ApiFailure.forbidden();
  }

  public void requireServiceAllowed(Actor a, UUID provider, UUID service) {
    boolean allowed =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM providers p WHERE p.tenant_id=:tenant AND"
                    + " p.id=:provider AND (NOT p.assigned_services_only OR EXISTS(SELECT 1 FROM"
                    + " provider_services ps WHERE ps.provider_id=p.id AND"
                    + " ps.service_id=:service)))")
            .param("tenant", a.tenantId())
            .param("provider", provider)
            .param("service", service)
            .query(Boolean.class)
            .single();
    if (!allowed) throw ApiFailure.conflict("Provider is not assigned to this service");
  }

  public void reserve(
      Actor a, UUID id, UUID provider, UUID room, String kind, Instant from, Instant to) {
    db.sql(
            "INSERT INTO"
                + " resource_occupancy(id,tenant_id,provider_id,room_id,kind,starts_at,ends_at)"
                + " VALUES(:id,:tenant,:provider,:room,:kind,:start,:end)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("room", room, java.sql.Types.OTHER)
        .param("kind", kind)
        .param("start", utc(from))
        .param("end", utc(to))
        .update();
  }

  public void release(Actor a, UUID id) {
    db.sql("DELETE FROM resource_occupancy WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
  }
}
