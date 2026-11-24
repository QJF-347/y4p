-- Orange POS backend schema, v1 (DATA_CONTRACT.md §9 + API_CONTRACT.md §1).
-- Money is integer KES cents; timestamps are TIMESTAMPTZ (UTC).

CREATE SEQUENCE server_version_seq START WITH 1;

-- ---------------------------------------------------------------------------
-- Admin users and sessions
-- ---------------------------------------------------------------------------

CREATE TABLE app_user (
  id            UUID PRIMARY KEY,
  name          TEXT NOT NULL,
  email         TEXT NOT NULL,
  password_hash TEXT NOT NULL,                 -- Argon2id encoded string
  role          TEXT NOT NULL CHECK (role IN ('OWNER','MANAGER')),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE app_user_branch (
  user_id   UUID NOT NULL REFERENCES app_user(id),
  branch_id UUID NOT NULL,                     -- REFERENCES branch(id); added below
  PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE refresh_token (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES app_user(id),
  family_id  UUID NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,             -- sha256 hex of the opaque token
  expires_at TIMESTAMPTZ NOT NULL,
  rotated_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  replaced_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_agent TEXT,
  ip         TEXT
);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);

CREATE TABLE idempotency_key (
  key           TEXT NOT NULL,
  actor_id      TEXT NOT NULL,
  endpoint      TEXT NOT NULL,
  request_hash  TEXT NOT NULL,
  status_code   INTEGER,
  response      JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (key, actor_id, endpoint)
);

-- ---------------------------------------------------------------------------
-- Catalog (DATA_CONTRACT.md §9)
-- ---------------------------------------------------------------------------

CREATE TABLE branch (
  id            UUID PRIMARY KEY,
  name          TEXT NOT NULL,
  code          TEXT NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9-]{2,12}$'),
  location      TEXT,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL,
  deleted_at    TIMESTAMPTZ,
  server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  server_version BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE app_user_branch
  ADD CONSTRAINT app_user_branch_branch_fk FOREIGN KEY (branch_id) REFERENCES branch(id);

CREATE TABLE category (
  id          UUID PRIMARY KEY,
  branch_id   UUID NOT NULL REFERENCES branch(id),
  name        TEXT NOT NULL,
  sort_order  INTEGER NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at  TIMESTAMPTZ,
  server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  server_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX category_name_uq ON category (branch_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX category_branch_version_idx ON category (branch_id, server_version);

CREATE TABLE product (
  id               UUID PRIMARY KEY,
  branch_id        UUID NOT NULL REFERENCES branch(id),
  category_id      UUID REFERENCES category(id),
  name             TEXT NOT NULL,
  sku              TEXT,
  barcode          TEXT,
  price_cents      BIGINT NOT NULL CHECK (price_cents >= 0),
  tax_rate_bp      INTEGER NOT NULL DEFAULT 1600 CHECK (tax_rate_bp BETWEEN 0 AND 10000),
  cost_price_cents BIGINT CHECK (cost_price_cents >= 0),
  stock_quantity   INTEGER NOT NULL DEFAULT 0,
  image_url        TEXT,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ NOT NULL,
  updated_at       TIMESTAMPTZ NOT NULL,
  deleted_at       TIMESTAMPTZ,
  server_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  server_version   BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX product_sku_uq     ON product (branch_id, sku) WHERE sku IS NOT NULL;
CREATE UNIQUE INDEX product_barcode_uq ON product (barcode)         WHERE barcode IS NOT NULL;
CREATE INDEX product_branch_version_idx ON product (branch_id, server_version);

-- ---------------------------------------------------------------------------
-- Devices (API_CONTRACT.md §1.2)
-- ---------------------------------------------------------------------------

CREATE TABLE device (
  id           UUID PRIMARY KEY,
  branch_id    UUID NOT NULL REFERENCES branch(id),
  name         TEXT NOT NULL,
  device_code  TEXT NOT NULL UNIQUE CHECK (device_code ~ '^[A-Z0-9]{4}$'),
  token_hash   TEXT NOT NULL UNIQUE,            -- sha256 hex of the device token
  token_expires_at TIMESTAMPTZ NOT NULL,
  is_revoked   BOOLEAN NOT NULL DEFAULT FALSE,
  last_seen_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at   TIMESTAMPTZ
);
CREATE INDEX device_branch_idx ON device (branch_id);

-- ---------------------------------------------------------------------------
-- Sales (append-only) and payments
-- ---------------------------------------------------------------------------

CREATE TABLE sale (
  id                   UUID PRIMARY KEY,
  receipt_number       TEXT NOT NULL,
  branch_id            UUID NOT NULL REFERENCES branch(id),
  device_id            TEXT NOT NULL,
  cashier_id           UUID NOT NULL,          -- no FK: v1 warns on unknown cashiers
  cashier_name         TEXT NOT NULL,
  payment_method       TEXT NOT NULL CHECK (payment_method IN ('CASH','MPESA','CARD')),
  subtotal_cents       BIGINT NOT NULL CHECK (subtotal_cents >= 0),
  tax_amount_cents     BIGINT NOT NULL CHECK (tax_amount_cents >= 0),
  total_amount_cents   BIGINT NOT NULL CHECK (total_amount_cents >= 0),
  amount_received_cents BIGINT NOT NULL CHECK (amount_received_cents >= 0),
  change_amount_cents  BIGINT NOT NULL DEFAULT 0 CHECK (change_amount_cents >= 0),
  payment_status       TEXT NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PAID','FAILED')),
  mpesa_receipt        TEXT,
  created_at           TIMESTAMPTZ NOT NULL,
  received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  payment_updated_at   TIMESTAMPTZ,
  server_version       BIGINT NOT NULL DEFAULT 0,
  UNIQUE (branch_id, receipt_number)
);
CREATE INDEX sale_branch_created_idx ON sale (branch_id, created_at DESC);
CREATE INDEX sale_branch_version_idx ON sale (branch_id, server_version);
CREATE INDEX sale_payment_status_idx ON sale (payment_status) WHERE payment_status = 'PENDING';

CREATE TABLE sale_item (
  id               UUID PRIMARY KEY,
  sale_id          UUID NOT NULL REFERENCES sale(id),
  product_id       UUID NOT NULL REFERENCES product(id),
  product_name     TEXT NOT NULL,
  quantity         INTEGER NOT NULL CHECK (quantity > 0),
  unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents >= 0),
  line_total_cents BIGINT NOT NULL CHECK (line_total_cents >= 0)
);
CREATE INDEX sale_item_sale_idx ON sale_item (sale_id);

CREATE TABLE payment_transaction (
  id                  UUID PRIMARY KEY,
  sale_id             UUID NOT NULL REFERENCES sale(id),
  checkout_request_id TEXT UNIQUE,
  merchant_request_id TEXT,
  mpesa_receipt_number TEXT,
  amount_cents        BIGINT NOT NULL CHECK (amount_cents >= 0),
  phone_number        TEXT NOT NULL,
  status              TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCESS','FAILED')),
  result_code         INTEGER,
  result_desc         TEXT,
  raw_payload_ref     TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX pt_receipt_uq ON payment_transaction (mpesa_receipt_number)
  WHERE mpesa_receipt_number IS NOT NULL;
CREATE INDEX pt_sale_idx ON payment_transaction (sale_id);
CREATE INDEX pt_pending_idx ON payment_transaction (created_at) WHERE status = 'PENDING';

-- One in-flight STK attempt per sale; finished attempts do not occupy the slot.
CREATE UNIQUE INDEX pt_one_pending_per_sale ON payment_transaction (sale_id)
  WHERE status = 'PENDING';

CREATE TABLE daraja_callback (
  id                  BIGSERIAL PRIMARY KEY,
  checkout_request_id TEXT,
  merchant_request_id TEXT,
  body                JSONB NOT NULL,
  received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_queue (
  id                  UUID PRIMARY KEY,
  payment_transaction_id UUID NOT NULL REFERENCES payment_transaction(id),
  checkout_request_id TEXT,
  mpesa_receipt_number TEXT,
  raw_payload_ref     TEXT,
  detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at         TIMESTAMPTZ,
  resolved_by         UUID REFERENCES app_user(id),
  action              TEXT CHECK (action IN ('APPLY_TO_SALE','MARK_REFUND','DISCARD'))
);
CREATE INDEX recon_open_idx ON reconciliation_queue (detected_at) WHERE resolved_at IS NULL;

-- ---------------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------------

CREATE TABLE audit_log (
  id         BIGSERIAL PRIMARY KEY,
  actor_type TEXT NOT NULL CHECK (actor_type IN ('USER','DEVICE','SYSTEM')),
  actor_id   TEXT NOT NULL,
  action     TEXT NOT NULL,
  entity     TEXT NOT NULL,
  entity_id  TEXT,
  details    JSONB,
  ip         TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity_idx ON audit_log (entity, entity_id);
