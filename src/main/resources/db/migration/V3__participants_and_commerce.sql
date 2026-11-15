ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK(role IN ('ADMIN','PROVIDER','CUSTOMER','GUARDIAN','MANAGED_STUDENT'));
CREATE TABLE participants (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenants(id),guardian_id uuid,user_id uuid,
 display_name varchar(120) NOT NULL,date_of_birth date,gender varchar(40) NOT NULL DEFAULT '',
 login_enabled boolean NOT NULL DEFAULT false,
 self_booking_enabled boolean NOT NULL DEFAULT false,active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,id),UNIQUE(tenant_id,user_id),
 FOREIGN KEY(tenant_id,guardian_id) REFERENCES users(tenant_id,id),
 FOREIGN KEY(tenant_id,user_id) REFERENCES users(tenant_id,id),
 CHECK(guardian_id IS NOT NULL OR user_id IS NOT NULL)
);
INSERT INTO participants(id,tenant_id,user_id,display_name)
 SELECT id,tenant_id,id,CASE WHEN display_name='' THEN email ELSE display_name END FROM users;
CREATE INDEX participants_guardian ON participants(tenant_id,guardian_id,id);
CREATE FUNCTION create_user_participant() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.role<>'MANAGED_STUDENT' THEN
  INSERT INTO participants(id,tenant_id,user_id,display_name) VALUES(NEW.id,NEW.tenant_id,NEW.id,CASE WHEN NEW.display_name='' THEN NEW.email ELSE NEW.display_name END);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER user_participant AFTER INSERT ON users FOR EACH ROW EXECUTE FUNCTION create_user_participant();
ALTER TABLE bookings ADD COLUMN participant_id uuid;
ALTER TABLE bookings ADD COLUMN booked_by uuid;
UPDATE bookings SET participant_id=customer_id,booked_by=customer_id;
ALTER TABLE bookings ALTER COLUMN participant_id SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN booked_by SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT bookings_participant_fk FOREIGN KEY(tenant_id,participant_id) REFERENCES participants(tenant_id,id);
ALTER TABLE bookings ADD CONSTRAINT bookings_actor_fk FOREIGN KEY(tenant_id,booked_by) REFERENCES users(tenant_id,id);
ALTER TABLE bookings DROP CONSTRAINT bookings_customer_id_tstzrange_excl;
ALTER TABLE bookings ADD CONSTRAINT bookings_participant_no_overlap EXCLUDE USING gist(participant_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status='CONFIRMED');
ALTER TABLE bookings DROP CONSTRAINT bookings_tenant_id_customer_id_idempotency_key_key;
ALTER TABLE bookings ADD CONSTRAINT bookings_actor_key UNIQUE(tenant_id,booked_by,idempotency_key);
DROP INDEX bookings_active_session_customer;
CREATE UNIQUE INDEX bookings_active_session_participant ON bookings(session_id,participant_id) WHERE(status='CONFIRMED');
CREATE INDEX bookings_participant_page ON bookings(tenant_id,participant_id,created_at DESC,id DESC);
ALTER TABLE services ADD COLUMN price_minor bigint NOT NULL DEFAULT 0 CHECK(price_minor BETWEEN 0 AND 1000000000000);
ALTER TABLE services ADD COLUMN currency char(3) NOT NULL DEFAULT 'USD' CHECK(currency~'^[A-Z]{3}$');
CREATE TABLE provider_payment_settings (
 provider_id uuid PRIMARY KEY,tenant_id uuid NOT NULL,cash_enabled boolean NOT NULL DEFAULT false,wallet_enabled boolean NOT NULL DEFAULT false,
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id)
);
CREATE TABLE wallet_accounts (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,provider_id uuid NOT NULL,owner_id uuid,currency char(3) NOT NULL,
 kind varchar(20) NOT NULL CHECK(kind IN ('CUSTOMER','CLEARING')),balance_minor bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,provider_id,currency,id),
 CHECK((kind='CUSTOMER' AND owner_id IS NOT NULL AND balance_minor>=0) OR (kind='CLEARING' AND owner_id IS NULL)),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),FOREIGN KEY(tenant_id,owner_id) REFERENCES users(tenant_id,id)
);
CREATE UNIQUE INDEX wallets_owner ON wallet_accounts(tenant_id,provider_id,currency,owner_id) WHERE(owner_id IS NOT NULL);
CREATE UNIQUE INDEX wallets_clearing ON wallet_accounts(tenant_id,provider_id,currency) WHERE(owner_id IS NULL);
CREATE TABLE wallet_transfers (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,provider_id uuid NOT NULL,currency char(3) NOT NULL,
 amount_minor bigint NOT NULL CHECK(amount_minor BETWEEN 1 AND 1000000000000),
 reason varchar(500) NOT NULL,kind varchar(40) NOT NULL,reference_id uuid,actor_id uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,provider_id,currency,id),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),FOREIGN KEY(tenant_id,actor_id) REFERENCES users(tenant_id,id)
);
CREATE TABLE wallet_entries (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,provider_id uuid NOT NULL,currency char(3) NOT NULL,
 transfer_id uuid NOT NULL,account_id uuid NOT NULL,delta_minor bigint NOT NULL CHECK(delta_minor<>0),
 created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(transfer_id,account_id),
 FOREIGN KEY(tenant_id,provider_id,currency,transfer_id) REFERENCES wallet_transfers(tenant_id,provider_id,currency,id),
 FOREIGN KEY(tenant_id,provider_id,currency,account_id) REFERENCES wallet_accounts(tenant_id,provider_id,currency,id)
);
CREATE INDEX wallet_entries_account ON wallet_entries(tenant_id,account_id,created_at DESC,id DESC);
CREATE FUNCTION ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Ledger history is immutable' USING ERRCODE='55000'; END $$;
CREATE TRIGGER ledger_entries_immutable BEFORE UPDATE OR DELETE ON wallet_entries FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER ledger_transfers_immutable BEFORE UPDATE OR DELETE ON wallet_transfers FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE FUNCTION ledger_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE n integer;DECLARE total numeric;DECLARE magnitude numeric;DECLARE kinds integer;DECLARE expected bigint;
BEGIN
 SELECT count(*),sum(e.delta_minor),sum(abs(e.delta_minor)),count(DISTINCT a.kind) INTO n,total,magnitude,kinds
 FROM wallet_entries e JOIN wallet_accounts a ON a.id=e.account_id WHERE e.transfer_id=NEW.transfer_id;
 SELECT amount_minor INTO expected FROM wallet_transfers WHERE id=NEW.transfer_id;
 IF n<>2 OR total<>0 OR magnitude<>2::numeric*expected OR kinds<>2 THEN RAISE EXCEPTION 'Unbalanced wallet transfer' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER wallet_transfer_balanced AFTER INSERT ON wallet_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_balanced();
CREATE TABLE commerce_orders (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,provider_id uuid NOT NULL,customer_id uuid NOT NULL,booked_by uuid NOT NULL,
 currency char(3) NOT NULL,payment_method varchar(20) NOT NULL CHECK(payment_method IN ('WALLET','CASH','WALLET_CASH')),
 created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),FOREIGN KEY(tenant_id,customer_id) REFERENCES users(tenant_id,id),FOREIGN KEY(tenant_id,booked_by) REFERENCES users(tenant_id,id)
);
CREATE INDEX commerce_orders_payer ON commerce_orders(tenant_id,customer_id,created_at DESC,id DESC);
CREATE TABLE commerce_order_items (
 booking_id uuid PRIMARY KEY,tenant_id uuid NOT NULL,order_id uuid NOT NULL,amount_minor bigint NOT NULL CHECK(amount_minor>=0),
 wallet_paid_minor bigint NOT NULL CHECK(wallet_paid_minor>=0),cash_due_minor bigint NOT NULL CHECK(cash_due_minor>=0),
 cash_collected_minor bigint NOT NULL DEFAULT 0 CHECK(cash_collected_minor>=0),refunded_minor bigint NOT NULL DEFAULT 0 CHECK(refunded_minor>=0),cancelled_at timestamptz,
 FOREIGN KEY(tenant_id,order_id) REFERENCES commerce_orders(tenant_id,id),FOREIGN KEY(booking_id) REFERENCES bookings(id),
 CHECK(amount_minor=wallet_paid_minor+cash_due_minor),CHECK(cash_collected_minor<=cash_due_minor),CHECK(refunded_minor<=wallet_paid_minor+cash_collected_minor)
);
CREATE INDEX commerce_items_order ON commerce_order_items(tenant_id,order_id,booking_id);
CREATE TABLE cash_receipts (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,booking_id uuid NOT NULL REFERENCES commerce_order_items(booking_id),
 amount_minor bigint NOT NULL CHECK(amount_minor>0),actor_id uuid NOT NULL REFERENCES users(id),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER cash_receipts_immutable BEFORE UPDATE OR DELETE ON cash_receipts FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
