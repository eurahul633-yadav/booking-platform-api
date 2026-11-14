package dev.rahul.booking;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class Models {
  private Models() {}

  public record WorkspaceRequest(
      @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,62}") String slug,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record RegisterRequest(
      @NotBlank @Size(max = 63) String tenantSlug,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record LoginRequest(
      @NotBlank @Size(max = 63) String tenantSlug,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 72) String password) {}

  public record ProviderRequest(
      @NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 60) String timeZone) {}

  public record ServiceRequest(
      @NotBlank @Size(max = 120) String name,
      @Min(5) @Max(480) int durationMinutes,
      @Min(0) @Max(720) int cancellationHours) {}

  public record AvailabilityRequest(@NotNull Instant startsAt, @NotNull Instant endsAt) {}

  public record BookingRequest(
      @NotNull UUID providerId, @NotNull UUID serviceId, @NotNull Instant startsAt, UUID roomId) {}

  public record Provider(UUID id, String name, String timeZone) {}

  public record Service(UUID id, String name, int durationMinutes, int cancellationHours) {}

  public record Window(UUID id, Instant startsAt, Instant endsAt) {}

  public record Booking(
      UUID id,
      UUID providerId,
      UUID serviceId,
      UUID customerId,
      Instant startsAt,
      Instant endsAt,
      String status,
      int cancellationHours,
      UUID roomId,
      UUID sessionId) {}

  public record Page<T>(List<T> items, int page, int size, boolean hasNext) {}
}
