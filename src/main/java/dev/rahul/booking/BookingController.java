package dev.rahul.booking;

import static dev.rahul.booking.Models.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class BookingController {
  private final CatalogService catalog;
  private final BookingService bookings;

  public BookingController(CatalogService catalog, BookingService bookings) {
    this.catalog = catalog;
    this.bookings = bookings;
  }

  @PostMapping("/providers")
  @ResponseStatus(HttpStatus.CREATED)
  public Provider provider(
      @AuthenticationPrincipal Actor a, @Valid @RequestBody ProviderRequest r) {
    return catalog.addProvider(a, r);
  }

  @GetMapping("/providers")
  public List<Provider> providers(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.providers(a, page, size);
  }

  @PostMapping("/services")
  @ResponseStatus(HttpStatus.CREATED)
  public Models.Service service(
      @AuthenticationPrincipal Actor a, @Valid @RequestBody ServiceRequest r) {
    return catalog.addService(a, r);
  }

  @GetMapping("/services")
  public List<Models.Service> services(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.services(a, page, size);
  }

  @PostMapping("/providers/{id}/availability")
  @ResponseStatus(HttpStatus.CREATED)
  public Window window(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody AvailabilityRequest r) {
    return catalog.addWindow(a, id, r);
  }

  @GetMapping("/providers/{id}/availability")
  public List<Window> windows(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestParam Instant from,
      @RequestParam Instant to) {
    return catalog.windows(a, id, from, to);
  }

  @GetMapping("/providers/{id}/slots")
  public List<Instant> slots(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @RequestParam UUID serviceId,
      @RequestParam Instant from,
      @RequestParam Instant to) {
    return bookings.slots(a, id, serviceId, from, to);
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  public Booking create(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody BookingRequest r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return bookings.createFree(a, r, key);
  }

  @GetMapping("/bookings/{id}")
  public Booking get(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return bookings.get(a, id);
  }

  @GetMapping("/bookings")
  public Page<Booking> list(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return bookings.list(a, page, size);
  }

  @PostMapping("/bookings/{id}/cancel")
  public Booking cancel(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return bookings.cancel(a, id);
  }
}
