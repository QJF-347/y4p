# Orange POS — Shared Data Contract (v1)

Status: **LOCKED — v1 approved 2026-11-21 (Step 1 complete)**
Date: 2026-11-21
Applies to: `orangemain` (KMP POS, SQLDelight), `orangeadmin` (Android admin), backend + PostgreSQL (to be built).

This is the single source of truth for entity shapes, field semantics, and mutation rules across all three
layers. Every layer must produce and accept exactly these shapes over the wire (snake_case JSON) and store
them without lossy conversions.

Change process: any field addition/rename/type change bumps the version and lists the change in §11.
Old clients must fail loudly on unknown required fields, not guess.

---

## 1. Global conventions

| Topic | Rule |
|---|---|
| Record IDs | **Client-generated UUIDs as strings** for records created on a device (offline-safe, no coordination). Canonical form: lowercase, hyphens, 36 chars, e.g. `019a3c58-6f2e-7c31-9c1d-8a2f9b0c4d11`. Store as `TEXT` in SQLite, `UUID` in PostgreSQL, `String` in Kotlin. |
| UUID version | SHOULD be **UUIDv7** (time-ordered → better index locality, stable offline ordering). UUIDv4 is acceptable in v1. Never v1/v2 (MAC leakage). |
| Seeded IDs | Client-seeded default rows (e.g. default categories) MUST use **UUIDv5** with a fixed app namespace + a deterministic key (`namespace + "/" + branch_id + "/" + slug`) so every offline device generates the *same* ID for the *same* seed and seeds don't duplicate on sync. |
| Record IDs (server exceptions) | `PaymentTransaction.id` is created by the backend (STK push is inherently online). Clients accept server-issued IDs for transactions only. |
| Money | **Integer minor units** — every monetary field ends in `_cents` and is `BIGINT` (Pg) / `INTEGER` (SQLite) / `Long` (Kotlin). Currency is **KES**; 1 KES = 100 cents. **Never use floating point for money.** |
| Rates (tax) | Integer **basis points** `*_bp`: `1600` = 16.00 %. Range 0–10000. |
| Timestamps | RFC 3339 / ISO 8601, **UTC**, millisecond precision, `Z` suffix: `2026-11-21T09:31:07.412Z`. SQLite `TEXT`, PostgreSQL `TIMESTAMPTZ`, Kotlin `String` (or `Instant` converted at the edge). `created_at` on offline records = **device clock**; the server adds its own `received_at`/`server_updated_at` (not part of the client payload). |
| Enums | Uppercase strings on the wire and in the DB (`TEXT` + `CHECK`). No numeric codes. |
| Phone numbers | Stored **E.164**: `+254712345678`. Normalize (`07XX…`/`2547XX…`/`+2547XX…` → `+254…`) before persisting. Never store display formatting. |
| `sync_status` | **Device-local only.** Lives in SQLite, never serialized to the server, never stored in PostgreSQL. See §6. |
| Deletion | Synced entities are **soft-deleted** via `deleted_at` (tombstone); never hard-delete on a client. Only the backend may purge after retention. |
| IDs referenced across entities | FK fields (`*_id`) use the same UUID string format. FKs are enforced server-side; clients must treat a dangling FK as a sync error, not silently drop the row. |
| JSON | snake_case keys; unknown keys ignored; missing required keys = 400. |

> **UUID generation (implementation note, resolved in Step 2):** server side use Rust `uuid`
> (features `v7`, `v5`) or Node `uuid@9+` (`v7()`, `v5()`). Kotlin's `kotlin.uuid.Uuid` only exposes
> v4 via `Uuid.random()`, so `orangemain`/`orangeadmin` need either a vetted KMP library or a small
> shared helper (v5 = SHA-1 of namespace+name; v7 = 48-bit unix-ms + version/variant bits + random).
> Until that helper lands, v4 remains contract-compliant (no sortability benefit).

---

## 2. Entity specifications

Legend for "Mutable": **No** = immutable after the row exists; **Yes** = may change while row is live;
**Write-once** = may be set exactly once from NULL, then immutable.

### 2.1 Branch

Created/renamed by admin (online). Referenced by products, devices, sales.

| Field | Type (PG / SQLite / Kotlin) | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Client-generated UUID; admin screen generates it. |
| `name` | TEXT / TEXT / String | ✓ | Yes | Display name. |
| `code` | TEXT / TEXT / String | ✓ | **No** | Short uppercase code, `^[A-Z0-9-]{2,12}$` (e.g. `NBO-CBD-01`). Used inside receipt numbers. **UNIQUE.** |
| `location` | TEXT / TEXT / String | – | Yes | Free-form for v1 (address or description). Lat/lng deferred to v2. |
| `is_active` | BOOLEAN / INTEGER / Boolean | ✓ | Yes | Default `TRUE`. Closed branches: `FALSE`. |
| `created_at` | TIMESTAMPTZ / TEXT / String | ✓ | **No** | |
| `updated_at` | TIMESTAMPTZ / TEXT / String | ✓ | Yes | |
| `deleted_at` | TIMESTAMPTZ / TEXT / String | – | Set-once | Tombstone. |

Server-only metadata: `server_updated_at`, `server_version`.

Derived/presentational data (`devices_count`, `today_sales`) is **not stored on Branch** — orangeadmin must
fetch it from stats endpoints and keep it out of the entity.

### 2.2 Category

| Field | Type | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Default seeds use UUIDv5 (see §1). |
| `branch_id` | UUID / TEXT / String | ✓ | **No** | v1 categories are per-branch (same scope as products). |
| `name` | TEXT / TEXT / String | ✓ | Yes | Unique per branch (case-insensitive). |
| `sort_order` | INTEGER / INTEGER / Int | ✓ | Yes | Default `0`, for POS grid ordering. |
| `created_at` | TIMESTAMPTZ / TEXT / String | ✓ | **No** | |
| `updated_at` | TIMESTAMPTZ / TEXT / String | ✓ | Yes | |
| `deleted_at` | TIMESTAMPTZ / TEXT / String | – | Set-once | Tombstone. |
| `sync_status` | – / TEXT / String | local | Yes | Local only. |

### 2.3 Product (with Category reference)

v1 scope: **products belong to one branch's catalog** (`branch_id`), matching the offline branch POS.
The v2 target (catalog-wide product + `branch_inventory` join table) is explicitly deferred — see §10.

| Field | Type | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Client-generated UUID. |
| `branch_id` | UUID / TEXT / String | ✓ | **No** | Owning branch/catalog scope. |
| `category_id` | UUID / TEXT / String | – | Yes | FK → Category. NULL = uncategorized. |
| `name` | TEXT / TEXT / String | ✓ | Yes | |
| `sku` | TEXT / TEXT / String | – | Yes | UNIQUE `(branch_id, sku)` where not null. |
| `barcode` | TEXT / TEXT / String | – | Yes | EAN/UPC; UNIQUE globally where not null. |
| `price_cents` | BIGINT / INTEGER / Long | ✓ | Yes | Selling price, KES cents, `>= 0`. |
| `tax_rate_bp` | INTEGER / INTEGER / Int | ✓ | Yes | Default `1600` (16 %). |
| `cost_price_cents` | BIGINT / INTEGER / Long | – | Yes | Purchase cost, for margin reports. |
| `stock_quantity` | INTEGER / INTEGER / Int | ✓ | Yes* | *Mutable, but write semantics are constrained (§6.4): sales decrement via deltas; admin restock sets absolute values. Never free-write from an offline client outside those paths. |
| `image_url` | TEXT / TEXT / String | – | Yes | Object-storage URL. |
| `is_active` | BOOLEAN / INTEGER / Boolean | ✓ | Yes | Default `TRUE`; hides discontinued items without deleting. |
| `created_at` | TIMESTAMPTZ / TEXT / String | ✓ | **No** | |
| `updated_at` | TIMESTAMPTZ / TEXT / String | ✓ | Yes | |
| `deleted_at` | TIMESTAMPTZ / TEXT / String | – | Set-once | Tombstone. |
| `sync_status` | – / TEXT / String | local | Yes | Local only. |

Low-stock is **derived** (v1: branch default threshold of 5); a per-product `low_stock_threshold` field is
deferred (§10). PostgreSQL keeps `JSONB attributes` free-form column for future extras — never used for
contract fields.

### 2.4 Sale

Created by the POS **offline**, then pushed. Append-only: once inserted, all financial fields are frozen.

| Field | Type | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Client-generated UUID. |
| `receipt_number` | TEXT / TEXT / String | ✓ | **No** | Format `ORG-{BRANCH_CODE}-{DEVICE_CODE}-{YYYYMMDD}-{NNNN}` (e.g. `ORG-NBO-CBD-01-D0042-20261121-0007`). Contains the device code so two offline devices in the same branch can never collide. UNIQUE `(branch_id, receipt_number)`. |
| `branch_id` | UUID / TEXT / String | ✓ | **No** | |
| `device_id` | TEXT / TEXT / String | ✓ | **No** | Registered device that created the sale. Also carries the `DEVICE_CODE` used in receipts. |
| `cashier_id` | UUID / TEXT / String | ✓ | **No** | FK → users. |
| `cashier_name` | TEXT / TEXT / String | ✓ | **No** | Denormalized snapshot for receipts (users can be renamed). |
| `payment_method` | TEXT / TEXT / String | ✓ | **No** | `CASH` \| `MPESA` \| `CARD` \| `SPLIT`. |
| `subtotal_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Sum of line totals. |
| `tax_amount_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Sum of per-line tax, each rounded half-up (see §5.3). |
| `total_amount_cents` | BIGINT / INTEGER / Long | ✓ | **No** | `subtotal + tax`. |
| `amount_received_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Cash tendered; equal to `total_amount_cents` for non-cash. For `SPLIT`, equal to `cash_amount_cents` (cash was taken exactly). |
| `change_amount_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Change handed back; `0` for `SPLIT` (no over-tender on the cash leg), `0` otherwise unless cash over-tendered. `>= 0`. |
| `cash_amount_cents` | BIGINT / INTEGER / Long | ✓ | **No** | `SPLIT` only: cash portion, `0 < cash_amount_cents < total_amount_cents`. The M-Pesa leg is the remainder (`total_amount_cents - cash_amount_cents`). Must be `0` for `CASH`/`MPESA`/`CARD`. |
| `payment_status` | TEXT / TEXT / String | ✓ | **Server only** | `PENDING` \| `PAID` \| `FAILED`. Rule: device creates `CASH`/`CARD` sales as `PAID`; `MPESA` and `SPLIT` sales as `PENDING`. Only the backend may transition M-Pesa status (§4.1). |
| `mpesa_receipt` | TEXT / TEXT / String | – | **Write-once (server)** | Denormalized copy of the winning `PaymentTransaction.mpesa_receipt_number`. NULL for cash/card. The **canonical** source is PaymentTransaction. |
| `created_at` | TIMESTAMPTZ / TEXT / String | ✓ | **No** | Device clock. |
| `sync_status` | – / TEXT / String | local | Yes | Local only. |

Server-only metadata: `received_at` (server clock), `server_version`.

### 2.5 SaleItem

Append-only child rows. No updates, ever. Corrections happen only through future refund/void events (out of
scope for v1 — §10).

| Field | Type | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Client-generated UUID (NOT derived from sale id — independent rows). |
| `sale_id` | UUID / TEXT / String | ✓ | **No** | FK → Sale. |
| `product_id` | UUID / TEXT / String | ✓ | **No** | FK → Product. |
| `product_name` | TEXT / TEXT / String | ✓ | **No** | Snapshot at sale time (product may be renamed later). |
| `quantity` | INTEGER / INTEGER / Int | ✓ | **No** | `> 0`. |
| `unit_price_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Snapshot of `Product.price_cents` at sale time. |
| `line_total_cents` | BIGINT / INTEGER / Long | ✓ | **No** | `quantity * unit_price_cents`; stored for integrity checks. |

UNIQUE `(sale_id, product_id)` is **not** imposed — the same product may legitimately appear twice only
if a future "line notes" feature requires it; v1 UI merges quantities per product.

### 2.6 PaymentTransaction

One row per STK-push attempt. The backend creates it; the POS may display it. Idempotent by construction.

| Field | Type | Req | Mutable | Notes |
|---|---|---|---|---|
| `id` | UUID / TEXT / String | ✓ | **No** | Server-generated UUIDv7. |
| `sale_id` | UUID / TEXT / String | ✓ | **No** | FK → Sale. Multiple attempts per sale allowed (retries). |
| `checkout_request_id` | TEXT / TEXT / String | – | **Write-once** | Daraja `CheckoutRequestID`. **UNIQUE.** Primary idempotency key for callbacks. NULL only between attempt creation and Daraja response. |
| `merchant_request_id` | TEXT / TEXT / String | – | **Write-once** | Daraja `MerchantRequestID`, for support/diagnostics. |
| `mpesa_receipt_number` | TEXT / TEXT / String | – | **Write-once** | Daraja `MpesaReceiptNumber` on success. **Is the unique key that prevents double-counting: UNIQUE where not null.** |
| `amount_cents` | BIGINT / INTEGER / Long | ✓ | **No** | Amount requested, KES cents. |
| `phone_number` | TEXT / TEXT / String | ✓ | **No** | E.164. |
| `status` | TEXT / TEXT / String | ✓ | **Server only, terminal** | `PENDING` → `SUCCESS` \| `FAILED`. Terminal states never change (§4.2). |
| `result_code` | INTEGER / INTEGER / Int | – | Server only | Daraja `ResultCode` (`0` = success). |
| `result_desc` | TEXT / TEXT / String | – | Server only | Daraja `ResultDesc`. |
| `raw_payload_ref` | TEXT / TEXT / String | – | **Write-once** | Object-storage key for the raw callback JSON, e.g. `mpesa-callbacks/2026/11/{checkout_request_id}.json`. Retention ≥ 90 days. Payload body is NOT stored in Postgres. |
| `created_at` | TIMESTAMPTZ / TEXT / String | ✓ | **No** | Server clock (attempt creation). |
| `updated_at` | TIMESTAMPTZ / TEXT / String | ✓ | Server only | Server clock. |
| `sync_status` | – / TEXT / String | local | Yes | POS caches txns for display; local only. |

---

## 3. Immutability matrix (summary)

| Entity | Immutable forever | Mutable | Write-once | Only the server may change |
|---|---|---|---|---|
| Branch | `id`, `code`, `created_at` | `name`, `location`, `is_active`, `updated_at`, `deleted_at` | — | (admin API on behalf of admin) |
| Category | `id`, `branch_id`, `created_at` | `name`, `sort_order`, `updated_at`, `deleted_at` | — | — |
| Product | `id`, `branch_id`, `created_at` | `name`, `category_id`, `sku`, `barcode`, `price_cents`, `tax_rate_bp`, `cost_price_cents`, `stock_quantity`*, `image_url`, `is_active`, `updated_at`, `deleted_at` | — | `stock_quantity` when driven by sale deltas |
| Sale | `id`, `receipt_number`, `branch_id`, `device_id`, `cashier_id`, `cashier_name`, `payment_method`, `subtotal_cents`, `tax_amount_cents`, `total_amount_cents`, `amount_received_cents`, `change_amount_cents`, `cash_amount_cents`, `created_at` | `payment_status` (PENDING only) | `mpesa_receipt` | `payment_status`, `mpesa_receipt` |
| SaleItem | **all fields** (append-only) | — | — | — |
| PaymentTransaction | `id`, `sale_id`, `amount_cents`, `phone_number`, `created_at` | `status` (terminal), `result_code`, `result_desc`, `updated_at` | `checkout_request_id`, `merchant_request_id`, `mpesa_receipt_number`, `raw_payload_ref` | `status`, `result_code`, `result_desc`, `updated_at`, receipt fields |

\* `stock_quantity` mutation is constrained by §6.4, not free-form.

**Rule of thumb:** once a sale or payment is recorded, its money, its lines, and its identity can never be
edited — only *status* fields advance, and only the server advances them. Corrections are new events.

---

## 4. State machines

### 4.1 `Sale.payment_status`

```
CASH / CARD sales:  created as PAID (device) ── terminal
MPESA sales:        created as PENDING (device)
                      ├─ any PaymentTransaction SUCCESS  → PAID     (server)
                      ├─ ≥1 PENDING remains               → PENDING
                      └─ all attempts FAILED, none ok     → FAILED   (server)
SPLIT sales:        created as PENDING (device; cash leg taken at counter)
                      ├─ any PaymentTransaction SUCCESS  → PAID     (server)
                      └─ every attempt FAILED / expired   → PENDING   (retryable)
```

`PAID` and `FAILED` are terminal in v1. A `SPLIT` sale whose M-Pesa leg fails does **not** flip to `FAILED`:
cash was already collected and the till may retry the M-Pesa remainder with a new STK push. It stays `PENDING`
until a leg succeeds; an operator resolves abandoned ones. Refunds/reversals are separate future events (deferred).

### 4.2 `PaymentTransaction.status`

```
PENDING ──result_code == 0──▶ SUCCESS   (terminal, sets mpesa_receipt_number)
   └─────result_code != 0──▶ FAILED    (terminal)
```

A callback arriving after a terminal state is a **duplicate** → persist to the raw callback log, return
HTTP 200, change nothing. A success that arrives after `FAILED` (late/racy delivery) is flagged for manual
reconciliation; it must **not** auto-flip the transaction or the sale.

### 4.3 `sync_status` (device-local)

```
PENDING ──push──▶ SYNCING ──ack──▶ SYNCED
   ▲                  │
   │                  ├──retryable error──▶ FAILED ──retry──▶ SYNCING
   │                  └──rejection/conflict──▶ CONFLICT (needs resolution; blocks deletion)
```

`SYNCED` rows are immutable on-device except for the server-controlled status fields in §3.

---

## 5. Uniqueness, idempotency, and Daraja callbacks

### 5.1 Unique constraints

| Constraint | Purpose |
|---|---|
| `branch.code` UNIQUE | stable branch identity in receipts. |
| `category (branch_id, lower(name))` UNIQUE where `deleted_at IS NULL` | no duplicate categories. |
| `product (branch_id, sku)` UNIQUE where `sku IS NOT NULL` | SKU per branch. |
| `product (barcode)` UNIQUE where `barcode IS NOT NULL` | global barcode identity. |
| `sale (branch_id, receipt_number)` UNIQUE | receipts unique even across devices (device code embedded). |
| `payment_transaction (checkout_request_id)` UNIQUE | exactly one txn per Daraja checkout attempt. |
| `payment_transaction (mpesa_receipt_number)` UNIQUE where not null | **prevents double-counting duplicate Daraja callbacks.** |
| `sale_item.id`, `payment_transaction.id` PK | append-only identity. |

### 5.2 Callback processing algorithm (backend)

1. Authenticate webhook, parse `Body.stkCallback`.
2. `SELECT … FOR UPDATE` by `checkout_request_id`. Not found → record orphan callback, return 200.
3. If `status != PENDING` (duplicate delivery) → append raw payload to callback log, return 200. **No state change.**
4. Persist raw payload to object storage; set `raw_payload_ref` (write-once).
5. `result_code = 0` → `status := SUCCESS`, set `mpesa_receipt_number` (unique guard), `merchant_request_id`.
   Else → `status := FAILED`, `result_code`, `result_desc`.
6. Recompute parent `Sale.payment_status` per §4.1; copy winning receipt into `Sale.mpesa_receipt` (write-once).
7. One DB transaction. Unique constraints make replays safe.
8. Always return HTTP 200 so Daraja stops retrying; log non-200 paths.

### 5.3 Money & rounding

Tax is computed per line at sale time:
`line_tax = round_half_up(line_total_cents * tax_rate_bp / 10000)`, then `tax_amount_cents = Σ line_tax`.
`total = subtotal + tax`. The POS sends the computed values; the backend validates them against the
products' stored `price_cents`/`tax_rate_bp` and rejects mismatches (prices may have changed since the
offline sale — see §6.5). All arithmetic is integer.

---

## 6. Offline & sync rules

### 6.1 Push protocol

- Clients maintain an **outbox**: every dirty row carries `sync_status = PENDING`.
- Push payload = batch of **upserts keyed by `id`** (idempotent). Server returns per-row acks.
- Server assigns `server_version` (monotonic) on every accepted write; clients persist it.
- Replaying a batch after timeout is always safe (PK + unique constraints make it idempotent).

### 6.2 Conflict resolution

- **Catalog metadata** (Branch, Category, Product non-stock fields): last-writer-wins using `updated_at`;
  server clock breaks ties. The loser is preserved in an audit log, not silently dropped.
- **Sales, SaleItems, PaymentTransactions**: append-only, never conflict.
- Clients must not delete any row that is `PENDING`/`SYNCING`/`CONFLICT`; soft-delete tombstones only.

### 6.3 Clock policy

Device `created_at` is recorded as-is (immutable). The server records `received_at`. All ordering used for
sync is by `server_version`, never by raw device timestamps.

### 6.4 Stock

- Offline sale completion decrements `stock_quantity` locally in the same local DB transaction as the sale.
- On sync, the server applies the **sale's line quantities as deltas** (not the client's absolute value),
  inside a DB transaction, and recomputes stock. This avoids lost updates between devices.
- Admin restock is an absolute set (`stock_quantity := N`) plus a future movement-audit row (v2 ledger).
- Negative stock is allowed on the device (sales can't be blocked offline) and flagged server-side.

### 6.5 Price changes while offline

If a product's server price changed after an offline sale was recorded, the sale keeps its snapshot
(`unit_price_cents`) — the server must accept it and flag a price-drift warning, never rewrite the sale.

---

## 7. Mapping to current code

### 7.1 `orangemain` — SQLDelight (`shared/src/commonMain/sqldelight/.../local/*.sq`)

| Current | Contract change |
|---|---|
| `ProductEntity.price REAL` | → `price_cents INTEGER` (`round(price * 100)` in migration). |
| `ProductEntity.cost_price REAL` | → `cost_price_cents INTEGER`. |
| `ProductEntity.category TEXT` (free text) | → `category_id TEXT` FK to Category; migrate names → category rows per branch. |
| `ProductEntity.synced INTEGER` | → `sync_status TEXT` (drop boolean). |
| — missing — | add `branch_id`, `tax_rate_bp`, `deleted_at`. |
| `CategoryEntity` (id, name) | add `branch_id`, `sort_order`, `updated_at`, `deleted_at`, `sync_status`; default seeds → UUIDv5. |
| `SaleEntity.subtotal/tax_amount/total_amount/amount_received/change_amount REAL` | → `*_cents INTEGER`. |
| `SaleEntity.mpesa_ref` | → `mpesa_receipt` (write-once, server-populated). |
| — missing — | add `branch_id`, `device_id`, `cashier_id` (currently only `cashier_name`), `payment_status`, `sync_status`. |
| `SaleEntity.synced INTEGER` | → `sync_status TEXT`. |
| `SaleItemEntity.unit_price/total_price REAL` | → `unit_price_cents`/`line_total_cents`. Keep `product_name` snapshot. |
| `SaleItemEntity` id `"${saleId}_$index"` | → independent client UUIDs per row. |
| `CartRepository` in-memory | out of contract (not persisted). |

Stock: `TransactionRepository.completeSale()` now decrements `stock_quantity` via a new `decrementStock`
SQLDelight query inside the same DB transaction as the sale + items (§6.4). `updateStock()` remains for
absolute restock sets; sale completion uses the atomic decrement form to avoid read-modify-write races.

### 7.2 `orangeadmin` — currently mock UI models (`screens/main/*.kt`)

| Current | Contract change |
|---|---|
| `Branch(id, name, location, devicesCount, todaySales, isActive)` | **Applied:** `id` → UUID; `code` added; `devicesCount`/`todaySales` moved to `BranchStats` mock (stats endpoint later); `isActive` kept on the client model, maps to `is_active` on the wire. |
| `InventoryItem(id, name, quantity, isLowStock)` | **Applied:** derived from `MockInventoryData`; `isLowStock` computed from `stock <= 5`. |
| `InventoryProduct(id, name, category, stock, price: String, isLowStock)` | **Applied:** `price` string → `priceCents: Long` (KES cents, displayed via `formatKes`); `category` → `categoryId` (UUID, names via `MockCategoryData`); `isLowStock` derived. |
| `MockBranchData` / `MockInventoryData` | **Applied:** mocks now UUID-formatted with KES cents and contract field names; replace with API-backed repositories in Step 3. |
| Retrofit `NetworkModule.baseUrl("https://api.example.com/")` | **Pending:** point at real backend; request/response DTOs must mirror §8 exactly. |

### 7.3 Backend (new)

PostgreSQL schema per §9; JSON DTOs per §8; callback handler per §5.2. No field renames on the wire.

---

## 8. Wire format examples (snake_case JSON)

Product (push):

```json
{
  "id": "019a3c58-6f2e-7c31-9c1d-8a2f9b0c4d11",
  "branch_id": "019a3c40-1111-7000-8000-000000000001",
  "category_id": "019a3c41-2222-7000-8000-000000000002",
  "name": "Navel Oranges (kg)",
  "sku": "PRD-0042",
  "barcode": "6161100012345",
  "price_cents": 120,
  "tax_rate_bp": 1600,
  "cost_price_cents": 80,
  "stock_quantity": 12,
  "image_url": null,
  "is_active": true,
  "created_at": "2026-11-21T09:31:07.412Z",
  "updated_at": "2026-11-21T09:31:07.412Z",
  "deleted_at": null
}
```

Sale with items (push):

```json
{
  "id": "019a3c58-aaaa-7c31-9c1d-000000000010",
  "receipt_number": "ORG-NBO-CBD-01-D0042-20261121-0007",
  "branch_id": "019a3c40-1111-7000-8000-000000000001",
  "device_id": "019a3c40-dddd-7000-8000-0000000000d4",
  "cashier_id": "019a3c40-cccc-7000-8000-0000000000c3",
  "cashier_name": "Wanjiku M.",
  "payment_method": "MPESA",
  "subtotal_cents": 2400,
  "tax_amount_cents": 384,
  "total_amount_cents": 2784,
  "amount_received_cents": 2784,
  "change_amount_cents": 0,
  "payment_status": "PENDING",
  "mpesa_receipt": null,
  "created_at": "2026-11-21T09:31:07.412Z",
  "items": [
    {
      "id": "019a3c59-0001-7000-8000-000000000001",
      "product_id": "019a3c58-6f2e-7c31-9c1d-8a2f9b0c4d11",
      "product_name": "Navel Oranges (kg)",
      "quantity": 20,
      "unit_price_cents": 120,
      "line_total_cents": 2400
    }
  ]
}
```

Client pushes `payment_status`/`mpesa_receipt` for cash/card sales; for M-Pesa the server ignores client
values and owns the fields.

---

## 9. PostgreSQL DDL sketch (v1)

```sql
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

CREATE TABLE sale (
  id                   UUID PRIMARY KEY,
  receipt_number       TEXT NOT NULL,
  branch_id            UUID NOT NULL REFERENCES branch(id),
  device_id            TEXT NOT NULL,
  cashier_id           UUID NOT NULL,          -- REFERENCES users(id) once users table exists
  cashier_name         TEXT NOT NULL,
  payment_method       TEXT NOT NULL CHECK (payment_method IN ('CASH','MPESA','CARD','SPLIT')),
  subtotal_cents       BIGINT NOT NULL CHECK (subtotal_cents >= 0),
  tax_amount_cents     BIGINT NOT NULL CHECK (tax_amount_cents >= 0),
  total_amount_cents   BIGINT NOT NULL CHECK (total_amount_cents >= 0),
  amount_received_cents BIGINT NOT NULL CHECK (amount_received_cents >= 0),
  change_amount_cents  BIGINT NOT NULL DEFAULT 0 CHECK (change_amount_cents >= 0),
  cash_amount_cents    BIGINT NOT NULL DEFAULT 0
                         CHECK (cash_amount_cents BETWEEN 0 AND total_amount_cents),
  payment_status       TEXT NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PAID','FAILED')),
  mpesa_receipt        TEXT,
  created_at           TIMESTAMPTZ NOT NULL,
  received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  server_version       BIGINT NOT NULL DEFAULT 0,
  UNIQUE (branch_id, receipt_number),
  -- Split shape: SPLIT carries a non-zero cash leg below the total; other methods carry none.
  CONSTRAINT sale_split_shape CHECK (
    (payment_method =  'SPLIT' AND cash_amount_cents > 0 AND cash_amount_cents < total_amount_cents)
    OR (payment_method <> 'SPLIT' AND cash_amount_cents = 0)
  )
);

CREATE TABLE sale_item (
  id               UUID PRIMARY KEY,
  sale_id          UUID NOT NULL REFERENCES sale(id),
  product_id       UUID NOT NULL REFERENCES product(id),
  product_name     TEXT NOT NULL,
  quantity         INTEGER NOT NULL CHECK (quantity > 0),
  unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents >= 0),
  line_total_cents BIGINT NOT NULL CHECK (line_total_cents >= 0)
);

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
```

---

## 10. Deferred decisions (explicitly out of v1)

1. **Catalog-wide products + `branch_inventory`** (v2): today `product.branch_id` is the v1 simplification.
2. **Refunds / voids / returns**: events referencing original `sale_id` (sale stays immutable).
3. **Per-product `low_stock_threshold`** (v1 uses branch default = 5).
4. **`stock_movement` ledger** with audit reasons (v2; v1 writes deltas + restock sets).
5. **Users/cashiers contract** (login, roles) — orangeadmin already has auth endpoints; separate spec.
6. **Devices contract** (registration, `DEVICE_CODE`, revoked devices) — needed before receipt format is
   final; v1 assumes a stable 4-char `DEVICE_CODE` per registered device.
7. **eTIMS / KRA invoice fields** — later compliance layer; do not add ad-hoc fields now.

---

## 11. Change log vs. the Step 0 field list

Original proposed fields were accepted with these changes (all deliberate):

| # | Change | Why |
|---|---|---|
| 1 | All money as `*_cents` integers (Long/BIGINT); tax as `*_bp` | float money corrupts cents; mandatory for payments/inventory. |
| 2 | IDs: UUID strings, UUIDv7 recommended; UUIDv5 for seeded defaults | offline-safe + deterministic seeds. |
| 3 | `sync_status` marked **local-only**; added `deleted_at` tombstones | sync metadata must not pollute the server schema; deletes must propagate. |
| 4 | Added `Category.branch_id`, `sort_order`; product `category_id` is a FK | current `Product.category` is free text; FK removes name-drift. |
| 5 | `Sale`: added `device_id`, `payment_method`, `subtotal_cents`, `tax_amount_cents`, `amount_received_cents`, `change_amount_cents`, `cashier_name` snapshot | receipts/reconciliation need them; current app computes/persists them anyway. |
| 6 | `receipt_number` format includes `DEVICE_CODE`; uniqueness `(branch_id, receipt_number)` | two offline devices would otherwise collide on day sequences. |
| 7 | `SaleItem`: added `product_name` snapshot and `line_total_cents`; fully append-only | receipts must show historical names/prices. |
| 8 | `PaymentTransaction`: backend-generated id; `checkout_request_id` unique; `mpesa_receipt_number` **partial-unique write-once**; `status` terminal; added `merchant_request_id`, `result_code`, `result_desc`, `updated_at` | idempotent Daraja callbacks; no double-counting. |
| 9 | `Sale.payment_status` is server-derived for M-Pesa; client only sets PAID for cash/card | a device cannot know an STK push succeeded. |
| 10 | `Sale.mpesa_receipt` kept but defined as write-once denormalized cache | one canonical source (PaymentTransaction) + one convenience copy for reports. |
| 11 | `Branch.devicesCount` / `todaySales` are not entity fields; `isActive` → `is_active` | presentational/aggregate data would go stale in sync. |
| 12 | Phone numbers E.164; timestamps RFC 3339 UTC with ms | unambiguous transport. |
| 13 | `Sale`: added `SPLIT` method + `cash_amount_cents` with `sale_split_shape` check; SPLIT stays `PENDING` on M-Pesa failure | part-cash/part-M-Pesa tender at the till; cash collected must not be stranded by a failed STK, so the sale stays retryable. |
