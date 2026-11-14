CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE tenants (
 id uuid PRIMARY KEY, slug varchar(63) NOT NULL UNIQUE,
 name varchar(120) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE users (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
 email varchar(254) NOT NULL, password_hash varchar(100) NOT NULL,
 role varchar(20) NOT NULL CHECK (role IN ('ADMIN','CUSTOMER')),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (tenant_id,email), UNIQUE(tenant_id,id)
);
CREATE TABLE auth_sessions (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
 token_hash char(64) NOT NULL UNIQUE, kind varchar(10) NOT NULL CHECK(kind IN ('BEARER','COOKIE')),
 expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auth_sessions_expiry ON auth_sessions(expires_at);
CREATE TABLE providers (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
 name varchar(120) NOT NULL, time_zone varchar(60) NOT NULL,
 UNIQUE(tenant_id,id)
);
CREATE TABLE services (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
 name varchar(120) NOT NULL, duration_minutes integer NOT NULL CHECK(duration_minutes BETWEEN 5 AND 480),
 cancellation_hours integer NOT NULL CHECK(cancellation_hours BETWEEN 0 AND 720),
 UNIQUE(tenant_id,id)
);
CREATE TABLE availability (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, provider_id uuid NOT NULL,
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 CHECK (ends_at > starts_at),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),
 EXCLUDE USING gist (provider_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&)
);
CREATE TABLE bookings (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, provider_id uuid NOT NULL,
 service_id uuid NOT NULL, customer_id uuid NOT NULL,
 starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
 cancellation_hours integer NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('CONFIRMED','CANCELLED')),
 idempotency_key varchar(100) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), cancelled_at timestamptz,
 CHECK (ends_at > starts_at),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id),
 FOREIGN KEY(tenant_id,customer_id) REFERENCES users(tenant_id,id),
 UNIQUE(tenant_id,customer_id,idempotency_key),
 EXCLUDE USING gist (provider_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status='CONFIRMED'),
 EXCLUDE USING gist (customer_id WITH =, tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(status='CONFIRMED')
);
CREATE INDEX bookings_customer_page ON bookings(tenant_id,customer_id,created_at DESC,id DESC);
CREATE INDEX bookings_admin_page ON bookings(tenant_id,created_at DESC,id DESC);
CREATE INDEX availability_lookup ON availability(tenant_id,provider_id,starts_at);
CREATE INDEX services_tenant ON services(tenant_id,id);
CREATE INDEX providers_tenant ON providers(tenant_id,id);
CREATE TABLE booking_events (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), booking_id uuid NOT NULL REFERENCES bookings(id),
 event_type varchar(40) NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
