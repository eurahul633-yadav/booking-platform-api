package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.PlatformModels.*;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final JdbcClient db;

  public NotificationService(JdbcClient db) {
    this.db = db;
  }

  public void event(Actor a, UUID booking, UUID customer, UUID provider, String type) {
    db.sql(
            "INSERT INTO booking_events(id,tenant_id,booking_id,event_type,actor_id)"
                + " VALUES(:id,:tenant,:booking,:type,:actor)")
        .param("id", UUID.randomUUID())
        .param("tenant", a.tenantId())
        .param("booking", booking)
        .param("type", type)
        .param("actor", a.userId())
        .update();
    var recipients = new HashSet<UUID>();
    recipients.add(customer);
    recipients.addAll(
        db.sql(
                "SELECT user_id FROM providers WHERE tenant_id=:tenant AND id=:id AND user_id IS"
                    + " NOT NULL")
            .param("tenant", a.tenantId())
            .param("id", provider)
            .query(UUID.class)
            .list());
    for (UUID user : recipients)
      db.sql(
              "INSERT INTO notifications(id,tenant_id,user_id,booking_id,event_type)"
                  + " VALUES(:id,:tenant,:user,:booking,:type)")
          .param("id", UUID.randomUUID())
          .param("tenant", a.tenantId())
          .param("user", user)
          .param("booking", booking)
          .param("type", type)
          .update();
  }

  public Models.Page<Notification> notifications(Actor a, int page, int size) {
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT * FROM notifications WHERE tenant_id=:tenant AND user_id=:user ORDER BY"
                    + " created_at DESC,id DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("user", a.userId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query(
                (rs, n) ->
                    new Notification(
                        rs.getObject("id", UUID.class),
                        rs.getObject("booking_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null
                            ? null
                            : rs.getTimestamp("read_at").toInstant()))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  public void read(Actor a, UUID id) {
    if (db.sql(
                "UPDATE notifications SET read_at=coalesce(read_at,now()) WHERE tenant_id=:tenant"
                    + " AND user_id=:user AND id=:id")
            .param("tenant", a.tenantId())
            .param("user", a.userId())
            .param("id", id)
            .update()
        == 0) throw ApiFailure.notFound();
  }

  public Models.Page<AuditEvent> events(Actor a, int page, int size) {
    a.requireAdmin();
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT * FROM booking_events WHERE tenant_id=:tenant ORDER BY created_at DESC,id"
                    + " DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query(
                (rs, n) ->
                    new AuditEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("booking_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant()))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }
}
