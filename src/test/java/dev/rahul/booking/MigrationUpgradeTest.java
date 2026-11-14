package dev.rahul.booking;

import static org.assertj.core.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MigrationUpgradeTest {
  @Test
  void existingBookingsUpgradeAndDirectWritesRetainConflictProtection() throws Exception {
    try (var pg =
        EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").start()) {
      var source =
          new DriverManagerDataSource(
              pg.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
      Flyway.configure().dataSource(source).target("1").load().migrate();
      var db = JdbcClient.create(source);
      UUID tenant = UUID.randomUUID(),
          user = UUID.randomUUID(),
          other = UUID.randomUUID(),
          provider = UUID.randomUUID(),
          service = UUID.randomUUID(),
          booking = UUID.randomUUID();
      var start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5).withNano(0);
      var end = start.plusMinutes(30);
      db.sql("INSERT INTO tenants(id,slug,name) VALUES(?,?,?)")
          .params(tenant, "upgrade-test", "Upgrade")
          .update();
      db.sql(
              "INSERT INTO users(id,tenant_id,email,password_hash,role)"
                  + " VALUES(?,?,?,'not-a-login-hash','CUSTOMER')")
          .params(user, tenant, "one@example.test")
          .update();
      db.sql(
              "INSERT INTO users(id,tenant_id,email,password_hash,role)"
                  + " VALUES(?,?,?,'not-a-login-hash','CUSTOMER')")
          .params(other, tenant, "two@example.test")
          .update();
      db.sql("INSERT INTO providers(id,tenant_id,name,time_zone) VALUES(?,?,'Coach','UTC')")
          .params(provider, tenant)
          .update();
      db.sql(
              "INSERT INTO services(id,tenant_id,name,duration_minutes,cancellation_hours)"
                  + " VALUES(?,?,'Lesson',30,24)")
          .params(service, tenant)
          .update();
      db.sql(
              "INSERT INTO"
                  + " bookings(id,tenant_id,provider_id,service_id,customer_id,starts_at,ends_at,cancellation_hours,status,idempotency_key)"
                  + " VALUES(?,?,?,?,?,?,?,24,'CONFIRMED','legacy-request1')")
          .params(booking, tenant, provider, service, user, start, end)
          .update();
      Flyway.configure().dataSource(source).load().migrate();
      assertThat(db.sql("SELECT id FROM resource_occupancy").query(UUID.class).single())
          .isEqualTo(booking);
      assertThat(
              db.sql("SELECT request_starts_at FROM bookings WHERE id=?")
                  .param(booking)
                  .query(OffsetDateTime.class)
                  .single()
                  .toInstant())
          .isEqualTo(start.toInstant());
      assertThatThrownBy(
              () ->
                  db.sql(
                          "INSERT INTO"
                              + " bookings(id,tenant_id,provider_id,service_id,customer_id,starts_at,ends_at,request_starts_at,cancellation_hours,status,idempotency_key)"
                              + " VALUES(?,?,?,?,?,?,?,?,24,'CONFIRMED','direct-conflict')")
                      .params(
                          UUID.randomUUID(), tenant, provider, service, other, start, end, start)
                      .update())
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(db.sql("SELECT count(*) FROM bookings").query(Long.class).single()).isEqualTo(1);
    }
  }
}
