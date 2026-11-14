package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.PlatformModels.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
  private final JdbcClient db;
  private final PasswordEncoder passwords;
  private final CatalogService catalog;

  public IdentityService(JdbcClient db, PasswordEncoder passwords, CatalogService catalog) {
    this.db = db;
    this.passwords = passwords;
    this.catalog = catalog;
  }

  public User me(Actor a) {
    return user(a, a.userId());
  }

  public User user(Actor a, UUID id) {
    if (!a.admin() && !a.userId().equals(id)) throw ApiFailure.forbidden();
    return db.sql("SELECT id,email,role,display_name FROM users WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .query(
            (rs, n) ->
                new User(
                    rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  public Models.Page<User> users(Actor a, int page, int size) {
    a.requireAdmin();
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT id,email,role,display_name FROM users WHERE tenant_id=:tenant ORDER BY"
                    + " created_at DESC,id DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query(
                (rs, n) ->
                    new User(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  public User profile(Actor a, Profile r) {
    db.sql("UPDATE users SET display_name=:name WHERE tenant_id=:tenant AND id=:id")
        .param("name", r.displayName().trim())
        .param("tenant", a.tenantId())
        .param("id", a.userId())
        .update();
    return me(a);
  }

  private String encode(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiFailure.invalid("Password must be at most 72 UTF-8 bytes");
    return passwords.encode(password);
  }

  @Transactional
  public void password(Actor a, ChangePassword r) {
    var hash =
        db.sql("SELECT password_hash FROM users WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
            .param("tenant", a.tenantId())
            .param("id", a.userId())
            .query(String.class)
            .single();
    if (r.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(r.currentPassword(), hash)) throw ApiFailure.unauthorized();
    var updated = encode(r.newPassword());
    db.sql("UPDATE users SET password_hash=:hash WHERE tenant_id=:tenant AND id=:id")
        .param("hash", updated)
        .param("tenant", a.tenantId())
        .param("id", a.userId())
        .update();
    db.sql("DELETE FROM auth_sessions WHERE user_id=:id").param("id", a.userId()).update();
  }

  @Transactional
  public IssuedInvite invite(Actor a, InviteInput r) {
    a.requireAdmin();
    if ("PROVIDER".equals(r.role())) {
      if (r.providerId() == null)
        throw ApiFailure.invalid("Provider invitations require providerId");
      catalog.lockProvider(a, r.providerId());
      if (db.sql("SELECT user_id IS NOT NULL FROM providers WHERE tenant_id=:tenant AND id=:id")
          .param("tenant", a.tenantId())
          .param("id", r.providerId())
          .query(Boolean.class)
          .single()) throw ApiFailure.conflict("Provider already has an account");
    } else if (r.providerId() != null)
      throw ApiFailure.invalid("Only provider invitations may link a provider");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id = UUID.randomUUID();
    Instant expires = Instant.now().plus(Duration.ofDays(7));
    db.sql(
            "INSERT INTO"
                + " invitations(id,tenant_id,email,role,provider_id,token_hash,status,expires_at)"
                + " VALUES(:id,:tenant,:email,:role,:provider,:hash,'PENDING',:expires)")
        .param("id", id)
        .param("tenant", a.tenantId())
        .param("email", r.email().trim().toLowerCase(Locale.ROOT))
        .param("role", r.role())
        .param("provider", r.providerId(), java.sql.Types.OTHER)
        .param("hash", SessionService.hash(token))
        .param("expires", utc(expires))
        .update();
    return new IssuedInvite(id, token, expires);
  }

  public Models.Page<Invite> invitations(Actor a, int page, int size) {
    a.requireAdmin();
    bounds(page, size);
    var rows =
        db.sql(
                "SELECT * FROM invitations WHERE tenant_id=:tenant ORDER BY created_at DESC,id DESC"
                    + " LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("limit", size + 1)
            .param("offset", page * size)
            .query(
                (rs, n) ->
                    new Invite(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getObject("provider_id", UUID.class),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant()))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  @Transactional
  public void revoke(Actor a, UUID id) {
    a.requireAdmin();
    String state =
        db.sql("SELECT status FROM invitations WHERE tenant_id=:tenant AND id=:id FOR UPDATE")
            .param("tenant", a.tenantId())
            .param("id", id)
            .query(String.class)
            .optional()
            .orElseThrow(ApiFailure::notFound);
    if ("ACCEPTED".equals(state))
      throw ApiFailure.conflict("Accepted invitation cannot be revoked");
    db.sql("UPDATE invitations SET status='REVOKED' WHERE tenant_id=:tenant AND id=:id")
        .param("tenant", a.tenantId())
        .param("id", id)
        .update();
  }

  record Pending(UUID id, UUID tenant, String email, String role, UUID provider) {}

  @Transactional
  public UUID accept(AcceptInvite r) {
    String password = encode(r.password());
    var invite =
        db.sql(
                "SELECT * FROM invitations WHERE token_hash=:hash AND status='PENDING' AND"
                    + " expires_at>now() FOR UPDATE")
            .param("hash", SessionService.hash(r.token()))
            .query(
                (rs, n) ->
                    new Pending(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getObject("provider_id", UUID.class)))
            .optional()
            .orElseThrow(ApiFailure::notFound);
    UUID id = UUID.randomUUID();
    if (invite.provider() != null)
      catalog.lockProvider(new Actor(id, invite.tenant(), "ADMIN", null), invite.provider());
    db.sql(
            "INSERT INTO users(id,tenant_id,email,password_hash,role,display_name)"
                + " VALUES(:id,:tenant,:email,:hash,:role,:name)")
        .param("id", id)
        .param("tenant", invite.tenant())
        .param("email", invite.email())
        .param("hash", password)
        .param("role", invite.role())
        .param("name", r.displayName().trim())
        .update();
    if (invite.provider() != null
        && db.sql(
                    "UPDATE providers SET user_id=:user WHERE tenant_id=:tenant AND id=:id AND"
                        + " user_id IS NULL")
                .param("user", id)
                .param("tenant", invite.tenant())
                .param("id", invite.provider())
                .update()
            != 1) throw ApiFailure.conflict("Provider already has an account");
    db.sql("UPDATE invitations SET status='ACCEPTED' WHERE id=:id")
        .param("id", invite.id())
        .update();
    return id;
  }
}
