# Orange POS — Step 2: API Contract & Sync/Payment Flows (v0.1 — draft for sign-off)

Date: 2026-11-21
Depends on: `DATA_CONTRACT.md` v1 (**locked**, 2026-11-21). All entity shapes and mutation rules come from
there; this document only defines transport, endpoints, and flows.

Status: **DRAFT** — confirm the decisions in §6, then implement in Step 3.

---

## 0. Conventions

| Topic | Rule |
|---|---|
| Base path | `https://api.<host>/api/v1` — version in the path; additive changes only within v1. |
| Format | JSON UTF-8, snake_case keys, RFC 3339 UTC timestamps, integer cents. Entity JSON is exactly `DATA_CONTRACT.md` §8 (plus envelope fields below). |
| Errors | `{"error": {"code": "MACHINE_CODE", "message": "human text", "details": {...}}}`. Codes are stable; messages are not. |
| Idempotency | Every mutation accepts an optional `Idempotency-Key` header. `POST /sync/push` is inherently idempotent by record `id`. |
| Pagination | `?limit=100&cursor=<opaque>`; response `{"data": [...], "next_cursor": "..."}`. |
| Limits | 5 MB / 200 records per sync batch. |
| Clock | Client timestamps are stored as-is; ordering, expiry, and rate limits use **server** time. |

---

## 1. Auth & scoping

### 1.1 Admin user (orangeadmin)

JWT access + rotating refresh. Passwords hashed Argon2id server-side.

| Endpoint | Body / Notes |
|---|---|
| `POST /auth/login` | `{email, password}` → `{access_token (15 min), refresh_token (30 d), user: {id, name, email, role: OWNER\|MANAGER, branch_ids: [uuid]}}` |
| `POST /auth/refresh` | `{refresh_token}` → new pair; old token revoked (**rotation**). Reuse of a revoked token → 401 and revoke the whole token family. |
| `POST /auth/logout` | Bearer + `{refresh_token}` → 204. Revokes the refresh family. |
| `GET /auth/me` | → current user (roles/branches) — for session restore. |
| `DELETE /auth/account` | Password re-confirm; soft-deletes the user and revokes tokens (covers orangeadmin's existing delete-account screen). |

orangeadmin's current `/api/login`, `/api/register`, `/api/refresh-token`, `/api/logout`, `/api/user` map to
these paths in Step 3 (register stays admin-only/invite-based: `POST /auth/users` by OWNER).

### 1.2 POS device (orangemain)

Devices are provisioned by an admin and get their own long-lived credential.

| Endpoint | Body / Notes |
|---|---|
| `POST /branches/{branch_id}/devices` | (Admin) `{name}` → `{device_id, device_code (4 chars A–Z0–9), device_token, branch_id}`. `device_code` is embedded in receipt numbers and immutable for the device's lifetime. |
| `POST /devices/heartbeat` | (Device) `{app_version?, outbox_pending}` → `{server_time, config:{...}}` — validates the token and refreshes `last_seen_at`. The POS sends one per sync cycle (launch, every 60s, manual Sync). |
| `DELETE /devices/{device_id}` | (Admin) revoke. Further device calls → 401 `DEVICE_REVOKED`. |
| `DELETE /devices/{device_id}/permanent` | (Owner) delete an **already revoked** device row; active → 409 `DEVICE_ACTIVE`. Sales keep the plain-text `device_id`, so history is unaffected. |

Device requests authenticate with `X-Device-Token: <token>`. The server derives `device_id` and
`branch_id` **from the token** — payloads repeat them only for debugging and MUST match, else 403
`BRANCH_SCOPE_MISMATCH`.

### 1.3 Scoping rules

- **Admin**: any `{branch_id}` in the path must be in `token.branch_ids`; role `OWNER` implicitly has all
  branches. Violation → 403 `BRANCH_FORBIDDEN`.
- **Device**: exactly one branch per token; no cross-branch access.
- `sale.cashier_id` must reference an existing user; branch membership is validated when the users contract
  lands (v1: warn, don't reject — cashiers are created server-side).
- Roles are server-issued claims; nothing in a request body can grant scope.

---

## 2. Admin & catalog endpoints

All admin endpoints require Bearer JWT. All mutations write an audit-log row.

| Method & path | Purpose / notes |
|---|---|
| `GET /branches` | List branches; `?include_deleted=true` optional. |
| `POST /branches` | Create. Body = Branch minus `created_at`/`updated_at` (client sends `created_at`; server echoes canonical). `code` immutable afterwards. |
| `GET /branches/{id}` | Read. |
| `PATCH /branches/{id}` | Update `{name?, location?, is_active?}`. Attempts to change `id`/`code` → 409 `IMMUTABLE_FIELD`. |
| `DELETE /branches/{id}` | Soft delete (`deleted_at`). 409 `BRANCH_IN_USE` if active devices exist or sales are unsynced. |
| `GET /branches/{id}/categories` | `?updated_since=&include_deleted=`. |
| `POST /branches/{id}/categories` | Create (UUID from client). |
| `PATCH /categories/{cid}` / `DELETE /categories/{cid}` | Update / soft delete. 409 if products still reference it. |
| `GET /branches/{id}/products` | `?updated_since=&include_deleted=&search=`. |
| `POST /branches/{id}/products` | Create (UUID from client, `branch_id` forced to path value). |
| `PATCH /products/{pid}` | Update non-stock fields. **Stock is not writable here** → 409 `USE_RESTOCK_ENDPOINT` (audit). |
| `POST /products/{pid}/restock` | `{stock_quantity, reason}` — absolute set + audit row (v2 movement ledger). |
| `DELETE /products/{pid}` | Soft delete. |
| `GET /branches/{id}/devices` | List devices with `{device_id, name, device_code, is_revoked, last_seen_at, app_version}`. `last_seen_at` is refreshed by `/devices/heartbeat` and every device request; orangeadmin flags >24h or missing as stale. |
| `GET /branches/{id}/sales` | Read-only drill-down `?from=&to=&payment_status=&cursor=`. |

### Stats / dashboard (read-only aggregates — never mutate entity tables)

| Method & path | Response sketch |
|---|---|
| `GET /branches/{id}/stats/today` | `{sales_total_cents, sales_count, low_stock_count, devices_count, top_sellers: [{product_id, name, quantity_sold, revenue_cents}]}` |
| `GET /dashboard/summary` | `{branches_count, active_devices, sales_today_cents, sales_today_count, pending_payments, low_stock_items}` |

This is what feeds orangeadmin's `BranchStats` / dashboard — the Branch entity itself stays lean
(`DATA_CONTRACT.md` §2.1).

### BI reports (read-only aggregates)

Both accept `?days=N` (`1..90`, default `7`) and cover whole UTC days ending today (`from`
inclusive, `to` exclusive = tomorrow 00:00 UTC). Only `PAID` sales are counted. Days without
sales are zero-filled so charts stay aligned.

| Method & path | Response sketch |
|---|---|
| `GET /branches/{id}/reports/sales-trend?days=7` | `{branch_id, branch_name, days, from, to, points: [{date: "YYYY-MM-DD", sales_total_cents, sales_count}]}` — one point per day. Branch-scoped (§1.3). |
| `GET /reports/branch-comparison?days=7` | `{days, from, to, branches: [{branch_id, name, sales_total_cents, sales_count, low_stock_items, devices_count}]}` — sorted by revenue desc, filtered to the caller's branches. `low_stock_items` uses the shared `stock_quantity <= 5` threshold; `devices_count` counts non-revoked devices. |

orangeadmin renders these as a Canvas bar chart (Sales Trends) and proportional bars
(Branch Comparison).

---

## 3. Sale sync (orangemain ⇄ backend)

### 3.1 Push

`POST /sync/push` (device token)

```json
{
  "device_id": "019a3c40-dddd-7000-8000-0000000000d4",
  "branch_id": "019a3c40-1111-7000-8000-000000000001",
  "batch_id": "019a3c59-bbbb-7000-8000-0000000000b0",
  "categories": [ /* new/changed Category rows */ ],
  "products":   [ /* new/changed Product rows */ ],
  "sales":      [ /* Sale rows, with nested "items": [SaleItem...] exactly as in DATA_CONTRACT.md §8 */ ],
  "tombstones": [ {"entity": "product", "id": "...", "deleted_at": "2026-11-21T09:00:00.000Z"} ]
}
```

Processing order per batch: **categories → products → sales** (FKs must resolve). Idempotent by `id`:
replaying a batch returns identical acks.

Response:

```json
{
  "batch_id": "019a3c59-bbbb-7000-8000-0000000000b0",
  "server_version": 1842,
  "results": [
    {"entity": "sale",    "id": "019a3c58-aaaa-...", "status": "SYNCED",   "server_version": 1840},
    {"entity": "product", "id": "019a3c58-6f2e-...", "status": "SYNCED",   "server_version": 1841},
    {"entity": "sale",    "id": "019a3c58-cccc-...", "status": "CONFLICT", "code": "SALE_IMMUTABLE",
     "message": "attempted to modify amounts of an existing sale"}
  ]
}
```

Rules:

- **sales/sale_items are append-only**: if the `id` exists, the server compares immutable fields
  (`DATA_CONTRACT.md` §3). Any difference → per-row `REJECTED`/`CONFLICT` with `SALE_IMMUTABLE`; the sale is
  never rewritten.
- Unknown FK → `REJECTED` `FK_MISSING` (client retries after syncing parents; may also be sent later in the
  same batch order).
- **Stock** is applied as **deltas from sale lines**, once per sale id, inside one DB transaction
  (`DATA_CONTRACT.md` §6.4). Client `stock_quantity` values on product rows are ignored on the server except
  for explicit admin restock; negative stock is accepted and flagged.
- **Price drift**: if `sale_item.unit_price_cents` differs from the current product price, the sale is
  accepted and the result carries `"warning": "PRICE_DRIFT"`.
- MPESA and SPLIT sales arrive with `payment_status=PENDING`; the server ignores client-sent `PAID`/`mpesa_receipt`
  for both (canonical values returned). CASH/CARD sales may arrive `PAID`.
- `cash_amount_cents` is required on sale rows (default `0`) and immutable once stored: a replay whose value
  differs → `SALE_IMMUTABLE`. A new `SPLIT` sale is rejected with `SPLIT_AMOUNT_INVALID` unless
  `0 < cash_amount_cents < total_amount_cents`, the M-Pesa remainder is `>= 100` cents and a whole number of
  KES (`remainder % 100 == 0`), `amount_received_cents == cash_amount_cents`, and `change_amount_cents == 0`.
  Non-SPLIT rows must carry `cash_amount_cents == 0`.
- Response includes the server's canonical `payment_status` for every accepted sale, so a device that was
  offline learns the current state on reconnect.

### 3.2 Pull

`GET /sync/pull?since={server_version}&branch_id={id}` (device token)

```json
{
  "server_version": 1850,
  "changes": {
    "branches":  [ /* rarely changes */ ],
    "categories": [ /* upserts */ ],
    "products":  [ /* upserts incl. server-side stock/price changes */ ],
    "sales":     [ /* status-only changes: id, payment_status, mpesa_receipt */ ]
  },
  "tombstones": [ {"entity": "product", "id": "...", "deleted_at": "..."} ]
}
```

- First sync sends `since=0`. Clients persist the highest applied `server_version`.
- Sales are only re-sent when `payment_status`/`mpesa_receipt` changes — never full sale bodies.
- Triggers: app start, foreground every 5 min, manual refresh, after a successful push.

### 3.3 Client sync state machine

```
PENDING ──push──▶ SYNCING ──ack SYNCED──▶ SYNCED
   ▲                  ├── retryable (5xx/timeout) → FAILED ──backoff──▶ SYNCING
   │                  └── REJECTED/CONFLICT → CONFLICT (surface to cashier/Settings)
```

Backoff 2 s → 4 s → 8 s … max 5 min with jitter; after 24 h unattempted/failing, surface in Settings.
`CONFLICT` rows block deletion and are visible to staff (rare with append-only sales).

---

## 4. STK push & payment status

### 4.1 Initiate

`POST /payments/stk-push` (device token)

```json
{"sale_id": "019a3c58-aaaa-...", "phone_number": "+254712345678", "amount_cents": 2784}
```

Preconditions (all server-enforced):

1. Sale exists, belongs to the token's branch, `payment_method IN (MPESA, SPLIT)`, `payment_status = PENDING`;
   otherwise 409 `NOT_MPESA_SALE`.
2. `expected = (method == SPLIT) ? total_amount_cents - cash_amount_cents : total_amount_cents`;
   `amount_cents == expected`, else 422 `AMOUNT_MISMATCH` (the response carries `expected_cents`).
   `amount_cents < 100` → 422 `AMOUNT_TOO_SMALL`; `amount_cents % 100 != 0` → 422 `AMOUNT_NOT_WHOLE_KES`
   (Daraja takes integer shillings, so a split remainder with cents must be rejected at checkout).
3. No other **PENDING** payment transaction for this sale → else 409 `STK_IN_PROGRESS` (retry allowed with
   `retry: true` only after the previous attempt is terminal or >5 min old).
4. `idempotency-key` header dedupes retries; reusing a key with a changed body → 422. The till sends a fresh
   per-attempt key (`stk-<sale_id>-<uuid>`) so an explicit retry starts a new STK instead of replaying the
   cached response.
5. Rate limit: 1 request / 3 s / sale; 10 / min / device → 429.

Server:

1. Calls Daraja STK Push with `CallBackURL = https://api.<host>/api/v1/payments/daraja/callback`,
   `AccountReference = sale.receipt_number`, `TransactionDesc = "Orange POS " + receipt_number`, amount in
   **whole KES** (Daraja takes integer shillings — convert from cents server-side).
2. On Daraja accept, inserts `PaymentTransaction` (`status=PENDING`, server UUIDv7, `checkout_request_id`,
   `merchant_request_id`).
3. Returns:

```json
{"payment_transaction_id": "...", "checkout_request_id": "...", "status": "PENDING",
 "expires_at": "2026-11-21T09:34:10.000Z", "poll_after_ms": 2000}
```

Errors: 422 `INVALID_MSISDN`, 502 `DARAJA_UNAVAILABLE` (retryable), 409 `STK_IN_PROGRESS`.

### 4.2 Daraja callback

`POST /payments/daraja/callback` — public endpoint, hardened by mTLS and/or IP allowlist + secret path;
**never** device-token authenticated. Always respond **200** after auth so Daraja stops retrying.

Processing is exactly `DATA_CONTRACT.md` §5.2:

- Duplicate callback → persist raw payload, 200, **no state change**.
- First terminal outcome wins (`PENDING → SUCCESS | FAILED`), write-once `mpesa_receipt_number`.
- Late `SUCCESS` after `FAILED` → **reconciliation queue**, never auto-flip the txn or sale.
- Raw callback JSON → object storage, `raw_payload_ref` set write-once.

### 4.3 Status back to the client (v1 = polling)

1. **Immediate poll** — `GET /payments/{payment_transaction_id}` (device token):

```json
{"status": "SUCCESS", "result_code": 0, "result_desc": "The service request is processed successfully.",
 "mpesa_receipt_number": "QGK42X8T1M", "sale": {"id": "...", "payment_status": "PAID", "mpesa_receipt": "QGK42X8T1M"}}
```

   Client polls every 2 s up to **90 s**, then every 10 s up to **5 min**; after that shows
   "Payment pending — will update" and keeps the sale `PENDING` locally.
2. **Sync pull** — terminal status changes ride the `sales` slice of `GET /sync/pull`; that is the guaranteed
   eventual delivery (e.g., after device reconnect).
3. **WebSocket/SSE push** is explicitly deferred to v1.5; polling + sync pull is sufficient for v1.

### 4.4 Timeouts

A scheduled job marks `PENDING` transactions with no callback after **5 minutes** as
`FAILED / STK_TIMEOUT` and recomputes `Sale.payment_status` (§4.1 of the data contract). A late success
after that goes to reconciliation — never auto-applied.

### 4.5 Manual reconciliation (late success after FAILED)

`reconciliation_queue` rows: `{id, checkout_request_id, mpesa_receipt_number, raw_payload_ref, detected_at,
resolved_at, resolved_by, action}`.

| Endpoint (OWNER) | Purpose |
|---|---|
| `GET /reconciliation/payments?status=open` | List flagged callbacks. |
| `POST /reconciliation/payments/{id}/resolve` | `{action: APPLY_TO_SALE \| MARK_REFUND \| DISCARD, sale_id?}` — `APPLY_TO_SALE` is the only exceptional path that can force `SUCCESS`/`PAID`, with audit trail. |

Admin UI for this is added after Step 3.

---

## 5. Security & ops notes

- TLS everywhere; device tokens + refresh tokens hashed at rest; refresh rotation with reuse detection.
- Daraja credentials live **only** on the server (removes the hardcoded sandbox key in
  `orangemain/.../MpesaService.kt`).
- Rate limits + audit log on all admin mutations; callback payloads encrypted at rest, retention ≥ 90 days.
- Clients ignore unknown JSON fields (forward compatibility); required fields missing = 400.
- MSISDN validation + E.164 normalization server-side (`07XX…`, `2547XX…`, `+2547XX…` → `+2547XX…`).

---

## 6. Decisions to confirm before Step 3

1. **Payment status delivery**: v1 polling + sync pull (recommended) vs. building WebSocket now.
2. **STK requires the sale to be synced first** (recommended: yes — server owns payment rows and validates
   amounts; requires connectivity at payment time, which STK needs anyway).
3. **PaymentTransaction is server-created**; devices never generate transaction ids.
4. **Device token lifetime**: 180 days + admin revoke (recommended), vs. short-lived with refresh.
5. **STK timeout = 5 min**, late success → reconciliation (recommended defaults).
6. **Cashier validation**: v1 accepts existing user ids with a warning; strict branch membership waits for the
   users contract (separate spec).
7. **Admin app is online-only** in v1 (no offline queue); POS-only offline scope.
8. **Single tenant** in v1 (no `tenant_id` columns yet).
