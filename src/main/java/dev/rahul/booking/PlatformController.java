package dev.rahul.booking;

import static dev.rahul.booking.PlatformModels.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class PlatformController {
  private final BusinessService business;
  private final SchedulingService scheduling;
  private final BookingService bookings;
  private final IdentityService identity;
  private final NotificationService notifications;

  public PlatformController(
      BusinessService business,
      SchedulingService scheduling,
      BookingService bookings,
      IdentityService identity,
      NotificationService notifications) {
    this.business = business;
    this.scheduling = scheduling;
    this.bookings = bookings;
    this.identity = identity;
    this.notifications = notifications;
  }

  @GetMapping("/business/settings")
  public Settings settings(@AuthenticationPrincipal Actor a) {
    return business.settings(a.tenantId());
  }

  @PutMapping("/business/settings")
  public Settings configure(@AuthenticationPrincipal Actor a, @Valid @RequestBody Settings r) {
    return business.configure(a, r);
  }

  @GetMapping("/locations")
  public List<Location> locations(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return business.locations(a, page, size);
  }

  @PostMapping("/locations")
  @ResponseStatus(HttpStatus.CREATED)
  public Location location(@AuthenticationPrincipal Actor a, @Valid @RequestBody LocationInput r) {
    return business.createLocation(a, r);
  }

  @PutMapping("/locations/{id}")
  public Location updateLocation(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody LocationInput r) {
    return business.updateLocation(a, id, r);
  }

  @GetMapping("/locations/{id}/rooms")
  public List<Room> rooms(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return business.rooms(a, id, page, size);
  }

  @PostMapping("/locations/{id}/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public Room room(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @Valid @RequestBody RoomInput r) {
    return business.createRoom(a, id, r);
  }

  @PutMapping("/providers/{id}/services")
  public Assignment assign(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @Valid @RequestBody Assignment r) {
    return scheduling.assign(a, id, r);
  }

  @DeleteMapping("/providers/{id}/availability/{window}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWindow(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @PathVariable UUID window) {
    scheduling.deleteWindow(a, id, window);
  }

  @PostMapping("/providers/{id}/blockouts")
  @ResponseStatus(HttpStatus.CREATED)
  public Blockout block(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody Models.AvailabilityRequest r) {
    return scheduling.block(a, id, r);
  }

  @GetMapping("/providers/{id}/blockouts")
  public List<Blockout> blocks(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestParam Instant from,
      @RequestParam Instant to) {
    return scheduling.blocks(a, id, from, to);
  }

  @DeleteMapping("/providers/{id}/blockouts/{block}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @PathVariable UUID block) {
    scheduling.unblock(a, id, block);
  }

  @PostMapping("/bookings/{id}/reschedule")
  public Models.Booking reschedule(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody Reschedule r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return bookings.reschedule(a, id, r, key);
  }

  @PostMapping("/bookings/batch")
  @ResponseStatus(HttpStatus.CREATED)
  public List<Models.Booking> batch(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody Batch r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return bookings.batch(a, r, key);
  }

  @GetMapping("/sessions")
  public Models.Page<LessonSession> sessions(
      @AuthenticationPrincipal Actor a,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return scheduling.sessions(a, from, to, page, size);
  }

  @GetMapping("/sessions/{id}")
  public LessonSession session(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return scheduling.session(a, id);
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public LessonSession createSession(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody SessionInput r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return scheduling.create(a, r, key);
  }

  @PostMapping("/session-series")
  @ResponseStatus(HttpStatus.CREATED)
  public List<LessonSession> series(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody SeriesInput r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return scheduling.series(a, r, key);
  }

  @PostMapping("/sessions/{id}/enrolments")
  @ResponseStatus(HttpStatus.CREATED)
  public Models.Booking enrol(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return scheduling.enrol(a, id, key);
  }

  @PostMapping("/sessions/{id}/cancel")
  public LessonSession cancelSession(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return scheduling.cancelSession(a, id);
  }

  @GetMapping("/me/profile")
  public User me(@AuthenticationPrincipal Actor a) {
    return identity.me(a);
  }

  @PutMapping("/me/profile")
  public User profile(@AuthenticationPrincipal Actor a, @Valid @RequestBody Profile r) {
    return identity.profile(a, r);
  }

  @PostMapping("/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void password(@AuthenticationPrincipal Actor a, @Valid @RequestBody ChangePassword r) {
    identity.password(a, r);
  }

  @GetMapping("/users")
  public Models.Page<User> users(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return identity.users(a, page, size);
  }

  @GetMapping("/users/{id}")
  public User user(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return identity.user(a, id);
  }

  @PostMapping("/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  public IssuedInvite invite(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody InviteInput r,
      HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return identity.invite(a, r);
  }

  @GetMapping("/invitations")
  public Models.Page<Invite> invites(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return identity.invitations(a, page, size);
  }

  @PostMapping("/invitations/{id}/revoke")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    identity.revoke(a, id);
  }

  @GetMapping("/notifications")
  public Models.Page<Notification> notifications(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return notifications.notifications(a, page, size);
  }

  @PostMapping("/notifications/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    notifications.read(a, id);
  }

  @GetMapping("/booking-events")
  public Models.Page<AuditEvent> events(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return notifications.events(a, page, size);
  }

  @GetMapping("/capabilities")
  public Map<String, Object> capabilities() {
    return Map.of(
        "version",
        "0.2.0",
        "features",
        List.of(
            "tenant-settings",
            "provider-accounts",
            "invitations",
            "locations",
            "rooms",
            "provider-service-assignments",
            "availability",
            "blockouts",
            "private-booking",
            "batch-booking",
            "rescheduling",
            "group-sessions",
            "weekly-session-series",
            "notifications",
            "audit-events"),
        "legacyRouteAliases",
        false,
        "externalPaymentsEnabled",
        false);
  }
}
