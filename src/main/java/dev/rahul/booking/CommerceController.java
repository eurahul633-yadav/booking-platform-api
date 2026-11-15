package dev.rahul.booking;

import static dev.rahul.booking.CommerceModels.*;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class CommerceController {
  private final ParticipantService participants;
  private final LedgerService ledger;
  private final CommerceService commerce;

  public CommerceController(
      ParticipantService participants, LedgerService ledger, CommerceService commerce) {
    this.participants = participants;
    this.ledger = ledger;
    this.commerce = commerce;
  }

  @GetMapping("/participants/me")
  public Student participant(@AuthenticationPrincipal Actor a) {
    return participants.own(a);
  }

  @GetMapping("/managed-students")
  public Models.Page<Student> students(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return participants.list(a, page, size);
  }

  @PostMapping("/managed-students")
  @ResponseStatus(HttpStatus.CREATED)
  public Student student(@AuthenticationPrincipal Actor a, @Valid @RequestBody StudentInput r) {
    return participants.create(a, r);
  }

  @GetMapping("/managed-students/{id}")
  public Student student(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return participants.get(a, id);
  }

  @PutMapping("/managed-students/{id}")
  public Student update(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @Valid @RequestBody StudentInput r) {
    return participants.update(a, id, r);
  }

  @PostMapping("/managed-students/{id}/login")
  public Student login(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @Valid @RequestBody StudentLogin r) {
    return participants.login(a, id, r);
  }

  @DeleteMapping("/managed-students/{id}/login")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disableLogin(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    participants.disableLogin(a, id);
  }

  @PostMapping("/managed-students/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    participants.archive(a, id);
  }

  @PutMapping("/services/{id}/pricing")
  public Models.Service pricing(
      @AuthenticationPrincipal Actor a, @PathVariable UUID id, @Valid @RequestBody Pricing r) {
    return commerce.price(a, id, r);
  }

  @GetMapping("/providers/{id}/payment-settings")
  public PaymentSettings settings(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return commerce.settings(a, id);
  }

  @PutMapping("/providers/{id}/payment-settings")
  public PaymentSettings configure(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody PaymentSettings r) {
    return commerce.configure(a, id, r);
  }

  @GetMapping("/wallet")
  public Wallet wallet(
      @AuthenticationPrincipal Actor a,
      @RequestParam UUID providerId,
      @RequestParam String currency,
      @RequestParam(required = false) UUID participantId) {
    var p = participants.resolve(a, participantId);
    return ledger.balance(a, participants.payer(p), providerId, currency);
  }

  @GetMapping("/wallet/transactions")
  public Models.Page<WalletEntry> history(
      @AuthenticationPrincipal Actor a,
      @RequestParam UUID providerId,
      @RequestParam String currency,
      @RequestParam(required = false) UUID participantId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var p = participants.resolve(a, participantId);
    return ledger.history(a, participants.payer(p), providerId, currency, page, size);
  }

  @PostMapping("/wallet-adjustments")
  public Wallet adjust(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody WalletAdjustment r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ledger.adjust(a, r, key);
  }

  @PostMapping("/checkout-quotes")
  public Quote quote(@AuthenticationPrincipal Actor a, @Valid @RequestBody Checkout r) {
    return commerce.quote(a, r);
  }

  @PostMapping("/checkouts")
  @ResponseStatus(HttpStatus.CREATED)
  public Order checkout(
      @AuthenticationPrincipal Actor a,
      @Valid @RequestBody Checkout r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return commerce.checkout(a, r, key);
  }

  @GetMapping("/orders/{id}")
  public Order order(@AuthenticationPrincipal Actor a, @PathVariable UUID id) {
    return commerce.order(a, id);
  }

  @GetMapping("/orders")
  public Models.Page<Order> orders(
      @AuthenticationPrincipal Actor a,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return commerce.orders(a, page, size);
  }

  @PostMapping("/bookings/{id}/cash-receipts")
  public Order receipt(
      @AuthenticationPrincipal Actor a,
      @PathVariable UUID id,
      @Valid @RequestBody CashReceipt r,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return commerce.receipt(a, id, r, key);
  }
}
