package dev.rahul.booking;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {
  private final JdbcClient db;
  private final LedgerService ledger;

  public SettlementService(JdbcClient db, LedgerService ledger) {
    this.db = db;
    this.ledger = ledger;
  }

  record Refund(UUID payer, UUID provider, String currency, long amount, boolean cancelled) {}

  public void cancel(Actor a, Models.Booking b) {
    var refund =
        db.sql(
                "SELECT"
                    + " o.customer_id,o.provider_id,o.currency,i.wallet_paid_minor+i.cash_collected_minor"
                    + " AS settled,i.cancelled_at IS NOT NULL AS cancelled FROM"
                    + " commerce_order_items i JOIN commerce_orders o ON o.id=i.order_id AND"
                    + " o.tenant_id=i.tenant_id WHERE i.tenant_id=:tenant AND i.booking_id=:id FOR"
                    + " UPDATE OF i")
            .param("tenant", a.tenantId())
            .param("id", b.id())
            .query(
                (rs, n) ->
                    new Refund(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getLong(4),
                        rs.getBoolean(5)))
            .optional();
    if (refund.isEmpty() || refund.get().cancelled()) return;
    var r = refund.get();
    if (r.amount() > 0)
      ledger.move(
          a,
          r.payer(),
          r.provider(),
          r.currency(),
          r.amount(),
          true,
          "BOOKING_REFUND",
          "Cancellation credit for settled lesson value",
          b.id());
    db.sql(
            "UPDATE commerce_order_items SET cancelled_at=now(),refunded_minor=:amount WHERE"
                + " tenant_id=:tenant AND booking_id=:id")
        .param("amount", r.amount())
        .param("tenant", a.tenantId())
        .param("id", b.id())
        .update();
  }
}
