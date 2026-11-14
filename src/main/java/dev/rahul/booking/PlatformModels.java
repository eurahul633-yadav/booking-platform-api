package dev.rahul.booking;

import dev.rahul.booking.Models.BookingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class PlatformModels {
  private PlatformModels() {}

  public record Settings(
      @Min(1) @Max(730) int bookingHorizonDays,
      @Min(0) @Max(43200) int minimumNoticeMinutes,
      boolean customerSignupEnabled) {}

  public record LocationInput(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 500) String address,
      @NotBlank @Size(max = 60) String timeZone) {}

  public record Location(UUID id, String name, String address, String timeZone) {}

  public record RoomInput(@NotBlank @Size(max = 120) String name, @Min(1) @Max(500) int capacity) {}

  public record Room(UUID id, UUID locationId, String name, int capacity) {}

  public record Assignment(@NotNull @Size(max = 100) Set<@NotNull UUID> serviceIds) {}

  public record Reschedule(@NotNull Instant startsAt) {}

  public record Batch(@NotEmpty @Size(max = 50) List<@NotNull @Valid BookingRequest> bookings) {}

  public record SessionInput(
      @NotNull UUID providerId,
      @NotNull UUID serviceId,
      UUID roomId,
      @NotNull Instant startsAt,
      @Min(1) @Max(100) int capacity) {}

  public record SeriesInput(
      @NotNull UUID providerId,
      @NotNull UUID serviceId,
      UUID roomId,
      @NotNull Instant startsAt,
      @Min(1) @Max(100) int capacity,
      @Min(1) @Max(26) int occurrences,
      @Min(1) @Max(12) int intervalWeeks) {}

  public record LessonSession(
      UUID id,
      UUID providerId,
      UUID serviceId,
      UUID roomId,
      UUID seriesId,
      Instant startsAt,
      Instant endsAt,
      int capacity,
      int bookedSeats,
      String status) {}

  public record Blockout(UUID id, Instant startsAt, Instant endsAt) {}

  public record InviteInput(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "PROVIDER|CUSTOMER|GUARDIAN") String role,
      UUID providerId) {}

  public record Invite(
      UUID id, String email, String role, UUID providerId, String status, Instant expiresAt) {}

  public record IssuedInvite(UUID id, String token, Instant expiresAt) {}

  public record AcceptInvite(
      @NotBlank @Size(min = 43, max = 43) String token,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 120) String displayName) {}

  public record Profile(@NotBlank @Size(max = 120) String displayName) {}

  public record User(UUID id, String email, String role, String displayName) {}

  public record ChangePassword(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record Notification(
      UUID id, UUID bookingId, String eventType, Instant createdAt, Instant readAt) {}

  public record AuditEvent(
      UUID id, UUID bookingId, String eventType, UUID actorId, Instant createdAt) {}
}
