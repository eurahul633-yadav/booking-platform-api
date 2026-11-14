package dev.rahul.booking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "app.rate-limit-enabled=false",
      "app.cache-enabled=false",
      "app.cookie-secure=false",
      "springdoc.api-docs.enabled=true"
    })
@AutoConfigureMockMvc
class BookingApiTest {
  static final EmbeddedPostgres PG;

  static {
    try {
      PG = EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "postgres");
    r.add("management.health.redis.enabled", () -> false);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcClient db;
  static final String PASSWORD = "correct-horse-battery-staple";

  record Fixture(
      String slug, String admin, String customer, String provider, String service, Instant start) {}

  private org.springframework.test.web.servlet.request.RequestPostProcessor actualCsrf()
      throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    Cookie cookie = response.getCookie("XSRF-TOKEN");
    String token = json.readTree(response.getContentAsString()).get("token").asText();
    assertThat(cookie).isNotNull();
    return request -> {
      var cookies = new java.util.ArrayList<Cookie>();
      if (request.getCookies() != null)
        cookies.addAll(java.util.Arrays.asList(request.getCookies()));
      cookies.add(cookie);
      request.setCookies(cookies.toArray(Cookie[]::new));
      request.addHeader("X-XSRF-TOKEN", token);
      return request;
    };
  }

  JsonNode postJson(String path, Object body, String token, int status) throws Exception {
    var builder =
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    if (token == null) builder.with(actualCsrf());
    else builder.header("Authorization", "Bearer " + token);
    var response = mvc.perform(builder).andExpect(status().is(status)).andReturn().getResponse();
    return response.getContentAsString().isBlank()
        ? json.createObjectNode()
        : json.readTree(response.getContentAsString());
  }

  String login(String slug, String email) throws Exception {
    return postJson(
            "/api/v1/auth/token",
            Map.of("tenantSlug", slug, "email", email, "password", PASSWORD),
            null,
            200)
        .get("accessToken")
        .asText();
  }

  Fixture fixture() throws Exception {
    String slug = "test-" + UUID.randomUUID();
    postJson(
        "/api/v1/auth/workspaces",
        Map.of("slug", slug, "name", "Demo", "email", "owner@example.test", "password", PASSWORD),
        null,
        201);
    String admin = login(slug, "owner@example.test");
    postJson(
        "/api/v1/auth/register",
        Map.of("tenantSlug", slug, "email", "customer@example.test", "password", PASSWORD),
        null,
        201);
    String customer = login(slug, "customer@example.test");
    String provider =
        postJson(
                "/api/v1/providers",
                Map.of("name", "Coach", "timeZone", "Europe/Vilnius"),
                admin,
                201)
            .get("id")
            .asText();
    String service =
        postJson(
                "/api/v1/services",
                Map.of("name", "Private lesson", "durationMinutes", 30, "cancellationHours", 24),
                admin,
                201)
            .get("id")
            .asText();
    Instant start =
        Instant.now().plus(Duration.ofDays(5)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    postJson(
        "/api/v1/providers/" + provider + "/availability",
        Map.of("startsAt", start, "endsAt", start.plus(Duration.ofHours(3))),
        admin,
        201);
    return new Fixture(slug, admin, customer, provider, service, start);
  }

  MvcResult book(Fixture f, String token, String key, Instant start) throws Exception {
    return mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "providerId",
                            f.provider(),
                            "serviceId",
                            f.service(),
                            "startsAt",
                            start))))
        .andReturn();
  }

  String bookingId(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void idempotentBookingCancellationAndAdjacentSlots() throws Exception {
    var f = fixture();
    var first = book(f, f.customer(), "request-0001", f.start());
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    var repeat = book(f, f.customer(), "request-0001", f.start());
    assertThat(bookingId(repeat)).isEqualTo(bookingId(first));
    assertThat(
            book(f, f.customer(), "request-0001", f.start().plusSeconds(1800))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            book(f, f.customer(), "request-0002", f.start().plusSeconds(1800))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    String id = bookingId(first);
    assertThat(
            postJson("/api/v1/bookings/" + id + "/cancel", Map.of(), f.customer(), 200)
                .get("status")
                .asText())
        .isEqualTo("CANCELLED");
    postJson("/api/v1/bookings/" + id + "/cancel", Map.of(), f.customer(), 200);
    assertThat(book(f, f.customer(), "request-0003", f.start()).getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void competingCustomersCannotDoubleBook() throws Exception {
    var f = fixture();
    postJson(
        "/api/v1/auth/register",
        Map.of("tenantSlug", f.slug(), "email", "other@example.test", "password", PASSWORD),
        null,
        201);
    String other = login(f.slug(), "other@example.test");
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                return book(f, f.customer(), "concurrent-001", f.start()).getResponse().getStatus();
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                return book(f, other, "concurrent-002", f.start()).getResponse().getStatus();
              });
      assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void simultaneousRetriesReturnSameBooking() throws Exception {
    var f = fixture();
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                return book(f, f.customer(), "same-key-001", f.start());
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                return book(f, f.customer(), "same-key-001", f.start());
              });
      var r1 = one.get(15, TimeUnit.SECONDS);
      var r2 = two.get(15, TimeUnit.SECONDS);
      assertThat(r1.getResponse().getStatus()).isEqualTo(201);
      assertThat(r2.getResponse().getStatus()).isEqualTo(201);
      assertThat(bookingId(r1)).isEqualTo(bookingId(r2));
    }
  }

  @Test
  void tenantAndCustomerIsolationAndRoles() throws Exception {
    var f = fixture();
    var alien = fixture();
    String id = bookingId(book(f, f.customer(), "isolation-01", f.start()));
    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + alien.admin()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/providers/" + f.provider() + "/slots")
                .param("serviceId", alien.service())
                .param("from", f.start().toString())
                .param("to", f.start().plusSeconds(7200).toString())
                .header("Authorization", "Bearer " + f.customer()))
        .andExpect(status().isNotFound());
    assertThat(book(f, alien.customer(), "cross-tenant1", f.start()).getResponse().getStatus())
        .isEqualTo(404);
    postJson(
        "/api/v1/providers", Map.of("name", "Forbidden", "timeZone", "UTC"), f.customer(), 403);
    postJson(
        "/api/v1/auth/register",
        Map.of("tenantSlug", f.slug(), "email", "second@example.test", "password", PASSWORD),
        null,
        201);
    String second = login(f.slug(), "second@example.test");
    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + second))
        .andExpect(status().isNotFound());
  }

  @Test
  void sessionCookiesRequireCsrfAndAreRevocable() throws Exception {
    var f = fixture();
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(actualCsrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "tenantSlug",
                                f.slug(),
                                "email",
                                "customer@example.test",
                                "password",
                                PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    String setCookie = login.getHeader("Set-Cookie");
    assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Max-Age=28800");
    String value = setCookie.split(";", 2)[0].split("=", 2)[1];
    var cookie = new Cookie("BOOKING_SESSION", value);
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isOk());
    // Invalid bearer must never fall back to a valid cookie, bypassing CSRF.
    mvc.perform(
            post("/api/v1/auth/logout").header("Authorization", "Bearer invalid").cookie(cookie))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/logout").cookie(cookie).with(actualCsrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void tokenHashesExpiryAndLogoutAll() throws Exception {
    var f = fixture();
    String hash = SessionService.hash(f.customer());
    assertThat(
            db.sql("SELECT count(*) FROM auth_sessions WHERE token_hash=:hash")
                .param("hash", hash)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    postJson("/api/v1/auth/logout-all", Map.of(), f.customer(), 204);
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + f.customer()))
        .andExpect(status().isUnauthorized());
    String token = login(f.slug(), "customer@example.test");
    db.sql("UPDATE auth_sessions SET expires_at=now()-interval '1 second' WHERE token_hash=:hash")
        .param("hash", SessionService.hash(token))
        .update();
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void slotsRespectAvailabilityAndConfirmedBookings() throws Exception {
    var f = fixture();
    book(f, f.customer(), "slot-query01", f.start());
    var result =
        mvc.perform(
                get("/api/v1/providers/" + f.provider() + "/slots")
                    .header("Authorization", "Bearer " + f.customer())
                    .param("serviceId", f.service())
                    .param("from", f.start().toString())
                    .param("to", f.start().plusSeconds(10800).toString()))
            .andExpect(status().isOk())
            .andReturn();
    var slots = json.readTree(result.getResponse().getContentAsString());
    assertThat(slots.size()).isEqualTo(5);
    assertThat(slots.get(0).asText()).isEqualTo(f.start().plusSeconds(1800).toString());
    assertThat(
            book(f, f.customer(), "outside-window", f.start().minusSeconds(1800))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void cancellationDeadlineAndInputValidation() throws Exception {
    var f = fixture();
    String id = bookingId(book(f, f.customer(), "cancel-deadline", f.start()));
    db.sql("UPDATE bookings SET cancellation_hours=720 WHERE id=:id")
        .param("id", UUID.fromString(id))
        .update();
    postJson("/api/v1/bookings/" + id + "/cancel", Map.of(), f.customer(), 409);
    postJson(
        "/api/v1/services",
        Map.of("name", "Invalid", "durationMinutes", 0, "cancellationHours", 0),
        f.admin(),
        400);
    postJson(
        "/api/v1/providers", Map.of("name", "Invalid", "timeZone", "Not/AZone"), f.admin(), 400);
    postJson(
        "/api/v1/auth/token",
        Map.of("tenantSlug", f.slug(), "email", "customer@example.test", "password", "wrong"),
        null,
        401);
    mvc.perform(
            get("/api/v1/bookings")
                .header("Authorization", "Bearer " + f.customer())
                .param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
  }

  @Test
  void actualCsrfCookieBootstrapWorks() throws Exception {
    var f = fixture();
    var bootstrap =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    String csrfToken = json.readTree(bootstrap.getContentAsString()).get("token").asText();
    Cookie cookie = bootstrap.getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    byte[] credentials =
        json.writeValueAsBytes(
            Map.of("tenantSlug", f.slug(), "email", "customer@example.test", "password", PASSWORD));
    mvc.perform(
            post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).content(credentials))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/token")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials))
        .andExpect(status().isOk());
  }

  @Test
  void customerCannotBookOverlappingAppointmentsAcrossProviders() throws Exception {
    var f = fixture();
    assertThat(book(f, f.customer(), "customer-overlap1", f.start()).getResponse().getStatus())
        .isEqualTo(201);
    String provider =
        postJson(
                "/api/v1/providers",
                Map.of("name", "Second coach", "timeZone", "UTC"),
                f.admin(),
                201)
            .get("id")
            .asText();
    postJson(
        "/api/v1/providers/" + provider + "/availability",
        Map.of("startsAt", f.start(), "endsAt", f.start().plusSeconds(7200)),
        f.admin(),
        201);
    var second = new Fixture(f.slug(), f.admin(), f.customer(), provider, f.service(), f.start());
    assertThat(book(second, f.customer(), "customer-overlap2", f.start()).getResponse().getStatus())
        .isEqualTo(409);
  }

  @Test
  void openApiIsGenerated() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/bookings']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/bookings'].post.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/token'].post.security").doesNotExist());
  }
}
