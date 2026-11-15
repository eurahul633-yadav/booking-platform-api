package dev.rahul.booking;

import static dev.rahul.booking.CatalogService.*;
import static dev.rahul.booking.CommerceModels.*;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LedgerService {
  private final JdbcClient db;
  private final CatalogService catalog;
  private final Commands commands;

  public LedgerService(JdbcClient db, CatalogService catalog, Commands commands) {
    this.db = db;
    this.catalog = catalog;
    this.commands = commands;
  }

  public static String currency(String value) {
    try {
      return Currency.getInstance(value).getCurrencyCode();
    } catch (Exception e) {
      throw ApiFailure.invalid("Use a supported ISO 4217 currency");
    }
  }

  Wallet account(Actor a, UUID owner, UUID provider, String currency) {
    currency(currency);
    var existing = lookup(a, owner, provider, currency);
    if (existing.isPresent()) return existing.get();
    db.sql(
            "INSERT INTO wallet_accounts(id,tenant_id,provider_id,owner_id,currency,kind)"
                + " VALUES(:id,:tenant,:provider,:owner,:currency,:kind) ON CONFLICT DO NOTHING")
        .param("id", UUID.randomUUID())
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("owner", owner, java.sql.Types.OTHER)
        .param("currency", currency)
        .param("kind", owner == null ? "CLEARING" : "CUSTOMER")
        .update();
    return lookup(a, owner, provider, currency).orElseThrow();
  }

  private Optional<Wallet> lookup(Actor a, UUID owner, UUID provider, String currency) {
    return db.sql(
            "SELECT id,owner_id,provider_id,currency,balance_minor FROM wallet_accounts WHERE"
                + " tenant_id=:tenant AND provider_id=:provider AND currency=:currency AND owner_id"
                + " IS NOT DISTINCT FROM cast(:owner AS uuid)")
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("currency", currency)
        .param("owner", owner, java.sql.Types.OTHER)
        .query(
            (rs, n) ->
                new Wallet(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class),
                    rs.getString(4),
                    rs.getLong(5)))
        .optional();
  }

  public Wallet balance(Actor a, UUID owner, UUID provider, String currency) {
    catalog.provider(a, provider);
    currency(currency);
    return lookup(a, owner, provider, currency)
        .orElse(new Wallet(null, owner, provider, currency, 0));
  }

  public Models.Page<WalletEntry> history(
      Actor a, UUID owner, UUID provider, String currency, int page, int size) {
    if (!a.userId().equals(owner) && !a.admin()) throw ApiFailure.forbidden();
    bounds(page, size);
    catalog.provider(a, provider);
    currency(currency);
    var rows =
        db.sql(
                "SELECT"
                    + " e.id,e.transfer_id,e.delta_minor,t.kind,t.reason,t.reference_id,e.created_at"
                    + " FROM wallet_entries e JOIN wallet_accounts a ON a.id=e.account_id JOIN"
                    + " wallet_transfers t ON t.id=e.transfer_id WHERE e.tenant_id=:tenant AND"
                    + " a.owner_id=:owner AND a.provider_id=:provider AND a.currency=:currency"
                    + " ORDER BY e.created_at DESC,e.id DESC LIMIT :limit OFFSET :offset")
            .param("tenant", a.tenantId())
            .param("owner", owner)
            .param("provider", provider)
            .param("currency", currency)
            .param("limit", size + 1)
            .param("offset", page * size)
            .query(
                (rs, n) ->
                    new WalletEntry(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getLong(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getObject(6, UUID.class),
                        rs.getTimestamp(7).toInstant()))
            .list();
    return new Models.Page<>(rows.stream().limit(size).toList(), page, size, rows.size() > size);
  }

  UUID move(
      Actor a,
      UUID owner,
      UUID provider,
      String currency,
      long amount,
      boolean credit,
      String kind,
      String reason,
      UUID reference) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Wallet transfers require a transaction");
    if (amount <= 0 || amount > 1000000000000L)
      throw ApiFailure.invalid("Amount is outside the supported range");
    var customer = account(a, owner, provider, currency);
    var clearing = account(a, null, provider, currency);
    for (UUID id : List.of(customer.id(), clearing.id()).stream().sorted().toList())
      db.sql("SELECT id FROM wallet_accounts WHERE id=:id AND tenant_id=:tenant FOR UPDATE")
          .param("id", id)
          .param("tenant", a.tenantId())
          .query(UUID.class)
          .single();
    long delta = credit ? amount : -amount;
    int changed =
        db.sql(
                "UPDATE wallet_accounts SET balance_minor=balance_minor+:delta WHERE"
                    + " tenant_id=:tenant AND id=:id AND balance_minor+:delta>=0")
            .param("delta", delta)
            .param("tenant", a.tenantId())
            .param("id", customer.id())
            .update();
    if (changed != 1)
      throw new ApiFailure(
          HttpStatus.CONFLICT, "INSUFFICIENT_WALLET_BALANCE", "Wallet balance is insufficient");
    db.sql(
            "UPDATE wallet_accounts SET balance_minor=balance_minor-:delta WHERE tenant_id=:tenant"
                + " AND id=:id")
        .param("delta", delta)
        .param("tenant", a.tenantId())
        .param("id", clearing.id())
        .update();
    UUID transfer = UUID.randomUUID();
    db.sql(
            "INSERT INTO"
                + " wallet_transfers(id,tenant_id,provider_id,currency,amount_minor,reason,kind,reference_id,actor_id)"
                + " VALUES(:id,:tenant,:provider,:currency,:amount,:reason,:kind,:reference,:actor)")
        .param("id", transfer)
        .param("tenant", a.tenantId())
        .param("provider", provider)
        .param("currency", currency)
        .param("amount", amount)
        .param("reason", reason)
        .param("kind", kind)
        .param("reference", reference, java.sql.Types.OTHER)
        .param("actor", a.userId())
        .update();
    for (var entry : List.of(Map.entry(customer.id(), delta), Map.entry(clearing.id(), -delta)))
      db.sql(
              "INSERT INTO"
                  + " wallet_entries(id,tenant_id,provider_id,currency,transfer_id,account_id,delta_minor)"
                  + " VALUES(:id,:tenant,:provider,:currency,:transfer,:account,:delta)")
          .param("id", UUID.randomUUID())
          .param("tenant", a.tenantId())
          .param("provider", provider)
          .param("currency", currency)
          .param("transfer", transfer)
          .param("account", entry.getKey())
          .param("delta", entry.getValue())
          .update();
    return transfer;
  }

  @Transactional
  public Wallet adjust(Actor a, WalletAdjustment r, String key) {
    a.requireAdmin();
    var replay = commands.find(a, "WALLET_ADJUST", key, r);
    if (replay.isPresent()) return balance(a, r.ownerId(), r.providerId(), r.currency());
    catalog.lockProvider(a, r.providerId());
    db.sql("SELECT id FROM users WHERE tenant_id=:tenant AND id=:id AND role<>'MANAGED_STUDENT'")
        .param("tenant", a.tenantId())
        .param("id", r.ownerId())
        .query(UUID.class)
        .optional()
        .orElseThrow(ApiFailure::notFound);
    var transfer =
        move(
            a,
            r.ownerId(),
            r.providerId(),
            r.currency(),
            r.amountMinor(),
            "CREDIT".equals(r.direction()),
            "MANUAL_" + r.direction(),
            r.reason().trim(),
            null);
    commands.save(a, "WALLET_ADJUST", key, r, List.of(transfer));
    return balance(a, r.ownerId(), r.providerId(), r.currency());
  }
}
