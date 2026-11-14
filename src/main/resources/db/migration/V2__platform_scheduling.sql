ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK(role IN ('ADMIN','PROVIDER','CUSTOMER','GUARDIAN'));
ALTER TABLE users ADD COLUMN display_name varchar(120) NOT NULL DEFAULT '';
ALTER TABLE providers ADD COLUMN user_id uuid;
ALTER TABLE providers ADD CONSTRAINT providers_user_fk FOREIGN KEY(tenant_id,user_id) REFERENCES users(tenant_id,id);
ALTER TABLE providers ADD CONSTRAINT providers_user_unique UNIQUE(tenant_id,user_id);
ALTER TABLE providers ADD COLUMN assigned_services_only boolean NOT NULL DEFAULT false;
CREATE TABLE tenant_settings (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
 booking_horizon_days integer NOT NULL DEFAULT 366 CHECK(booking_horizon_days BETWEEN 1 AND 730),
 minimum_notice_minutes integer NOT NULL DEFAULT 0 CHECK(minimum_notice_minutes BETWEEN 0 AND 43200),
 customer_signup_enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE locations (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id), name varchar(120) NOT NULL,
 address varchar(500) NOT NULL, time_zone varchar(60) NOT NULL, UNIQUE(tenant_id,id)
);
CREATE INDEX locations_tenant ON locations(tenant_id,id);
CREATE TABLE rooms (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, location_id uuid NOT NULL, name varchar(120) NOT NULL,
 capacity integer NOT NULL CHECK(capacity BETWEEN 1 AND 500), UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,location_id) REFERENCES locations(tenant_id,id)
);
CREATE INDEX rooms_location ON rooms(tenant_id,location_id,id);
CREATE TABLE provider_services (
 tenant_id uuid NOT NULL,provider_id uuid NOT NULL,service_id uuid NOT NULL,
 PRIMARY KEY(provider_id,service_id),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id)
);
CREATE TABLE lesson_sessions (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, provider_id uuid NOT NULL,service_id uuid NOT NULL,
 room_id uuid,series_id uuid,starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL,
 capacity integer NOT NULL CHECK(capacity BETWEEN 1 AND 100),
 cancellation_hours integer NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('OPEN','CANCELLED')),
 CHECK(ends_at>starts_at), UNIQUE(tenant_id,id),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),
 FOREIGN KEY(tenant_id,service_id) REFERENCES services(tenant_id,id),
 FOREIGN KEY(tenant_id,room_id) REFERENCES rooms(tenant_id,id)
);
CREATE INDEX sessions_browse ON lesson_sessions(tenant_id,starts_at,id);
ALTER TABLE bookings ADD COLUMN room_id uuid;
ALTER TABLE bookings ADD COLUMN session_id uuid;
ALTER TABLE bookings ADD COLUMN request_starts_at timestamptz;
ALTER TABLE bookings ADD COLUMN request_room_id uuid;
UPDATE bookings SET request_starts_at=starts_at;
ALTER TABLE bookings ALTER COLUMN request_starts_at SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT bookings_room_fk FOREIGN KEY(tenant_id,room_id) REFERENCES rooms(tenant_id,id);
ALTER TABLE bookings ADD CONSTRAINT bookings_session_fk FOREIGN KEY(tenant_id,session_id) REFERENCES lesson_sessions(tenant_id,id);
ALTER TABLE bookings DROP CONSTRAINT bookings_provider_id_tstzrange_excl;
-- One occupancy per private booking or group session. Seats do not each reserve the provider.
CREATE TABLE resource_occupancy (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,provider_id uuid NOT NULL,room_id uuid,
 kind varchar(20) NOT NULL CHECK(kind IN ('BOOKING','SESSION','BLOCKOUT')),
 starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL,
 CHECK(ends_at>starts_at),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id),
 FOREIGN KEY(tenant_id,room_id) REFERENCES rooms(tenant_id,id),
 EXCLUDE USING gist(provider_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&),
 EXCLUDE USING gist(room_id WITH =,tstzrange(starts_at,ends_at,'[)') WITH &&) WHERE(room_id IS NOT NULL)
);
INSERT INTO resource_occupancy(id,tenant_id,provider_id,kind,starts_at,ends_at)
 SELECT id,tenant_id,provider_id,'BOOKING',starts_at,ends_at FROM bookings WHERE status='CONFIRMED';
CREATE INDEX occupancy_tenant ON resource_occupancy(tenant_id,provider_id,starts_at);
CREATE INDEX bookings_session ON bookings(tenant_id,session_id,status);
CREATE UNIQUE INDEX bookings_active_session_customer ON bookings(session_id,customer_id) WHERE status='CONFIRMED';
CREATE TABLE booking_commands (
 tenant_id uuid NOT NULL, actor_id uuid NOT NULL,operation varchar(80) NOT NULL,
 command_key varchar(100) NOT NULL,request_hash char(64) NOT NULL,result_ids jsonb NOT NULL,
 PRIMARY KEY(tenant_id,actor_id,operation,command_key),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES users(tenant_id,id),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE invitations (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenants(id),email varchar(254) NOT NULL,
 role varchar(20) NOT NULL CHECK(role IN ('PROVIDER','CUSTOMER','GUARDIAN')),provider_id uuid,
 token_hash char(64) NOT NULL UNIQUE,status varchar(20) NOT NULL CHECK(status IN ('PENDING','ACCEPTED','REVOKED')),
 expires_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,provider_id) REFERENCES providers(tenant_id,id)
);
CREATE INDEX invitations_tenant ON invitations(tenant_id,created_at DESC,id DESC);
CREATE TABLE notifications (
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL,user_id uuid NOT NULL,booking_id uuid NOT NULL REFERENCES bookings(id),
 event_type varchar(40) NOT NULL,read_at timestamptz,created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,user_id) REFERENCES users(tenant_id,id)
);
CREATE INDEX notifications_user ON notifications(tenant_id,user_id,created_at DESC,id DESC);

-- Keep reservations synchronized even when a future internal writer bypasses a Java service.
CREATE FUNCTION sync_booking_occupancy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.session_id IS NULL AND NEW.status='CONFIRMED' THEN
  INSERT INTO resource_occupancy(id,tenant_id,provider_id,room_id,kind,starts_at,ends_at)
  VALUES(NEW.id,NEW.tenant_id,NEW.provider_id,NEW.room_id,'BOOKING',NEW.starts_at,NEW.ends_at)
  ON CONFLICT(id) DO UPDATE SET starts_at=excluded.starts_at,ends_at=excluded.ends_at,provider_id=excluded.provider_id,room_id=excluded.room_id;
 ELSE
  DELETE FROM resource_occupancy WHERE id=NEW.id AND tenant_id=NEW.tenant_id AND kind='BOOKING';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER bookings_occupancy AFTER INSERT OR UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION sync_booking_occupancy();
CREATE FUNCTION sync_session_occupancy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='OPEN' THEN
  INSERT INTO resource_occupancy(id,tenant_id,provider_id,room_id,kind,starts_at,ends_at)
  VALUES(NEW.id,NEW.tenant_id,NEW.provider_id,NEW.room_id,'SESSION',NEW.starts_at,NEW.ends_at)
  ON CONFLICT(id) DO UPDATE SET starts_at=excluded.starts_at,ends_at=excluded.ends_at,provider_id=excluded.provider_id,room_id=excluded.room_id;
 ELSE
  DELETE FROM resource_occupancy WHERE id=NEW.id AND tenant_id=NEW.tenant_id AND kind='SESSION';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER sessions_occupancy AFTER INSERT OR UPDATE ON lesson_sessions FOR EACH ROW EXECUTE FUNCTION sync_session_occupancy();

CREATE FUNCTION enforce_session_enrolment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s lesson_sessions%ROWTYPE;
DECLARE occupied integer;
BEGIN
 IF NEW.session_id IS NOT NULL AND NEW.status='CONFIRMED' THEN
  SELECT * INTO s FROM lesson_sessions WHERE id=NEW.session_id AND tenant_id=NEW.tenant_id FOR UPDATE;
  IF NOT FOUND OR s.status<>'OPEN' THEN RAISE EXCEPTION 'Session is unavailable' USING ERRCODE='23514'; END IF;
  IF NEW.provider_id<>s.provider_id OR NEW.service_id<>s.service_id OR NEW.starts_at<>s.starts_at OR NEW.ends_at<>s.ends_at OR NEW.room_id IS DISTINCT FROM s.room_id THEN
   RAISE EXCEPTION 'Enrolment does not match session' USING ERRCODE='23514';
  END IF;
  SELECT count(*) INTO occupied FROM bookings WHERE session_id=s.id AND status='CONFIRMED' AND id<>NEW.id;
  IF occupied>=s.capacity THEN RAISE EXCEPTION 'Session capacity exhausted' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER bookings_session_capacity BEFORE INSERT OR UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION enforce_session_enrolment();
