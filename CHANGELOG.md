# Orange POS — Changelog

**Project:** Distributed Real-Time Payment and Inventory System for the Kenyan Retail Sector
**Repository:** y4p (monorepo: `backend`, `orangeadmin`, `orangemain`)
**Snapshot date:** 2026-11-25
**Baseline:** commits through `673cda9` plus the current working-tree changes (not yet committed)

---

## 1. This round at a glance

| Area | Change | State |
|---|---|---|
| Sync integrity | Last-write-wins hardening with clock-skew rejection and audit logging of dropped writes | Done, tested |
| Reports API | Sales-trend (7/14/30/90-day) and branch-comparison endpoints | Done, tested |
| Admin reports UI | Reports screen with day-range chips, per-branch bar chart, comparison list | Done |
| Device heartbeat | POS sends `outbox_pending` each sync cycle; admin shows last-seen with stale flag | Done |
| Credential transfer | Admin shares device credentials via QR + copy text; POS setup accepts paste/scan | Done |
| Admin settings | Settings → Payment screen for local-only Daraja configuration | Done |
| API contract | Device share/heartbeat/reports endpoints documented | Done |
| Proposal document | Revised Markdown brought in line with the implementation (20-item revision) | Done |
| Verification | Backend 35/35 tests, both Android apps build, admin unit tests pass | Done |

---

## 2. Committed history covered by this snapshot

- `663184d` — orangeadmin wired to the v1 API; admin flows hardened.
- `94e3ee2` — session crypto recovery and shared components restored.
- `fa3e76e` — device revocation and owner seeding.
- `cbc364d` — seeded account promoted to OWNER on startup.
- `673cda9` — POS sync, cash checkout entry, and admin refresh fixes.

---

## 3. Backend (`backend/`, Node + TypeScript, v1 API)

### 3.1 Auth and roles
- Login, token refresh with rotation, logout, account deletion, and `/auth/me` with branch scope.
- A seeded account is created or promoted to **OWNER** on startup; OWNER-only user creation, MANAGER restricted to its branch.

### 3.2 Catalog
- Branch-scoped categories and products with create/patch/restock/delete lifecycle.
- Duplicate category names and SKUs rejected per branch; branches with an active device cannot be deleted.

### 3.3 Devices
- Device provisioning, per-device credentials, heartbeat, revocation, and permanent deletion.
- **New:** `POST /devices/heartbeat` — POS reports `device_id` and `outbox_pending`; server records `last_seen_at` so the admin can distinguish live devices from stale ones (>24 h = stale, never seen = flagged).

### 3.4 Sync (`/sync`)
- Push/pull of products, categories, and sales with last-write-wins conflict resolution.
- **New hardening:** writes stamped more than 5 minutes ahead of server time are rejected (`CLOCK_AHEAD`); writes that lose LWW (older than the stored row, including tombstones) are recorded to the sync audit log (`SYNC_STALE_WRITE`) so dropped offline writes are visible instead of silently vanishing.
- Idempotency-Key replay returns the stored response rather than re-running the write.

### 3.5 M-Pesa STK and reconciliation
- STK push with local phone-format normalization to E.164, in-flight duplicate protection, amount/total match validation.
- Daraja callback handling: success marks the sale PAID; duplicate callbacks are no-ops; late or receipt-less successes are queued for human review; timeout sweeper fails stale attempts.
- Reconciliation review queue with apply/discard actions.

### 3.6 Reports (new)
- `GET /branches/:id/reports/sales-trend?days=N` (1–90 days, UTC, zero-filled, PAID sales only).
- `GET /reports/branch-comparison` — per-branch revenue, sale count, low-stock count (≤5 units), and active (non-revoked) devices; sorted by revenue.
- Coverage in `test/stats.test.ts` (new) alongside the sync tests.

---

## 4. Admin app (`orangeadmin/`, Android, Kotlin + Compose)

- Wired to the v1 API: auth, repositories, settings storage, network layer.
- **Credential transfer (new):** share a provisioned device's credentials with the POS through a QR code plus human-readable copy text; POS can scan or paste. Unit tests for the QR payload and share text.
- **Reports (new):** Reports screen with 7/14/30-day range chips, per-branch bar chart, and proportional branch-comparison list fed by the new stats endpoints; added to main navigation.
- **Device visibility:** device list shows `last_seen_at` with "never"/stale highlighting; revocation and permanent deletion flows in place.
- **Settings → Payment (new):** local-only Daraja fields (paybill / buy-goods till, consumer key, consumer secret, passkey, shortcode) stored in DataStore and deliberately never synced.
- Cart/category/product management, sales listing with keyset cursor pagination.
- Refresh behavior fixed so screens reload cleanly after writes.

---

## 5. POS app (`orangemain/`, KMP + Compose)

- Device setup accepts credentials by paste or QR scan (shared parsing utility with tests, platform clipboard service via expect/actual).
- Sync service pulls catalog and pushes queued sales with retries; **new:** sends a heartbeat (including outbox depth) every sync cycle.
- Checkout: cash entry fix and full M-Pesa STK flow (pending → paid/failed/timeout surfaced to the cashier).
- Session crypto recovery restored; shared components reinstated.

---

## 6. Docs

- `API_CONTRACT.md` — updated for device credential sharing, heartbeat payload, and the reports endpoints (device share/heartbeat/reports sections).
- `project-description-revised.md` — fully rewritten to match the implementation; 20-item revision approved 2026-11-25.
- The proposal **PDF** still needs a manual re-export from the updated Markdown (Google Docs), as agreed.

---

## 7. Verification (2026-11-25)

| Check | Result |
|---|---|
| Backend typecheck | Pass |
| Backend test suite (`npm test`, local Postgres 16) | **35/35 pass, 0 fail** |
| orangeadmin `:app:assembleDebug` | BUILD SUCCESSFUL |
| orangeadmin `:app:testDebugUnitTest` | BUILD SUCCESSFUL |
| orangemain `:androidApp:assembleDebug` | BUILD SUCCESSFUL |

The backend suite runs against a local test cluster at `127.0.0.1:55432` (`orange_test`); it truncates `app_user`, so re-seed the owner login afterward for local manual testing.

---

## 8. Open items / known gaps

1. **Daraja STK prompt (P0 before demo):** sandbox app lacks the STK product (`404.001.03`), and the callback URL needs the `secret` query parameter. Configuration-only fix.
2. Reconciliation admin UI — API exists; screen deferred to the next step.
3. Admin profile editing (name/password) — endpoint behavior defined, UI pending.
4. Backend N+1 cleanup on branch/product listings; catalog category-deletion race noted for later hardening.
5. Cashier branch-membership strict validation deferred; cashier accounts remain branch-scoped by deployment.
6. Admin app is online-only by design; POS is offline-first.
7. Working tree is uncommitted — commit only on request, per the contract-first, step-gated workflow.
