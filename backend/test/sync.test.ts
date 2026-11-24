import assert from "node:assert/strict";
import { after, beforeEach, describe, it } from "node:test";
import {
  closePool,
  iso,
  login,
  makeApp,
  pool,
  productPayload,
  provisionDevice,
  pushBatch,
  resetDb,
  salePayload,
  seedBranch,
  seedUser,
} from "./helpers.js";

type App = Awaited<ReturnType<typeof makeApp>>["app"];
type Result = {
  entity: string;
  id: string;
  status: string;
  code?: string;
  message?: string;
  warning?: string;
  server_version?: number;
  payment_status?: string;
};

async function setup() {
  const { app } = await makeApp();
  const branchId = await seedBranch();
  await seedUser({ email: "owner@example.com", role: "OWNER", branchIds: [] });
  const { access } = await login(app);
  const device = await provisionDevice(app, access, branchId);
  return { app, branchId, access, device, deviceToken: device.deviceToken };
}

function pull(
  app: App,
  token: string,
  query = "",
) {
  return app.inject({
    method: "GET",
    url: `/api/v1/sync/pull${query}`,
    headers: { "x-device-token": token },
  });
}

function findResult(body: { results: Result[] }, entity: string, id: string): Result {
  const row = body.results.find((r) => r.entity === entity && r.id === id);
  assert.ok(row, `no ${entity} result for ${id}`);
  return row!;
}

describe("sync", () => {
  beforeEach(resetDb);
  after(closePool);

  it("pushes catalog rows and pulls them back", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId);
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      categories: [],
      products: [product],
      sales: [],
    });
    assert.equal(push.statusCode, 200, push.body);
    const body = push.json() as { batch_id: string; server_version: number; results: Result[] };
    const row = findResult(body, "product", product.id);
    assert.equal(row.status, "SYNCED");
    assert.ok(row.server_version! > 0);
    assert.ok(body.server_version >= row.server_version!);

    const pulled = await pull(app, device.deviceToken);
    assert.equal(pulled.statusCode, 200, pulled.body);
    const page = pulled.json() as {
      server_version: number;
      has_more: boolean;
      changes: { branches: { id: string }[]; products: { id: string; price_cents: number }[]; sales: unknown[] };
      tombstones: unknown[];
    };
    assert.equal(page.changes.products.length, 1);
    assert.equal(page.changes.products[0]!.id, product.id);
    assert.equal(page.changes.products[0]!.price_cents, product.price_cents);
    assert.equal(page.changes.branches.length, 1);
    assert.equal(page.changes.branches[0]!.id, branchId);
    assert.equal(page.changes.sales.length, 0, "device sales only flow down after a server-side payment update");
    assert.deepEqual(page.tombstones, []);
    assert.equal(page.has_more, false);

    const incremental = await pull(app, device.deviceToken, `?since=${page.server_version}`);
    const empty = incremental.json() as { changes: { products: unknown[] }; tombstones: unknown[] };
    assert.equal(empty.changes.products.length, 0);
  });

  it("rejects a stale write with LAST_WRITE_WINS", async () => {
    const { app, branchId, device } = await setup();
    const fresh = productPayload(branchId, { name: "Fresh" });
    await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [fresh],
    });

    const stale = productPayload(branchId, {
      id: fresh.id,
      name: "Stale",
      created_at: iso(-120_000),
      updated_at: iso(-90_000),
    });
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [stale],
    });
    assert.equal(push.statusCode, 200, push.body);
    const row = findResult(push.json() as { results: Result[] }, "product", fresh.id);
    assert.equal(row.status, "CONFLICT");
    assert.equal(row.code, "LAST_WRITE_WINS");

    const { rows } = await pool.query<{ name: string }>(`SELECT name FROM product WHERE id = $1`, [fresh.id]);
    assert.equal(rows[0]!.name, "Fresh");
  });

  it("applies a sale's stock delta exactly once", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId, { stock_quantity: 10 });
    const sale = salePayload(branchId, device.deviceId, { id: product.id, name: product.name });
    const payload = {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
      sales: [sale],
    };

    const first = await pushBatch(app, device.deviceToken, payload);
    assert.equal(first.statusCode, 200, first.body);
    const firstRow = findResult(first.json() as { results: Result[] }, "sale", sale.id);
    assert.equal(firstRow.status, "SYNCED");
    assert.equal(firstRow.payment_status, "PAID");

    const stockAfter = await pool.query<{ stock_quantity: number }>(
      `SELECT stock_quantity FROM product WHERE id = $1`,
      [product.id],
    );
    assert.equal(stockAfter.rows[0]!.stock_quantity, 8);

    const replay = await pushBatch(app, device.deviceToken, payload);
    const replayRow = findResult(replay.json() as { results: Result[] }, "sale", sale.id);
    assert.equal(replayRow.code, "ALREADY_APPLIED");
    assert.equal(replayRow.server_version, firstRow.server_version);

    const stockReplay = await pool.query<{ stock_quantity: number }>(
      `SELECT stock_quantity FROM product WHERE id = $1`,
      [product.id],
    );
    assert.equal(stockReplay.rows[0]!.stock_quantity, 8);
  });

  it("rejects a re-used receipt_number with RECEIPT_DUPLICATE", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId);
    const receipt = "M1-SHARED-1";
    await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
      sales: [salePayload(branchId, device.deviceId, { id: product.id }, { receipt_number: receipt })],
    });

    const dup = salePayload(branchId, device.deviceId, { id: product.id }, { receipt_number: receipt });
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      sales: [dup],
    });
    const row = findResult(push.json() as { results: Result[] }, "sale", dup.id);
    assert.equal(row.status, "REJECTED");
    assert.equal(row.code, "RECEIPT_DUPLICATE");
  });

  it("keeps M-Pesa payment state server-owned", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId);
    const sale = salePayload(
      branchId,
      device.deviceId,
      { id: product.id },
      { payment_method: "MPESA", payment_status: "PAID", mpesa_receipt: "QK-FORGED" },
    );
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
      sales: [sale],
    });
    const row = findResult(push.json() as { results: Result[] }, "sale", sale.id);
    assert.equal(row.status, "SYNCED");
    assert.equal(row.payment_status, "PENDING");

    const { rows } = await pool.query<{ payment_status: string; mpesa_receipt: string | null }>(
      `SELECT payment_status, mpesa_receipt FROM sale WHERE id = $1`,
      [sale.id],
    );
    assert.equal(rows[0]!.payment_status, "PENDING");
    assert.equal(rows[0]!.mpesa_receipt, null);
  });

  it("flags a sale that changed an immutable field", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId);
    const sale = salePayload(branchId, device.deviceId, { id: product.id });
    await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
      sales: [sale],
    });

    const edited = { ...sale, total_amount_cents: sale.total_amount_cents + 100 };
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      sales: [edited],
    });
    const row = findResult(push.json() as { results: Result[] }, "sale", sale.id);
    assert.equal(row.status, "CONFLICT");
    assert.equal(row.code, "SALE_IMMUTABLE");
    assert.match(row.message ?? "", /total_amount_cents/);

    const { rows } = await pool.query<{ total_amount_cents: string }>(
      `SELECT total_amount_cents FROM sale WHERE id = $1`,
      [sale.id],
    );
    assert.equal(Number(rows[0]!.total_amount_cents), sale.total_amount_cents);
  });

  it("warns PRICE_DRIFT when the device price differs from the catalog", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId, { price_cents: 50_000 });
    const sale = salePayload(
      branchId,
      device.deviceId,
      { id: product.id, price_cents: 60_000 },
      { subtotal_cents: 120_000, total_amount_cents: 120_000, amount_received_cents: 120_000 },
    );
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
      sales: [sale],
    });
    const row = findResult(push.json() as { results: Result[] }, "sale", sale.id);
    assert.equal(row.status, "SYNCED");
    assert.equal(row.warning, "PRICE_DRIFT");
  });

  it("turns product tombstones into pull tombstones", async () => {
    const { app, branchId, device } = await setup();
    const product = productPayload(branchId);
    await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      products: [product],
    });

    const deletedAt = iso(-1_000);
    const push = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      tombstones: [{ entity: "product", id: product.id, deleted_at: deletedAt }],
    });
    assert.equal(push.statusCode, 200, push.body);
    const row = findResult(push.json() as { results: Result[] }, "product", product.id);
    assert.equal(row.status, "SYNCED");

    const page = (await pull(app, device.deviceToken)).json() as {
      changes: { products: { id: string; deleted_at: string | null }[] };
      tombstones: { entity: string; id: string; deleted_at: string }[];
    };
    const tombstone = page.tombstones.find((t) => t.entity === "product" && t.id === product.id);
    assert.ok(tombstone, "product tombstone surfaced in pull");
    assert.equal(page.changes.products.find((p) => p.id === product.id)!.deleted_at, tombstone!.deleted_at);

    const branchStone = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: branchId,
      tombstones: [{ entity: "branch", id: branchId, deleted_at: deletedAt }],
    });
    const branchRow = findResult(branchStone.json() as { results: Result[] }, "branch", branchId);
    assert.equal(branchRow.status, "REJECTED");
    assert.equal(branchRow.code, "UNSUPPORTED_ENTITY");
  });

  it("rejects payloads that do not match the device's branch or id", async () => {
    const { app, branchId, device } = await setup();
    const otherBranch = await seedBranch("Other", "OTHER");

    const wrongBranch = await pushBatch(app, device.deviceToken, {
      device_id: device.deviceId,
      branch_id: otherBranch,
    });
    assert.equal(wrongBranch.statusCode, 403);
    assert.equal(wrongBranch.json().error.code, "BRANCH_SCOPE_MISMATCH");

    const wrongDevice = await pushBatch(app, device.deviceToken, {
      device_id: "some-other-device",
      branch_id: branchId,
    });
    assert.equal(wrongDevice.statusCode, 403);
    assert.equal(wrongDevice.json().error.code, "DEVICE_SCOPE_MISMATCH");
  });

  it("reports fleet diagnostics and persisted app_version on heartbeat", async () => {
    const { app, device } = await setup();
    const res = await app.inject({
      method: "POST",
      url: "/api/v1/devices/heartbeat",
      headers: { "x-device-token": device.deviceToken },
      payload: { app_version: "1.2.3", outbox_pending: 4 },
    });
    assert.equal(res.statusCode, 200, res.body);
    const body = res.json() as {
      server_time: string;
      config: { stk_timeout_minutes: number; low_stock_threshold: number };
    };
    assert.equal(typeof body.server_time, "string");
    assert.equal(body.config.low_stock_threshold, 5);
    assert.ok(body.config.stk_timeout_minutes > 0);

    const { rows } = await pool.query<{ app_version: string | null; last_seen_at: Date | null }>(
      `SELECT app_version, last_seen_at FROM device WHERE id = $1`,
      [device.deviceId],
    );
    assert.equal(rows[0]!.app_version, "1.2.3");
    assert.ok(rows[0]!.last_seen_at);
  });
});
