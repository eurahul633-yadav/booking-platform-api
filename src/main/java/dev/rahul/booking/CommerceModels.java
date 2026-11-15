package dev.rahul.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class CommerceModels {
  private CommerceModels() {}

  public record StudentInput(
      @NotBlank @Size(max = 120) String displayName,
      @PastOrPresent LocalDate dateOfBirth,
      @NotNull @Size(max = 40) String gender,
      boolean selfBookingEnabled) {}

  public record Student(
      UUID id,
      UUID guardianId,
      UUID userId,
      String displayName,
      LocalDate dateOfBirth,
      String gender,
      boolean selfBookingEnabled,
      boolean loginEnabled,
      boolean active) {}

  public record StudentLogin(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Pricing(
      @Min(0) @Max(1000000000000L) long priceMinor,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

  public record PaymentSettings(boolean cashPayEnabled, boolean walletPayEnabled) {}

  public record WalletAdjustment(
      @NotNull UUID ownerId,
      @NotNull UUID providerId,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @Min(1) @Max(1000000000000L) long amountMinor,
      @NotBlank @Size(max = 500) String reason,
      @NotBlank @Pattern(regexp = "CREDIT|DEBIT") String direction) {}

  public record Wallet(
      UUID id, UUID ownerId, UUID providerId, String currency, long balanceMinor) {}

  public record WalletEntry(
      UUID id,
      UUID transferId,
      long deltaMinor,
      String kind,
      String reason,
      UUID referenceId,
      Instant createdAt) {}

  public record Checkout(
      @NotEmpty @Size(max = 50) List<Models.@Valid @NotNull BookingRequest> bookings,
      @NotBlank @Pattern(regexp = "WALLET|CASH|WALLET_CASH") String paymentMethod,
      @Min(0) @Max(1000000000000L) Long expectedTotalMinor,
      @Pattern(regexp = "[A-Z]{3}") String expectedCurrency) {}

  public record Quote(
      UUID payerId,
      UUID providerId,
      String currency,
      long totalMinor,
      long availableWalletMinor,
      List<Long> lineAmountsMinor,
      List<String> allowedMethods) {}

  public record OrderItem(
      UUID bookingId,
      long amountMinor,
      long walletPaidMinor,
      long cashDueMinor,
      long cashCollectedMinor,
      long refundedMinor,
      boolean cancelled) {}

  public record Order(
      UUID id,
      UUID customerId,
      UUID bookedBy,
      UUID providerId,
      String currency,
      String paymentMethod,
      String paymentStatus,
      long totalMinor,
      long walletPaidMinor,
      long cashDueMinor,
      long cashCollectedMinor,
      long refundedMinor,
      List<OrderItem> items) {}

  public record Enrolment(UUID participantId) {}

  public record SessionEnrolment(UUID sessionId, UUID participantId) {}

  public record CashReceipt(@Min(1) @Max(1000000000000L) long amountMinor) {}
}
