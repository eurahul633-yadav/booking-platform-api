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

  JsonNode command(String path, Object body, String token, String key, int expected)
      throws Exception {
    var result =
        mvc.perform(
                post(path)
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  JsonNode getJson(String path, String token, int expected) throws Exception {
    var result =
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  JsonNode putJson(String path, Object body, String token, int expected) throws Exception {
    var result =
        mvc.perform(
                put(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  String secondCustomer(Fixture f) throws Exception {
    String email = "other-" + UUID.randomUUID() + "@example.test";
    postJson(
        "/api/v1/auth/register",
        Map.of("tenantSlug", f.slug(), "email", email, "password", PASSWORD),
        null,
        201);
    return login(f.slug(), email);
  }

  String room(Fixture f, int capacity) throws Exception {
    var location =
        postJson(
            "/api/v1/locations",
            Map.of("name", "Studio", "address", "Synthetic address", "timeZone", "Europe/Vilnius"),
            f.admin(),
            201);
    return postJson(
            "/api/v1/locations/" + location.get("id").asText() + "/rooms",
            Map.of("name", "Room A", "capacity", capacity),
            f.admin(),
            201)
        .get("id")
        .asText();
  }

  Map<String, Object> sessionBody(Fixture f, int capacity, String room) {
    var body = new HashMap<String, Object>();
    body.put("providerId", f.provider());
    body.put("serviceId", f.service());
    body.put("startsAt", f.start());
    body.put("capacity", capacity);
    if (room != null) body.put("roomId", room);
    return body;
  }

  @Test
  void roomConflictsAreSharedAcrossProviders() throws Exception {
    var f = fixture();
    String room = room(f, 2), other = secondCustomer(f);
    String provider =
        postJson("/api/v1/providers", Map.of("name", "Other", "timeZone", "UTC"), f.admin(), 201)
            .get("id")
            .asText();
    postJson(
        "/api/v1/providers/" + provider + "/availability",
        Map.of("startsAt", f.start(), "endsAt", f.start().plusSeconds(7200)),
        f.admin(),
        201);
    var payload =
        new HashMap<String, Object>(
            Map.of(
                "providerId",
                f.provider(),
                "serviceId",
                f.service(),
                "startsAt",
                f.start(),
                "roomId",
                room));
    command("/api/v1/bookings", payload, f.customer(), "room-conflict1", 201);
    payload.put("providerId", provider);
    command("/api/v1/bookings", payload, other, "room-conflict2", 409);
    payload.put("startsAt", f.start().plusSeconds(1800));
    command("/api/v1/bookings", payload, other, "room-adjacent1", 201);
  }

  @Test
  void blockoutsAndWindowDeletionPreserveConfirmedAppointments() throws Exception {
    var f = fixture();
    String block =
        postJson(
                "/api/v1/providers/" + f.provider() + "/blockouts",
                Map.of("startsAt", f.start(), "endsAt", f.start().plusSeconds(1800)),
                f.admin(),
                201)
            .get("id")
            .asText();
    assertThat(book(f, f.customer(), "blocked-slot1", f.start()).getResponse().getStatus())
        .isEqualTo(409);
    mvc.perform(
            delete("/api/v1/providers/" + f.provider() + "/blockouts/" + block)
                .header("Authorization", "Bearer " + f.customer()))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/providers/" + f.provider() + "/blockouts/" + block)
                .header("Authorization", "Bearer " + f.admin()))
        .andExpect(status().isNoContent());
    assertThat(book(f, f.customer(), "unblocked-slot", f.start()).getResponse().getStatus())
        .isEqualTo(201);
    var windows =
        mvc.perform(
                get("/api/v1/providers/" + f.provider() + "/availability")
                    .header("Authorization", "Bearer " + f.admin())
                    .param("from", f.start().toString())
                    .param("to", f.start().plusSeconds(7200).toString()))
            .andExpect(status().isOk())
            .andReturn();
    String window =
        json.readTree(windows.getResponse().getContentAsString()).get(0).get("id").asText();
    mvc.perform(
            delete("/api/v1/providers/" + f.provider() + "/availability/" + window)
                .header("Authorization", "Bearer " + f.admin()))
        .andExpect(status().isConflict());
  }

  @Test
  void rescheduleIsAtomicAndOriginalCreateRetriesRemainSafe() throws Exception {
    var f = fixture();
    String other = secondCustomer(f);
    String first = bookingId(book(f, f.customer(), "original-create", f.start()));
    book(f, other, "reschedule-busy", f.start().plusSeconds(1800));
    command(
        "/api/v1/bookings/" + first + "/reschedule",
        Map.of("startsAt", f.start().plusSeconds(1800)),
        f.customer(),
        "reschedule-fail",
        409);
    assertThat(getJson("/api/v1/bookings/" + first, f.customer(), 200).get("startsAt").asText())
        .isEqualTo(f.start().toString());
    var moved =
        command(
            "/api/v1/bookings/" + first + "/reschedule",
            Map.of("startsAt", f.start().plusSeconds(3600)),
            f.customer(),
            "reschedule-good",
            200);
    assertThat(moved.get("startsAt").asText()).isEqualTo(f.start().plusSeconds(3600).toString());
    command(
        "/api/v1/bookings/" + first + "/reschedule",
        Map.of("startsAt", f.start().plusSeconds(3600)),
        f.customer(),
        "reschedule-good",
        200);
    var retry = book(f, f.customer(), "original-create", f.start());
    assertThat(bookingId(retry)).isEqualTo(first);
    assertThat(json.readTree(retry.getResponse().getContentAsString()).get("startsAt").asText())
        .isEqualTo(f.start().plusSeconds(3600).toString());
    assertThat(book(f, f.customer(), "after-move-free", f.start()).getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void batchRollsBackAllItemsAndReplaysSuccessfulResults() throws Exception {
    var f = fixture();
    var one = Map.of("providerId", f.provider(), "serviceId", f.service(), "startsAt", f.start());
    var bad =
        Map.of(
            "providerId",
            f.provider(),
            "serviceId",
            f.service(),
            "startsAt",
            f.start().minusSeconds(1800));
    command(
        "/api/v1/bookings/batch",
        Map.of("bookings", List.of(one, bad)),
        f.customer(),
        "batch-rollback",
        409);
    assertThat(getJson("/api/v1/bookings", f.customer(), 200).get("items").size()).isZero();
    var two =
        Map.of(
            "providerId",
            f.provider(),
            "serviceId",
            f.service(),
            "startsAt",
            f.start().plusSeconds(1800));
    var body = Map.of("bookings", List.of(one, two));
    var first = command("/api/v1/bookings/batch", body, f.customer(), "batch-success", 201);
    assertThat(command("/api/v1/bookings/batch", body, f.customer(), "batch-success", 201))
        .isEqualTo(first);
    assertThat(getJson("/api/v1/bookings", f.customer(), 200).get("items").size()).isEqualTo(2);
  }

  @Test
  void lastSeatRaceAndSeatReleaseAreSafe() throws Exception {
    var f = fixture();
    String other = secondCustomer(f);
    String id =
        command(
                "/api/v1/sessions",
                sessionBody(f, 1, room(f, 1)),
                f.admin(),
                "last-seat-session",
                201)
            .get("id")
            .asText();
    var barrier = new CyclicBarrier(2);
    var path = "/api/v1/sessions/" + id + "/enrolments";
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        post(path)
                            .header("Authorization", "Bearer " + f.customer())
                            .header("Idempotency-Key", "seat-customer1"))
                    .andReturn();
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                return mvc.perform(
                        post(path)
                            .header("Authorization", "Bearer " + other)
                            .header("Idempotency-Key", "seat-customer2"))
                    .andReturn();
              });
      var a = one.get(15, TimeUnit.SECONDS);
      var b = two.get(15, TimeUnit.SECONDS);
      assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 409);
      String winner = a.getResponse().getStatus() == 201 ? f.customer() : other;
      String loser = a.getResponse().getStatus() == 201 ? other : f.customer();
      String booking = bookingId(a.getResponse().getStatus() == 201 ? a : b);
      postJson("/api/v1/bookings/" + booking + "/cancel", Map.of(), winner, 200);
      command(path, Map.of(), loser, "seat-released1", 201);
    }
    assertThat(getJson("/api/v1/sessions/" + id, f.customer(), 200).get("bookedSeats").asInt())
        .isEqualTo(1);
  }

  @Test
  void groupSeatsShareOneReservationAndSessionCancellationNotifies() throws Exception {
    var f = fixture();
    String other = secondCustomer(f);
    String id =
        command("/api/v1/sessions", sessionBody(f, 2, room(f, 2)), f.admin(), "group-session1", 201)
            .get("id")
            .asText();
    var a =
        command(
            "/api/v1/sessions/" + id + "/enrolments", Map.of(), f.customer(), "group-enrol01", 201);
    command("/api/v1/sessions/" + id + "/enrolments", Map.of(), other, "group-enrol02", 201);
    assertThat(getJson("/api/v1/sessions/" + id, other, 200).get("bookedSeats").asInt())
        .isEqualTo(2);
    assertThat(book(f, secondCustomer(f), "private-v-group", f.start()).getResponse().getStatus())
        .isEqualTo(409);
    postJson("/api/v1/sessions/" + id + "/cancel", Map.of(), other, 403);
    assertThat(
            postJson("/api/v1/sessions/" + id + "/cancel", Map.of(), f.admin(), 200)
                .get("status")
                .asText())
        .isEqualTo("CANCELLED");
    assertThat(
            getJson("/api/v1/bookings/" + a.get("id").asText(), f.customer(), 200)
                .get("status")
                .asText())
        .isEqualTo("CANCELLED");
    assertThat(getJson("/api/v1/notifications", other, 200).get("items").size()).isEqualTo(2);
    String notification =
        getJson("/api/v1/notifications", other, 200).get("items").get(0).get("id").asText();
    postJson("/api/v1/notifications/" + notification + "/read", Map.of(), f.customer(), 404);
    postJson("/api/v1/notifications/" + notification + "/read", Map.of(), other, 204);
    assertThat(book(f, f.customer(), "after-session-cancel", f.start()).getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void weeklySeriesHasAtomicExpansionAndSafeReplay() throws Exception {
    var f = fixture();
    var body = new HashMap<String, Object>(sessionBody(f, 2, null));
    body.put("occurrences", 2);
    body.put("intervalWeeks", 1);
    command("/api/v1/session-series", body, f.admin(), "weekly-series1", 409);
    assertThat(
            db.sql("SELECT count(*) FROM lesson_sessions WHERE provider_id=:provider")
                .param("provider", UUID.fromString(f.provider()))
                .query(Long.class)
                .single())
        .isZero();
    Instant next = SchedulingService.weekly(f.start(), "Europe/Vilnius", 2, 1).get(1);
    postJson(
        "/api/v1/providers/" + f.provider() + "/availability",
        Map.of("startsAt", next, "endsAt", next.plusSeconds(7200)),
        f.admin(),
        201);
    var result = command("/api/v1/session-series", body, f.admin(), "weekly-series1", 201);
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).get("seriesId")).isEqualTo(result.get(1).get("seriesId"));
    assertThat(command("/api/v1/session-series", body, f.admin(), "weekly-series1", 201))
        .isEqualTo(result);
  }

  @Test
  void providerInvitationCreatesScopedProviderAccount() throws Exception {
    var f = fixture();
    var invite =
        postJson(
            "/api/v1/invitations",
            Map.of(
                "email", "provider@example.test", "role", "PROVIDER", "providerId", f.provider()),
            f.admin(),
            201);
    postJson(
        "/api/v1/auth/invitations/accept",
        Map.of(
            "token",
            invite.get("token").asText(),
            "password",
            PASSWORD,
            "displayName",
            "Coach account"),
        null,
        201);
    String provider = login(f.slug(), "provider@example.test");
    assertThat(getJson("/api/v1/auth/me", provider, 200).get("role").asText())
        .isEqualTo("PROVIDER");
    String id = bookingId(book(f, f.customer(), "provider-view1", f.start()));
    getJson("/api/v1/bookings/" + id, provider, 200);
    postJson(
        "/api/v1/providers/" + f.provider() + "/availability",
        Map.of("startsAt", f.start().plusSeconds(20000), "endsAt", f.start().plusSeconds(23000)),
        provider,
        201);
    String another =
        postJson(
                "/api/v1/providers", Map.of("name", "Unrelated", "timeZone", "UTC"), f.admin(), 201)
            .get("id")
            .asText();
    postJson(
        "/api/v1/providers/" + another + "/availability",
        Map.of("startsAt", f.start(), "endsAt", f.start().plusSeconds(7200)),
        provider,
        403);
    postJson("/api/v1/providers", Map.of("name", "Forbidden", "timeZone", "UTC"), provider, 403);
    assertThat(getJson("/api/v1/notifications", provider, 200).get("items").size()).isEqualTo(1);
    postJson(
        "/api/v1/auth/invitations/accept",
        Map.of(
            "token", invite.get("token").asText(), "password", PASSWORD, "displayName", "Replay"),
        null,
        404);
  }

  @Test
  void businessPoliciesAndProviderAssignmentsAreEnforced() throws Exception {
    var f = fixture();
    putJson(
        "/api/v1/business/settings",
        Map.of("bookingHorizonDays", 2, "minimumNoticeMinutes", 0, "customerSignupEnabled", false),
        f.customer(),
        403);
    putJson(
        "/api/v1/business/settings",
        Map.of("bookingHorizonDays", 2, "minimumNoticeMinutes", 0, "customerSignupEnabled", false),
        f.admin(),
        200);
    postJson(
        "/api/v1/auth/register",
        Map.of("tenantSlug", f.slug(), "email", "blocked@example.test", "password", PASSWORD),
        null,
        403);
    assertThat(book(f, f.customer(), "horizon-block", f.start()).getResponse().getStatus())
        .isEqualTo(400);
    putJson(
        "/api/v1/business/settings",
        Map.of("bookingHorizonDays", 366, "minimumNoticeMinutes", 0, "customerSignupEnabled", true),
        f.admin(),
        200);
    putJson(
        "/api/v1/providers/" + f.provider() + "/services",
        Map.of("serviceIds", List.of()),
        f.admin(),
        200);
    assertThat(book(f, f.customer(), "assignment-block", f.start()).getResponse().getStatus())
        .isEqualTo(409);
    putJson(
        "/api/v1/providers/" + f.provider() + "/services",
        Map.of("serviceIds", List.of(f.service())),
        f.admin(),
        200);
    assertThat(book(f, f.customer(), "assignment-ok1", f.start()).getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void revokedInvitationsAndPasswordChangesRevokeAccess() throws Exception {
    var f = fixture();
    var invite =
        postJson(
            "/api/v1/invitations",
            Map.of("email", "invited@example.test", "role", "CUSTOMER"),
            f.admin(),
            201);
    String raw = invite.get("token").asText();
    assertThat(
            db.sql("SELECT token_hash FROM invitations WHERE id=:id")
                .param("id", UUID.fromString(invite.get("id").asText()))
                .query(String.class)
                .single())
        .isEqualTo(SessionService.hash(raw));
    assertThat(getJson("/api/v1/invitations", f.admin(), 200).toString()).doesNotContain(raw);
    postJson(
        "/api/v1/invitations/" + invite.get("id").asText() + "/revoke", Map.of(), f.admin(), 204);
    postJson(
        "/api/v1/auth/invitations/accept",
        Map.of("token", raw, "password", PASSWORD, "displayName", "Invited"),
        null,
        404);
    String other = login(f.slug(), "customer@example.test");
    postJson(
        "/api/v1/me/password",
        Map.of("currentPassword", PASSWORD, "newPassword", "a-new-long-password-123"),
        f.customer(),
        204);
    getJson("/api/v1/auth/me", other, 401);
    getJson("/api/v1/auth/me", f.customer(), 401);
    postJson(
        "/api/v1/auth/token",
        Map.of("tenantSlug", f.slug(), "email", "customer@example.test", "password", PASSWORD),
        null,
        401);
    postJson(
        "/api/v1/auth/token",
        Map.of(
            "tenantSlug",
            f.slug(),
            "email",
            "customer@example.test",
            "password",
            "a-new-long-password-123"),
        null,
        200);
  }

  @Test
  void crossTenantRoomReferencesAndProfilePermissionsAreDenied() throws Exception {
    Fixture f = fixture(), other = fixture();
    String room = room(other, 2);
    command(
        "/api/v1/bookings",
        Map.of(
            "providerId",
            f.provider(),
            "serviceId",
            f.service(),
            "startsAt",
            f.start(),
            "roomId",
            room),
        f.customer(),
        "tenant-room-leak",
        404);
    assertThat(
            putJson("/api/v1/me/profile", Map.of("displayName", "New name"), f.customer(), 200)
                .get("displayName")
                .asText())
        .isEqualTo("New name");
    getJson("/api/v1/users", f.customer(), 403);
    getJson("/api/v1/users", f.admin(), 200);
    var user = getJson("/api/v1/me/profile", other.customer(), 200);
    getJson("/api/v1/users/" + user.get("id").asText(), f.admin(), 404);
  }

  @Test
  void oversizedJsonIsRejectedBeforeParsing() throws Exception {
    mvc.perform(
            post("/api/v1/auth/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(65537)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void revokedBrowserCookieDoesNotBlockCredentialLogin() throws Exception {
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
    var cookie = login.getCookie("BOOKING_SESSION");
    assertThat(cookie).isNotNull();
    postJson("/api/v1/auth/logout-all", Map.of(), f.customer(), 204);
    mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/csrf").cookie(cookie)).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(cookie)
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
        .andExpect(status().isOk());
  }
}
