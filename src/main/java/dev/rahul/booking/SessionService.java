package dev.rahul.booking;

import static dev.rahul.booking.Models.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
  private final JdbcClient db;
  private final PasswordEncoder passwords;
  private final SecureRandom random = new SecureRandom();
  private final String dummyHash;

  public SessionService(JdbcClient db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
    this.dummyHash = passwords.encode("not-a-real-account-password");
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String encode(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiFailure.invalid("Password must be at most 72 UTF-8 bytes");
    return passwords.encode(password);
  }

  @Transactional
  public Map<String, UUID> workspace(WorkspaceRequest request) {
    String password = encode(request.password());
    var tenant = UUID.randomUUID();
    var user = UUID.randomUUID();
    db.sql("INSERT INTO tenants(id,slug,name) VALUES(:id,:slug,:name)")
        .param("id", tenant)
        .param("slug", request.slug())
        .param("name", request.name().trim())
        .update();
    insertUser(user, tenant, request.email(), password, "ADMIN");
    return Map.of("tenantId", tenant, "userId", user);
  }

  @Transactional
  public UUID register(RegisterRequest r) {
    var password = encode(r.password());
    var tenant = tenant(r.tenantSlug());
    boolean signup =
        db.sql("SELECT customer_signup_enabled FROM tenant_settings WHERE tenant_id=:tenant")
            .param("tenant", tenant)
            .query(Boolean.class)
            .optional()
            .orElse(true);
    if (!signup) throw ApiFailure.forbidden();
    var id = UUID.randomUUID();
    insertUser(id, tenant, r.email(), password, "CUSTOMER");
    return id;
  }

  private void insertUser(UUID id, UUID tenant, String email, String hash, String role) {
    db.sql(
            "INSERT INTO users(id,tenant_id,email,password_hash,role)"
                + " VALUES(:id,:tenant,:email,:hash,:role)")
        .param("id", id)
        .param("tenant", tenant)
        .param("email", email.trim().toLowerCase(Locale.ROOT))
        .param("hash", hash)
        .param("role", role)
        .update();
  }

  UUID tenant(String slug) {
    return db.sql("SELECT id FROM tenants WHERE slug=:slug")
        .param("slug", slug)
        .query(UUID.class)
        .optional()
        .orElseThrow(ApiFailure::notFound);
  }

  record Credentials(UUID id, String passwordHash) {}

  public record Issued(String accessToken, String tokenType, long expiresIn) {}

  public Issued login(LoginRequest r, String kind) {
    var credentials =
        db.sql(
                "SELECT u.id,u.password_hash FROM users u JOIN tenants t ON t.id=u.tenant_id WHERE"
                    + " t.slug=:slug AND u.email=:email")
            .param("slug", r.tenantSlug())
            .param("email", r.email().trim().toLowerCase(Locale.ROOT))
            .query(
                (rs, n) ->
                    new Credentials(rs.getObject("id", UUID.class), rs.getString("password_hash")))
            .optional();
    boolean withinLimit = r.password().getBytes(StandardCharsets.UTF_8).length <= 72;
    boolean valid =
        passwords.matches(
                withinLimit ? r.password() : "invalid-overlength-password",
                credentials.map(Credentials::passwordHash).orElse(dummyHash))
            && withinLimit;
    if (!valid || credentials.isEmpty()) throw ApiFailure.unauthorized();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    long seconds = "COOKIE".equals(kind) ? 28800 : 3600;
    db.sql(
            "INSERT INTO auth_sessions(id,user_id,token_hash,kind,expires_at)"
                + " VALUES(:id,:user,:hash,:kind,:expires)")
        .param("id", UUID.randomUUID())
        .param("user", credentials.get().id())
        .param("hash", hash(token))
        .param("kind", kind)
        .param("expires", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(seconds))
        .update();
    return new Issued(token, "Bearer", seconds);
  }

  public Optional<Actor> authenticate(String token, String kind) {
    if (token == null || token.length() != 43) return Optional.empty();
    return db.sql(
            "SELECT u.id,u.tenant_id,u.role,s.id AS session_id FROM auth_sessions s JOIN users u ON"
                + " u.id=s.user_id WHERE s.token_hash=:hash AND s.kind=:kind AND"
                + " s.expires_at>now()")
        .param("hash", hash(token))
        .param("kind", kind)
        .query(
            (rs, n) ->
                new Actor(
                    rs.getObject("id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("role"),
                    rs.getObject("session_id", UUID.class)))
        .optional();
  }

  public void logout(Actor actor) {
    db.sql("DELETE FROM auth_sessions WHERE id=:id").param("id", actor.sessionId()).update();
  }

  public void logoutAll(Actor actor) {
    db.sql("DELETE FROM auth_sessions WHERE user_id=:id").param("id", actor.userId()).update();
  }

  @Scheduled(fixedDelay = 3600000)
  public void cleanup() {
    db.sql("DELETE FROM auth_sessions WHERE expires_at<now()").update();
  }
}
