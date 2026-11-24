import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";
import { closePool, login, makeApp, pool, resetDb, seedBranch, seedUser } from "./helpers.js";

after(closePool);
beforeEach(resetDb);

async function setup() {
  const ctx = await makeApp();
  const branchId = await seedBranch();
  await seedUser({ email: "owner@example.com" });
  const { access } = await login(ctx.app);
  return { ...ctx, branchId, access };
}

function auth(access: string) {
  return { authorization: `Bearer ${access}` };
}

test("products expose a create/patch/restock/delete lifecycle", async () => {
  const { app, branchId, access } = await setup();

  const created = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers: auth(access),
    payload: { name: "Chapati", price_cents: 2000, stock_quantity: 20, tax_rate_bp: 1600 },
  });
  assert.equal(created.statusCode, 201);
  const product = created.json();
  assert.equal(product.stock_quantity, 20);

  // Immutable fields cannot be patched.
  const immutable = await app.inject({
    method: "PATCH",
    url: `/api/v1/products/${product.id}`,
    headers: auth(access),
    payload: { branch_id: await seedBranch("Other", "OTHER") },
  });
  assert.equal(immutable.statusCode, 409);
  assert.equal(immutable.json().error.code, "IMMUTABLE_FIELD");

  // Stock cannot be set through PATCH; use restock.
  const stockPatch = await app.inject({
    method: "PATCH",
    url: `/api/v1/products/${product.id}`,
    headers: auth(access),
    payload: { stock_quantity: 99 },
  });
  assert.equal(stockPatch.statusCode, 409);
  assert.equal(stockPatch.json().error.code, "USE_RESTOCK_ENDPOINT");

  const restock = await app.inject({
    method: "POST",
    url: `/api/v1/products/${product.id}/restock`,
    headers: auth(access),
    payload: { stock_quantity: 35 },
  });
  assert.equal(restock.statusCode, 200);
  assert.equal(restock.json().stock_quantity, 35);

  const removed = await app.inject({
    method: "DELETE",
    url: `/api/v1/products/${product.id}`,
    headers: auth(access),
  });
  assert.equal(removed.statusCode, 204);
  const { rows } = await pool.query(`SELECT deleted_at FROM product WHERE id = $1`, [product.id]);
  assert.ok(rows[0].deleted_at);
});

test("duplicate category names and SKUs are rejected per branch", async () => {
  const { app, branchId, access } = await setup();

  const cat = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/categories`,
    headers: auth(access),
    payload: { name: "Drinks" },
  });
  assert.equal(cat.statusCode, 201);

  const dupeCat = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/categories`,
    headers: auth(access),
    payload: { name: "drinks" },
  });
  assert.equal(dupeCat.statusCode, 409);
  assert.equal(dupeCat.json().error.code, "CATEGORY_NAME_TAKEN");

  const one = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers: auth(access),
    payload: { name: "Soda", sku: "SODA-1", price_cents: 8000 },
  });
  assert.equal(one.statusCode, 201);

  const dupeSku = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers: auth(access),
    payload: { name: "Other Soda", sku: "SODA-1", price_cents: 9000 },
  });
  assert.equal(dupeSku.statusCode, 409);
  assert.equal(dupeSku.json().error.code, "SKU_TAKEN");
});

test("a branch with an active device cannot be deleted", async () => {
  const { app, branchId, access } = await setup();
  await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/devices`,
    headers: auth(access),
    payload: { name: "Till 1" },
  });

  const del = await app.inject({
    method: "DELETE",
    url: `/api/v1/branches/${branchId}`,
    headers: auth(access),
  });
  assert.equal(del.statusCode, 409);
  assert.equal(del.json().error.code, "BRANCH_IN_USE");
});

test("a revoked device can be permanently deleted, an active one cannot", async () => {
  const { app, branchId, access } = await setup();
  const created = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/devices`,
    headers: auth(access),
    payload: { name: "Till 1" },
  });
  assert.equal(created.statusCode, 201);
  const deviceId = created.json().device_id as string;

  const activeDelete = await app.inject({
    method: "DELETE",
    url: `/api/v1/devices/${deviceId}/permanent`,
    headers: auth(access),
  });
  assert.equal(activeDelete.statusCode, 409);
  assert.equal(activeDelete.json().error.code, "DEVICE_ACTIVE");

  const revoke = await app.inject({
    method: "DELETE",
    url: `/api/v1/devices/${deviceId}`,
    headers: auth(access),
  });
  assert.equal(revoke.statusCode, 204);

  const deleted = await app.inject({
    method: "DELETE",
    url: `/api/v1/devices/${deviceId}/permanent`,
    headers: auth(access),
  });
  assert.equal(deleted.statusCode, 204);

  const { rows } = await pool.query(`SELECT 1 FROM device WHERE id = $1`, [deviceId]);
  assert.equal(rows.length, 0);

  const again = await app.inject({
    method: "DELETE",
    url: `/api/v1/devices/${deviceId}/permanent`,
    headers: auth(access),
  });
  assert.equal(again.statusCode, 404);
  assert.equal(again.json().error.code, "DEVICE_NOT_FOUND");
});

test("Idempotency-Key replays the stored response instead of re-running the write", async () => {
  const { app, branchId, access } = await setup();
  const payload = { name: "Mandazi", price_cents: 1000, stock_quantity: 5 };
  const headers = { ...auth(access), "idempotency-key": "create-mandazi-001" };

  const first = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers,
    payload,
  });
  assert.equal(first.statusCode, 201);
  assert.equal(first.headers["idempotent-replay"], undefined);

  const replay = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers,
    payload,
  });
  assert.equal(replay.statusCode, 201);
  assert.equal(replay.headers["idempotent-replay"], "true");
  assert.equal(replay.json().id, first.json().id);

  const { rows } = await pool.query<{ n: string }>(
    `SELECT COUNT(*) AS n FROM product WHERE branch_id = $1 AND name = 'Mandazi'`,
    [branchId],
  );
  assert.equal(Number(rows[0]!.n), 1);

  const differentBody = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/products`,
    headers,
    payload: { ...payload, name: "Kaimati" },
  });
  assert.equal(differentBody.statusCode, 422);
  assert.equal(differentBody.json().error.code, "IDEMPOTENCY_KEY_REUSED");
});

test("sales listing paginates with a keyset cursor", async () => {
  const { app, branchId, access } = await setup();
  for (let i = 0; i < 3; i += 1) {
    await pool.query(
      `INSERT INTO sale (id, receipt_number, branch_id, device_id, cashier_id, cashier_name, payment_method,
                         subtotal_cents, tax_amount_cents, total_amount_cents, amount_received_cents,
                         payment_status, created_at)
       VALUES (gen_random_uuid(), $1, $2, 'dev', gen_random_uuid(), 'Amina', 'CASH',
               100, 0, 100, 100, 'PAID', now() - ($3 || ' minutes')::interval)`,
      [`R-${i}`, branchId, String(i)],
    );
  }

  const first = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${branchId}/sales?limit=2`,
    headers: auth(access),
  });
  assert.equal(first.statusCode, 200);
  assert.equal(first.json().data.length, 2);
  assert.ok(first.json().next_cursor);

  const second = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${branchId}/sales?limit=2&cursor=${first.json().next_cursor}`,
    headers: auth(access),
  });
  assert.equal(second.statusCode, 200);
  assert.equal(second.json().data.length, 1);
  assert.equal(second.json().next_cursor, null);
});
