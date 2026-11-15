package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.CommerceModels.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantService {
  private final JdbcClient db;
  private final PasswordEncoder passwords;

  public ParticipantService(JdbcClient db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  private Student map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new Student(
        rs.getObject("id", UUID.class),
        rs.getObject("guardian_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("display_name"),
        rs.getObject("date_of_birth", LocalDate.class),
        rs.getString("gender"),
        rs.getBoolean("self_booking_enabled"),
        rs.getBoolean("login_enabled"),
        rs.getBoolean("active"));
  }

  Student raw(Actor a, UUID id) {
    return db.sql("SELECT * FROM participants WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query((rs, n) -> map(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Student own(Actor a) {
    return db.sql("SELECT * FROM participants WHERE tenant_id=:tenant AND user_id=:user")
        .param("tenant", a.tenantId())
        .param("user", a.userId())
        .query((rs, n) -> map(rs))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Student get(Actor a, UUID id) {
    var s = raw(a, id);
    if (a.admin() || a.userId().equals(s.guardianId()) || a.userId().equals(s.userId())) return s;
    boolean linked =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM bookings b JOIN providers p ON p.id=b.provider_id AND"
                    + " p.tenant_id=b.tenant_id WHERE b.tenant_id=:tenant AND b.participant_id=:id"
                    + " AND p.user_id=:user)")
            .param("tenant", a.tenantId())
            .param("id", id)
            .param("user", a.userId())
            .query(Boolean.class)
            .single();
    if (!linked) throw ApiFailure.notFound();
    return new Student(s.id(), null, null, s.displayName(), null, "", false, false, s.active());
  }

  public Models.Page<Student> list(Actor a, int page, int size) {
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT * FROM participants WHERE tenant_id=:tenant AND guardian_id=:guardian ORDER"
                    + " BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("guardian", a.userId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query((rs, n) -> map(rs))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  private void lockFamily(Actor a, UUID id) {
    db.sql("SELECT id FROM users WHERE tenant_id=:tenant AND id=:user FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("user", a.userId())
        .query(UUID.class)
        .single();
    var s = guardian(a, id);
    if (s.userId() != null)
      db.sql("SELECT id FROM users WHERE tenant_id=:tenant AND id=:user FOR UPDATE")
          .param("tenant", a.tenantId())
          .param("user", s.userId())
          .query(UUID.class)
          .single();
    db.sql("SELECT id FROM participants WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(UUID.class)
        .single();
  }

  private Student guardian(Actor a, UUID id) {
    var s = raw(a, id);
    if (!a.userId().equals(s.guardianId())) throw ApiFailure.notFound();
    return s;
  }

  public Student reference(Actor a, UUID requested) {
    var s = requested == null ? own(a) : raw(a, requested);
    if (!a.userId().equals(s.guardianId()) && !a.userId().equals(s.userId()))
      throw ApiFailure.notFound();
    return s;
  }

  public Student resolve(Actor a, UUID requested) {
    var ref = reference(a, requested);
    if (org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      db.sql("SELECT id FROM participants WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
          .param("tenant", a.tenantId())
          .param("id", ref.id())
          .query(UUID.class)
          .single();
    var s = raw(a, ref.id());
    if (!s.active()) throw ApiFailure.conflict("Student is archived");
    if (a.userId().equals(s.guardianId())) return s;
    if (!a.userId().equals(s.userId())) throw ApiFailure.notFound();
    if (s.guardianId() != null && !s.selfBookingEnabled()) throw ApiFailure.forbidden();
    return s;
  }

  UUID payer(Student s) {
    return s.guardianId() != null ? s.guardianId() : s.userId();
  }

  @Transactional
  public Student create(Actor a, StudentInput r) {
    if (Set.of("PROVIDER", "MANAGED_STUDENT").contains(a.role())) throw ApiFailure.forbidden();
    UUID id = UUID.randomUUID();
    db.sql(
            "INSERT INTO"
                + " participants(id,tenant_id,guardian_id,display_name,date_of_birth,gender,self_booking_enabled)"
                + " VALUES(:id,:tenant,:guardian,:name,:dob,:gender,:self)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("guardian", a.userId())
        .param("name", r.displayName().trim())
        .param("dob", r.dateOfBirth(), java.sql.Types.DATE)
        .param("gender", r.gender().trim())
        .param("self", r.selfBookingEnabled())
        .update();
    return raw(a, id);
  }

  @Transactional
  public Student update(Actor a, UUID id, StudentInput r) {
    lockFamily(a, id);
    var s = guardian(a, id);
    if (!s.active()) throw ApiFailure.conflict("Student is archived");
    db.sql(
            "UPDATE participants SET"
                + " display_name=:name,date_of_birth=:dob,gender=:gender,self_booking_enabled=:self"
                + " WHERE tenant_id=:tenant AND id=:id")
        .param("name", r.displayName().trim())
        .param("dob", r.dateOfBirth(), java.sql.Types.DATE)
        .param("gender", r.gender().trim())
        .param("self", r.selfBookingEnabled())
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    if (s.userId() != null)
      db.sql("UPDATE users SET display_name=:name WHERE tenant_id=:tenant AND id=:id")
          .param("name", r.displayName().trim())
          .param("tenant", a.tenantId())
          .param("id", s.userId())
          .update();
    return raw(a, id);
  }

  @Transactional
  public Student login(Actor a, UUID id, StudentLogin r) {
    lockFamily(a, id);
    var s = guardian(a, id);
    if (!s.active()) throw ApiFailure.conflict("Student is archived");
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiFailure.invalid("Password must be at most 72 UTF-8 bytes");
    String hash = passwords.encode(r.password());
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    UUID user = s.userId();
    if (user == null) {
      user = UUID.randomUUID();
      db.sql(
              "INSERT INTO users(id,tenant_id,email,password_hash,role,display_name)"
                  + " VALUES(:id,:tenant,:email,:hash,'MANAGED_STUDENT',:name)")
          .param("id", user)
          .param("tenant", a.tenantId())
          .param("email", email)
          .param("hash", hash)
          .param("name", s.displayName())
          .update();
      db.sql("UPDATE participants SET user_id=:user WHERE tenant_id=:tenant AND id=:id")
          .param("user", user)
          .param("tenant", a.tenantId())
          .param("id", id)
          .update();
    } else {
      db.sql("UPDATE users SET email=:email,password_hash=:hash WHERE tenant_id=:tenant AND id=:id")
          .param("email", email)
          .param("hash", hash)
          .param("tenant", a.tenantId())
          .param("id", user)
          .update();
    }
    db.sql("UPDATE participants SET login_enabled=true WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    db.sql("DELETE FROM auth_sessions WHERE user_id=:id").param("id", user).update();
    return raw(a, id);
  }

  @Transactional
  public void disableLogin(Actor a, UUID id) {
    lockFamily(a, id);
    db.sql("UPDATE participants SET login_enabled=false WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
    var s = guardian(a, id);
    if (s.userId() == null) return;
    db.sql("DELETE FROM auth_sessions WHERE user_id=:id").param("id", s.userId()).update();
    db.sql("UPDATE users SET password_hash=:hash WHERE tenant_id=:tenant AND id=:id")
        .param("hash", passwords.encode(UUID.randomUUID().toString()))
        .param("tenant", a.tenantId())
        .param("id", s.userId())
        .update();
  }

  @Transactional
  public void archive(Actor a, UUID id) {
    lockFamily(a, id);
    guardian(a, id);
    db.sql("SELECT id FROM participants WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(UUID.class)
        .single();
    boolean active =
        db.sql(
                "SELECT EXISTS(SELECT 1 FROM bookings WHERE tenant_id=:tenant AND"
                    + " participant_id=:id AND status='CONFIRMED' AND ends_at>now())")
            .param("tenant", a.tenantId())
            .param("id", id)
            .query(Boolean.class)
            .single();
    if (active) throw ApiFailure.conflict("Cancel future bookings before archiving the student");
    disableLogin(a, id);
    db.sql(
            "UPDATE participants SET active=false,self_booking_enabled=false WHERE"
                + " tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
  }
}
