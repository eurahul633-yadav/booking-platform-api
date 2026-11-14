package dev.rahul.booking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class Commands {
  private final JdbcClient db;
  private final ObjectMapper json;

  public Commands(JdbcClient db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public void lock(Actor a) {
    db.sql("SELECT id FROM users WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", a.userId())
        .query(UUID.class)
        .single();
  }

  public static void key(String key) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{8,100}"))
      throw ApiFailure.invalid("Idempotency-Key must contain 8–100 letters, digits, or . _ : -");
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize command", e);
    }
  }

  public Optional<List<UUID>> find(Actor a, String operation, String key, Object request) {
    key(key);
    lock(a);
    String hash = SessionService.hash(encode(request));
    return db.sql(
            "SELECT request_hash,result_ids FROM booking_commands WHERE tenant_id=:tenant AND"
                + " actor_id=:actor AND operation=:operation AND command_key=:key")
        .param("tenant", a.tenantId())
        .param("actor", a.userId())
        .param("operation", operation)
        .param("key", key)
        .query(
            (rs, n) -> {
              if (!hash.equals(rs.getString("request_hash")))
                throw ApiFailure.conflict(
                    "Idempotency key was already used for a different request");
              try {
                return json.readValue(
                    rs.getString("result_ids"), new TypeReference<List<UUID>>() {});
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .optional();
  }

  public void save(Actor a, String operation, String key, Object request, List<UUID> ids) {
    db.sql(
            "INSERT INTO"
                + " booking_commands(tenant_id,actor_id,operation,command_key,request_hash,result_ids)"
                + " VALUES(:tenant,:actor,:operation,:key,:hash,cast(:ids AS jsonb))")
        .param("tenant", a.tenantId())
        .param("actor", a.userId())
        .param("operation", operation)
        .param("key", key)
        .param("hash", SessionService.hash(encode(request)))
        .param("ids", encode(ids))
        .update();
  }
}
