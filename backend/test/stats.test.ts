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

test("sales trend returns one zero-filled point per day with today's sales", async () => {
  const { app } = await makeApp();
  await seedUser({ email: "owner@example.com" });
  const { access } = await login(app);

  const branch = await app.inject({
    method: "POST",
    url: "/api/v1/branches",
    headers: { authorization: `Bearer ${access}` },
    payload: { name: "Westlands", code: "WST" },
  });
  const branchId = branch.json().id as string;

  const device = await provisionDevice(app, access, branchId);
  const product = productPayload(branchId, { stock_quantity: 20 });
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    products: [product],
  });
  const sale = salePayload(branchId, device.deviceId, product);
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    products: [],
    sales: [sale],
  });

  const trend = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${branchId}/reports/sales-trend?days=7`,
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(trend.statusCode, 200);
  const body = trend.json() as {
    days: number;
    branch_name: string;
    points: { date: string; sales_total_cents: number; sales_count: number }[];
  };
  assert.equal(body.days, 7);
  assert.equal(body.branch_name, "Westlands");
  assert.equal(body.points.length, 7);
  const today = body.points[body.points.length - 1]!;
  assert.equal(today.sales_count, 1);
  assert.equal(today.sales_total_cents, sale.total_amount_cents);
  assert.deepEqual(
    body.points.slice(0, 6).map((p) => p.sales_count),
    [0, 0, 0, 0, 0, 0],
  );
  assert.ok(new Date(today.date).getTime() >= new Date(body.points[0]!.date).getTime());

  const invalid = await app.inject({
    method: "GET",
    url: `/api/v1/branches/${branchId}/reports/sales-trend?days=0`,
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(invalid.statusCode, 400);
});

test("branch comparison ranks branches by revenue and honours branch scope", async () => {
  const { app } = await makeApp();
  await seedUser({ email: "owner@example.com" });
  const { access } = await login(app);

  const west = await app.inject({
    method: "POST",
    url: "/api/v1/branches",
    headers: { authorization: `Bearer ${access}` },
    payload: { name: "Westlands", code: "WST" },
  });
  const east = await app.inject({
    method: "POST",
    url: "/api/v1/branches",
    headers: { authorization: `Bearer ${access}` },
    payload: { name: "Eastleigh", code: "EST" },
  });
  const westId = west.json().id as string;
  const eastId = east.json().id as string;

  const device = await provisionDevice(app, access, westId);
  const product = productPayload(westId, { stock_quantity: 2 });
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: westId,
    products: [product],
  });
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: westId,
    sales: [salePayload(westId, device.deviceId, product)],
  });

  const owner = await app.inject({
    method: "GET",
    url: "/api/v1/reports/branch-comparison?days=7",
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(owner.statusCode, 200);
  const rows = (owner.json() as {
    branches: {
      branch_id: string;
      name: string;
      sales_total_cents: number;
      sales_count: number;
      low_stock_items: number;
      devices_count: number;
    }[];
  }).branches;
  assert.equal(rows.length, 2);
  assert.equal(rows[0]!.branch_id, westId);
  assert.equal(rows[0]!.sales_count, 1);
  assert.equal(rows[0]!.low_stock_items, 1);
  assert.equal(rows[0]!.devices_count, 1);
  assert.equal(rows[1]!.branch_id, eastId);
  assert.equal(rows[1]!.sales_total_cents, 0);

  await seedUser({ email: "manager@example.com", role: "MANAGER", branchIds: [eastId] });
  const manager = await login(app, "manager@example.com");
  const scoped = await app.inject({
    method: "GET",
    url: "/api/v1/reports/branch-comparison?days=7",
    headers: { authorization: `Bearer ${manager.access}` },
  });
  assert.equal(scoped.statusCode, 200);
  const scopedRows = (scoped.json() as { branches: { branch_id: string }[] }).branches;
  assert.deepEqual(scopedRows.map((r) => r.branch_id), [eastId]);
});
