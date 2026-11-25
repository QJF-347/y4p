import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";
import { RateLimiter } from "../src/lib/rate-limit.js";
import { expirePendingStk } from "../src/routes/payments.js";
import {
  closePool,
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

after(closePool);
beforeEach(resetDb);

const CALLBACK_SECRET = "test-callback-secret";

type App = Awaited<ReturnType<typeof makeApp>>["app"];

async function setup() {
  const ctx = await makeApp();
  const branchId = await seedBranch();
  await seedUser({ email: "owner@example.com" });
  const { access } = await login(ctx.app);
  const device = await provisionDevice(ctx.app, access, branchId);
  const product = productPayload(branchId);
  await pushBatch(ctx.app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    products: [product],
  });
  return { ...ctx, branchId, access, device, product };
}

async function pushMpesaSale(
  app: App,
  branchId: string,
  device: { deviceId: string; deviceToken: string },
  product: { id: string },
  over: Record<string, unknown> = {},
) {
  const sale = salePayload(branchId, device.deviceId, product, {
    payment_method: "MPESA",
    payment_status: "PENDING",
    amount_received_cents: 0,
    ...over,
  });
  const res = await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    sales: [sale],
  });
  assert.equal(res.statusCode, 200, res.body);
  return sale;
}

function stkPush(
  app: App,
  deviceToken: string,
  saleId: string,
  phoneNumber = "+254712345678",
) {
  return app.inject({
    method: "POST",
    url: "/api/v1/payments/stk-push",
    headers: { "x-device-token": deviceToken },
    payload: { sale_id: saleId, phone_number: phoneNumber },
  });
}

function callback(
  app: App,
  checkoutRequestId: string,
  opts: { resultCode?: number; receipt?: string | null; secret?: string | null } = {},
) {
  const resultCode = opts.resultCode ?? 0;
  const secure: Record<string, string> = {};
  if (opts.secret !== null) secure["x-callback-secret"] = opts.secret ?? CALLBACK_SECRET;
  return app.inject({
    method: "POST",
    url: "/api/v1/payments/daraja/callback",
    headers: secure,
    payload: {
      Body: {
        stkCallback: {
          MerchantRequestID: "MER-1",
          CheckoutRequestID: checkoutRequestId,
          ResultCode: resultCode,
          ResultDesc:
            resultCode === 0 ? "The service request is processed successfully." : "Request cancelled by user",
          ...(resultCode === 0 && opts.receipt !== null
            ? {
                CallbackMetadata: {
                  Item: [
                    { Name: "Amount", Value: 1000 },
                    { Name: "MpesaReceiptNumber", Value: opts.receipt ?? "QK0000001" },
                    { Name: "PhoneNumber", Value: 254712345678 },
                  ],
                },
              }
            : {}),
        },
      },
    },
  });
}

async function startAttempt(app: App, deviceToken: string, saleId: string, phoneNumber?: string) {
  const res = await stkPush(app, deviceToken, saleId, phoneNumber);
  assert.equal(res.statusCode, 202, res.body);
  return res.json() as {
    payment_transaction_id: string;
    checkout_request_id: string;
    sale_id: string;
    status: string;
    phone_number: string;
    expires_at: string;
    poll_after_ms: number | null;
  };
}

function poll(app: App, token: string, transactionId: string) {
  return app.inject({
    method: "GET",
    url: `/api/v1/payments/${transactionId}`,
    headers: { "x-device-token": token },
  });
}

function pull(app: App, token: string) {
  return app.inject({
    method: "GET",
    url: "/api/v1/sync/pull",
    headers: { "x-device-token": token },
  });
}

test("stk-push accepts local phone formats and normalizes them to E.164", async () => {
  const { app, branchId, device, product } = await setup();

  const forms: [string, string][] = [
    ["0712345678", "+254712345678"],
    ["254712345678", "+254712345678"],
    ["+254712345678", "+254712345678"],
  ];
  for (const [input, expected] of forms) {
    const sale = await pushMpesaSale(app, branchId, device, product);
    const attempt = await startAttempt(app, device.deviceToken, sale.id, input);
    assert.equal(attempt.status, "PENDING");
    assert.equal(attempt.phone_number, expected);
    assert.equal(attempt.poll_after_ms, 2_000);
    assert.ok(new Date(attempt.expires_at).getTime() > Date.now());

    const { rows } = await pool.query<{ phone_number: string }>(
      `SELECT phone_number FROM payment_transaction WHERE id = $1`,
      [attempt.payment_transaction_id],
    );
    assert.equal(rows[0]!.phone_number, expected);
  }
});

test("stk-push rejects phone numbers that cannot be a Kenyan MSISDN", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);

  const invalid = await stkPush(app, device.deviceToken, sale.id, "abcdefghij");
  assert.equal(invalid.statusCode, 422);
  assert.equal(invalid.json().error.code, "INVALID_MSISDN");

  const tooShort = await stkPush(app, device.deviceToken, sale.id, "0712");
  assert.equal(tooShort.statusCode, 400);
  assert.equal(tooShort.json().error.code, "VALIDATION_ERROR");
});

test("stk-push refuses a second attempt while one is in flight", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  const again = await stkPush(app, device.deviceToken, sale.id);
  assert.equal(again.statusCode, 409);
  assert.equal(again.json().error.code, "STK_IN_PROGRESS");
  assert.equal(again.json().error.details.payment_transaction_id, attempt.payment_transaction_id);
});

test("successful callback marks the sale PAID and devices see it after sync", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  const unknownSecret = await callback(app, attempt.checkout_request_id, { secret: null });
  assert.equal(unknownSecret.statusCode, 401);

  const cb = await callback(app, attempt.checkout_request_id, { receipt: "QK1234567" });
  assert.equal(cb.statusCode, 200, cb.body);
  assert.equal(cb.json().ResultCode, 0);

  const status = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  assert.equal(status.statusCode, 200, status.body);
  const body = status.json() as {
    status: string;
    mpesa_receipt_number: string;
    poll_after_ms: number | null;
    sale: { id: string; payment_status: string; mpesa_receipt: string };
  };
  assert.equal(body.status, "SUCCESS");
  assert.equal(body.mpesa_receipt_number, "QK1234567");
  assert.equal(body.poll_after_ms, null);
  assert.equal(body.sale.id, sale.id);
  assert.equal(body.sale.payment_status, "PAID");
  assert.equal(body.sale.mpesa_receipt, "QK1234567");

  const page = (await pull(app, device.deviceToken)).json() as {
    changes: { sales: { id: string; payment_status: string; mpesa_receipt: string | null }[] };
  };
  const synced = page.changes.sales.find((s) => s.id === sale.id);
  assert.ok(synced, "paid sale flows down to the device");
  assert.equal(synced!.payment_status, "PAID");
  assert.equal(synced!.mpesa_receipt, "QK1234567");
});

test("duplicate success callbacks are no-ops", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await callback(app, attempt.checkout_request_id, { receipt: "QK1234567" });
  const again = await callback(app, attempt.checkout_request_id, { receipt: "QK9999999" });
  assert.equal(again.statusCode, 200);

  const { rows } = await pool.query<{ payment_status: string; mpesa_receipt: string | null }>(
    `SELECT payment_status, mpesa_receipt FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "PAID");
  assert.equal(rows[0]!.mpesa_receipt, "QK1234567");
});

test("late success after failure is queued once for human review and can be applied", async () => {
  const { app, access, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await callback(app, attempt.checkout_request_id, { resultCode: 1032 });
  const failed = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  assert.equal(failed.json().status, "FAILED");

  await callback(app, attempt.checkout_request_id, { receipt: "QK7654321" });
  await callback(app, attempt.checkout_request_id, { receipt: "QK7654321" }); // redelivery

  const queued = await pool.query<{ id: string; resolved_at: Date | null }>(
    `SELECT id, resolved_at FROM reconciliation_queue WHERE payment_transaction_id = $1`,
    [attempt.payment_transaction_id],
  );
  assert.equal(queued.rowCount, 1, "redelivery must not duplicate the case");
  const itemId = queued.rows[0]!.id;

  const list = await app.inject({
    method: "GET",
    url: "/api/v1/reconciliation/payments?status=open",
    headers: { authorization: `Bearer ${access}` },
  });
  assert.equal(list.statusCode, 200, list.body);
  const listed = (list.json() as { items: { id: string }[] }).items;
  assert.ok(listed.some((i) => i.id === itemId));

  const resolve = await app.inject({
    method: "POST",
    url: `/api/v1/reconciliation/payments/${itemId}/resolve`,
    headers: { authorization: `Bearer ${access}` },
    payload: { action: "APPLY_TO_SALE", note: "confirmed on M-Pesa statement" },
  });
  assert.equal(resolve.statusCode, 200, resolve.body);
  assert.deepEqual(resolve.json(), { id: itemId, action: "APPLY_TO_SALE", resolved: true });

  const { rows } = await pool.query<{ payment_status: string; mpesa_receipt: string | null }>(
    `SELECT payment_status, mpesa_receipt FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "PAID");
  assert.equal(rows[0]!.mpesa_receipt, "QK7654321");

  const again = await app.inject({
    method: "POST",
    url: `/api/v1/reconciliation/payments/${itemId}/resolve`,
    headers: { authorization: `Bearer ${access}` },
    payload: { action: "DISCARD" },
  });
  assert.equal(again.statusCode, 409);
  assert.equal(again.json().error.code, "ALREADY_RESOLVED");
});

test("success without a receipt is queued and cannot be applied until discarded", async () => {
  const { app, access, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await callback(app, attempt.checkout_request_id, { receipt: null });

  const queued = await pool.query<{ id: string; mpesa_receipt_number: string | null }>(
    `SELECT id, mpesa_receipt_number FROM reconciliation_queue WHERE payment_transaction_id = $1`,
    [attempt.payment_transaction_id],
  );
  assert.equal(queued.rowCount, 1);
  const item = queued.rows[0]!;
  assert.equal(item.mpesa_receipt_number, null);

  const apply = await app.inject({
    method: "POST",
    url: `/api/v1/reconciliation/payments/${item.id}/resolve`,
    headers: { authorization: `Bearer ${access}` },
    payload: { action: "APPLY_TO_SALE" },
  });
  assert.equal(apply.statusCode, 409);
  assert.equal(apply.json().error.code, "NO_RECEIPT");

  const discard = await app.inject({
    method: "POST",
    url: `/api/v1/reconciliation/payments/${item.id}/resolve`,
    headers: { authorization: `Bearer ${access}` },
    payload: { action: "DISCARD" },
  });
  assert.equal(discard.statusCode, 200, discard.body);
});

test("the timeout sweeper fails stale attempts and their sales", async () => {
  const { app, deps, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await pool.query(
    `UPDATE payment_transaction SET created_at = now() - interval '10 minutes' WHERE id = $1`,
    [attempt.payment_transaction_id],
  );
  const expired = await expirePendingStk(deps);
  assert.equal(expired, 1);

  const status = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  const body = status.json() as { status: string; result_desc: string | null };
  assert.equal(body.status, "FAILED");
  assert.equal(body.result_desc, "STK_TIMEOUT");

  const { rows } = await pool.query<{ payment_status: string }>(
    `SELECT payment_status FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "FAILED");
});

test("stk-push rejects an amount_cents that does not match the sale total", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushMpesaSale(app, branchId, device, product);

  const res = await app.inject({
    method: "POST",
    url: "/api/v1/payments/stk-push",
    headers: { "x-device-token": device.deviceToken },
    payload: { sale_id: sale.id, phone_number: "+254712345678", amount_cents: 1 },
  });
  assert.equal(res.statusCode, 422);
  assert.equal(res.json().error.code, "AMOUNT_MISMATCH");
});

test("stk-push requires an MPESA sale in PENDING state", async () => {
  const { app, branchId, device, product } = await setup();
  const cashSale = salePayload(branchId, device.deviceId, product);
  await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    sales: [cashSale],
  });

  const res = await stkPush(app, device.deviceToken, cashSale.id);
  assert.equal(res.statusCode, 422);
  assert.equal(res.json().error.code, "NOT_MPESA_SALE");
});

// --- SPLIT tender: part cash, part M-Pesa -----------------------------------

const SPLIT_CASH = 40_000; // total is 100_000 cents, so the M-Pesa leg is 60_000

async function pushSplitSale(
  app: App,
  branchId: string,
  device: { deviceId: string; deviceToken: string },
  product: { id: string },
  cashCents = SPLIT_CASH,
) {
  const sale = salePayload(branchId, device.deviceId, product, {
    payment_method: "SPLIT",
    payment_status: "PENDING",
    amount_received_cents: cashCents,
    change_amount_cents: 0,
    cash_amount_cents: cashCents,
  });
  const res = await pushBatch(app, device.deviceToken, {
    device_id: device.deviceId,
    branch_id: branchId,
    sales: [sale],
  });
  assert.equal(res.statusCode, 200, res.body);
  const row = (res.json() as { results: { entity: string; id: string; status: string }[] }).results.find(
    (r) => r.entity === "sale" && r.id === sale.id,
  );
  assert.equal(row?.status, "SYNCED");
  return sale;
}

function stkPushAmount(app: App, deviceToken: string, saleId: string, amountCents: number) {
  return app.inject({
    method: "POST",
    url: "/api/v1/payments/stk-push",
    headers: { "x-device-token": deviceToken },
    payload: { sale_id: saleId, phone_number: "+254712345678", amount_cents: amountCents },
  });
}

test("split sale charges only the M-Pesa remainder", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushSplitSale(app, branchId, device, product);

  const res = await stkPushAmount(app, device.deviceToken, sale.id, 60_000);
  assert.equal(res.statusCode, 202, res.body);
  const body = res.json() as { status: string; amount_cents: number };
  assert.equal(body.status, "PENDING");
  assert.equal(body.amount_cents, 60_000);

  const { rows } = await pool.query<{ amount_cents: string }>(
    `SELECT amount_cents FROM payment_transaction WHERE sale_id = $1`,
    [sale.id],
  );
  assert.equal(Number(rows[0]!.amount_cents), 60_000);

  const { rows: saleRows } = await pool.query<{ payment_status: string; cash_amount_cents: string }>(
    `SELECT payment_status, cash_amount_cents FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(saleRows[0]!.payment_status, "PENDING");
  assert.equal(Number(saleRows[0]!.cash_amount_cents), SPLIT_CASH);
});

test("split stk-push compares amount_cents against the remainder", async () => {
  const { app, branchId, device, product } = await setup();
  const sale = await pushSplitSale(app, branchId, device, product);

  const wrong = await stkPushAmount(app, device.deviceToken, sale.id, 100_000);
  assert.equal(wrong.statusCode, 422);
  assert.equal(wrong.json().error.code, "AMOUNT_MISMATCH");
  assert.equal(wrong.json().error.details.expected_cents, 60_000);
});

test("split remainder must be whole shillings and at least one", async () => {
  const { app, branchId, device, product } = await setup();

  const fractional = await pushSplitSale(app, branchId, device, product);
  await pool.query(`UPDATE sale SET cash_amount_cents = 40050 WHERE id = $1`, [fractional.id]);
  const res = await stkPush(app, device.deviceToken, fractional.id);
  assert.equal(res.statusCode, 422);
  assert.equal(res.json().error.code, "AMOUNT_NOT_WHOLE_KES");

  const tiny = await pushSplitSale(app, branchId, device, product);
  await pool.query(`UPDATE sale SET cash_amount_cents = 99901 WHERE id = $1`, [tiny.id]);
  const small = await stkPush(app, device.deviceToken, tiny.id);
  assert.equal(small.statusCode, 422);
  assert.equal(small.json().error.code, "AMOUNT_TOO_SMALL");
});

test("a failed split callback leaves the sale PENDING and allows a retry", async () => {
  const { app, deps, branchId, device, product } = await setup();
  const sale = await pushSplitSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await callback(app, attempt.checkout_request_id, { resultCode: 1032 });

  const failed = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  assert.equal(failed.json().status, "FAILED");
  const afterFailure = await pool.query<{ payment_status: string }>(
    `SELECT payment_status FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(afterFailure.rows[0]!.payment_status, "PENDING");

  // Move the per-sale rate limiter past its 3 s window without sleeping.
  let now = Date.now();
  deps.limiter = new RateLimiter(() => now);
  now += 4_000;

  const retry = await startAttempt(app, device.deviceToken, sale.id);
  await callback(app, retry.checkout_request_id, { receipt: "QKSPLIT01" });

  const { rows } = await pool.query<{ payment_status: string; mpesa_receipt: string | null; cash_amount_cents: string }>(
    `SELECT payment_status, mpesa_receipt, cash_amount_cents FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "PAID");
  assert.equal(rows[0]!.mpesa_receipt, "QKSPLIT01");
  assert.equal(Number(rows[0]!.cash_amount_cents), SPLIT_CASH);
});

test("the timeout sweeper leaves a SPLIT sale PENDING", async () => {
  const { app, deps, branchId, device, product } = await setup();
  const sale = await pushSplitSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);

  await pool.query(
    `UPDATE payment_transaction SET created_at = now() - interval '10 minutes' WHERE id = $1`,
    [attempt.payment_transaction_id],
  );
  const expired = await expirePendingStk(deps);
  assert.equal(expired, 1);

  const status = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  assert.equal(status.json().status, "FAILED");
  const { rows } = await pool.query<{ payment_status: string }>(
    `SELECT payment_status FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "PENDING");
});

test("a receipt collision leaves a SPLIT sale PENDING for reconciliation", async () => {
  const { app, branchId, device, product } = await setup();

  // Claim the receipt on an unrelated, fully paid M-Pesa sale first.
  const other = await pushMpesaSale(app, branchId, device, product);
  const otherAttempt = await startAttempt(app, device.deviceToken, other.id);
  await callback(app, otherAttempt.checkout_request_id, { receipt: "QKCOLLIDE" });

  const sale = await pushSplitSale(app, branchId, device, product);
  const attempt = await startAttempt(app, device.deviceToken, sale.id);
  await callback(app, attempt.checkout_request_id, { receipt: "QKCOLLIDE" });

  const failed = await poll(app, device.deviceToken, attempt.payment_transaction_id);
  assert.equal(failed.json().status, "FAILED");
  const { rows } = await pool.query<{ payment_status: string }>(
    `SELECT payment_status FROM sale WHERE id = $1`,
    [sale.id],
  );
  assert.equal(rows[0]!.payment_status, "PENDING");

  const queued = await pool.query(
    `SELECT 1 FROM reconciliation_queue WHERE payment_transaction_id = $1`,
    [attempt.payment_transaction_id],
  );
  assert.equal(queued.rowCount, 1);
});
