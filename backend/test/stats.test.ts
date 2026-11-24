import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";
import {
  closePool,
  login,
  makeApp,
  productPayload,
  provisionDevice,
  pushBatch,
  resetDb,
  salePayload,
  seedUser,
} from "./helpers.js";

after(closePool);
beforeEach(resetDb);

test("branch stats and dashboard summary reflect today's paid sales", async () => {
  const { app } = await makeApp();
  await seedUser({ email: "owner@example.com" });
  const { access } = await login(app);

  const branch = await app.inject({
    method: "POST",
    url: "/api/v1/branches",
    headers: { authorization: `Bearer ${access}` },
    payload: { name: "Westlands", code: "WST" },
  });
  assert.equal(branch.statusCode, 201);
  const branchId = branch.json().id as string;

  const device = await provisionDevice(app, access, branchId);
  const product = productPayload(branchId, { stock_quantity: 3 });
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    products: [product],
  });
  const sale = salePayload(branchId, device.deviceId, product);
  const push = await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    sales: [sale],
  });
  const salesResults = (push.json() as { results: { entity: string; id: string; status: string }[] }).results;
  const saleResult = salesResults.find((r) => r.entity === "sale" && r.id === sale.id);
  assert.equal(saleResult?.status, "SYNCED");

  const today = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${branchId}/stats/today`,
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(today.statusCode, 200);
  const stats = today.json();
  assert.equal(stats.sales_count, 1);
  assert.equal(stats.sales_total_cents, sale.total_amount_cents);
  assert.equal(stats.low_stock_count, 1); // 3 in stock minus 2 sold leaves 1, under the threshold
  assert.equal(stats.devices_count, 1);
  assert.equal(stats.top_sellers[0].product_id, product.id);
  assert.equal(stats.top_sellers[0].quantity_sold, 2);

  const summary = await app.inject({
    method: "GET",
    url: "/api/v1/dashboard/summary",
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(summary.statusCode, 200);
  assert.equal(summary.json().branches_count, 1);
  assert.equal(summary.json().active_devices, 1);
  assert.equal(summary.json().sales_today_count, 1);
  assert.equal(summary.json().pending_payments, 0);
  assert.equal(summary.json().low_stock_items, 1);
});
