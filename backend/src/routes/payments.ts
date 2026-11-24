import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { parse, type Deps, type Guards } from "../app.js";
import { writeAudit } from "../lib/audit.js";
import { conflict, forbidden, notFound, tooManyRequests, unauthorized, unprocessable } from "../lib/errors.js";
import type { Queryable } from "../lib/db.js";
import { newId } from "../lib/ids.js";
import { toIso } from "../lib/time.js";

const uuid = z.string().uuid();

/** API_CONTRACT §5: accept 07XX…, 2547XX…, +2547XX… and store E.164. */
function normalizeMsisdn(raw: string): string | null {
  const digits = raw.replace(/[\s()-]/g, "");
  const match = /^(?:\+?254|0)([17]\d{8})$/.exec(digits);
  return match ? `+254${match[1]}` : null;
}

const stkPushBody = z.object({
  sale_id: uuid,
  /** Accepted as 07XX…, 2547XX…, +2547XX…; normalized to E.164 before use. */
  phone_number: z.string().trim().min(9).max(20),
  /** Optional guard: when sent it must equal the sale total. */
  amount_cents: z.number().int().min(1).optional(),
});

const callbackBody = z.object({
  Body: z.object({
    stkCallback: z.object({
      MerchantRequestID: z.string().optional(),
      CheckoutRequestID: z.string().min(1),
      ResultCode: z.number().int(),
      ResultDesc: z.string().optional(),
      CallbackMetadata: z
        .object({ Item: z.array(z.object({ Name: z.string(), Value: z.union([z.string(), z.number(), z.null()]).optional() })) })
        .optional(),
    }),
  }),
});

function callbackItem(
  items: { Name: string; Value?: string | number | null }[] | undefined,
  name: string,
): string | null {
  const item = items?.find((i) => i.Name === name);
  if (!item || item.Value === undefined || item.Value === null) return null;
  return String(item.Value);
}

/** Bump a sale's version so `GET /sync/pull` surfaces the payment status change. */
async function setSalePayment(
  q: Queryable,
  saleId: string,
  status: "PAID" | "FAILED",
  mpesaReceipt: string | null,
): Promise<void> {
  await q.query(
    `UPDATE sale SET payment_status = $2, mpesa_receipt = COALESCE(mpesa_receipt, $3),
            payment_updated_at = now(), server_version = nextval('server_version_seq')
      WHERE id = $1 AND payment_status = 'PENDING'`,
    [saleId, status, mpesaReceipt],
  );
}

/**
 * Fail PENDING STK attempts older than the configured timeout, then fail any
 * sale still waiting on them. Returns the number of transactions expired.
 */
export async function expirePendingStk(deps: Deps): Promise<number> {
  return deps.db.tx(async (q) => {
    const { rows } = await q.query<{ id: string; sale_id: string }>(
      `UPDATE payment_transaction SET status = 'FAILED', result_desc = 'STK_TIMEOUT', updated_at = now()
        WHERE status = 'PENDING'
          AND created_at < now() - make_interval(mins => $1)
        RETURNING id, sale_id`,
      [deps.config.STK_TIMEOUT_MINUTES],
    );
    for (const row of rows) {
      await setSalePayment(q, row.sale_id, "FAILED", null);
      await writeAudit(q, {
        actorType: "SYSTEM",
        actorId: "stk-timeout",
        action: "payments.stk_timeout",
        entity: "payment_transaction",
        entityId: row.id,
        details: { sale_id: row.sale_id },
      });
    }
    return rows.length;
  });
}

export function registerPaymentRoutes(app: FastifyInstance, deps: Deps, guards: Guards): void {
  const { db, config } = deps;

  app.post("/payments/stk-push", async (req, reply) => {
    const device = await guards.requireDevice(req);
    const body = parse(stkPushBody, req.body);
    const clock = deps.clock();

    const { rows } = await db.query<{
      id: string;
      branch_id: string;
      payment_method: string;
      payment_status: string;
      total_amount_cents: string;
      receipt_number: string;
    }>(
      `SELECT id, branch_id, payment_method, payment_status, total_amount_cents, receipt_number
         FROM sale WHERE id = $1`,
      [body.sale_id],
    );
    const sale = rows[0];
    if (!sale) throw notFound("SALE_NOT_FOUND", "Sale does not exist");
    if (sale.branch_id !== device.branchId) {
      throw forbidden("BRANCH_SCOPE_MISMATCH", "This sale belongs to a different branch");
    }
    if (sale.payment_method !== "MPESA") {
      throw unprocessable("NOT_MPESA_SALE", "STK push only applies to MPESA sales");
    }
    if (sale.payment_status !== "PENDING") {
      throw conflict("SALE_ALREADY_FINAL", `This sale is already ${sale.payment_status}`);
    }
    if (body.amount_cents !== undefined && body.amount_cents !== Number(sale.total_amount_cents)) {
      throw unprocessable("AMOUNT_MISMATCH", "amount_cents does not match the sale total", {
        expected_cents: Number(sale.total_amount_cents),
      });
    }
    if (Number(sale.total_amount_cents) < 100) {
      throw unprocessable("AMOUNT_TOO_SMALL", "M-Pesa requires at least 1 whole shilling");
    }

    // A still-live attempt blocks a second one (partial unique index backs this
    // up). Checked before the limiter so a double-tap reports the in-flight
    // attempt instead of a generic rate-limit error.
    const pending = await db.query<{ id: string; created_at: Date }>(
      `SELECT id, created_at FROM payment_transaction
        WHERE sale_id = $1 AND status = 'PENDING'
          AND created_at >= now() - make_interval(mins => $2)`,
      [sale.id, config.STK_TIMEOUT_MINUTES],
    );
    const live = pending.rows[0];
    if (live) {
      const expiresAt = new Date(live.created_at.getTime() + config.STK_TIMEOUT_MINUTES * 60_000);
      throw conflict("STK_IN_PROGRESS", "An M-Pesa prompt is already in flight for this sale", {
        payment_transaction_id: live.id,
        expires_at: expiresAt.toISOString(),
        poll_after_ms: 2_000,
      });
    }

    // API_CONTRACT §4.1: 1 attempt per 3 s per sale, 10 per minute per device.
    if (!deps.limiter.hit(`stk:sale:${sale.id}`, 1, 3_000)) {
      throw tooManyRequests("STK_RATE_LIMITED", "Wait a moment before retrying this sale", {
        retry_after_ms: 3_000,
      });
    }
    if (!deps.limiter.hit(`stk:device:${device.id}`, 10, 60_000)) {
      throw tooManyRequests("STK_RATE_LIMITED", "Too many STK pushes from this device", {
        retry_after_ms: 60_000,
      });
    }

    const phone = normalizeMsisdn(body.phone_number);
    if (!phone) {
      throw unprocessable("INVALID_MSISDN", "phone_number must be a Kenyan mobile number", {
        accepted_formats: ["07XXXXXXXX", "2547XXXXXXXX", "+2547XXXXXXXX"],
      });
    }

    const amountKes = Math.round(Number(sale.total_amount_cents) / 100);
    const result = await deps.daraja.stkPush({
      phone,
      amountKes,
      accountReference: sale.receipt_number,
      transactionDesc: `Orange POS ${sale.receipt_number}`,
    });

    const id = newId();
    try {
      await db.query(
        `INSERT INTO payment_transaction (id, sale_id, checkout_request_id, merchant_request_id,
                                         amount_cents, phone_number, status, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, 'PENDING', $7, $7)`,
        [id, sale.id, result.checkoutRequestId, result.merchantRequestId, sale.total_amount_cents, phone, clock],
      );
    } catch (err) {
      if ((err as { constraint?: string }).constraint === "pt_one_pending_per_sale") {
        throw conflict("STK_IN_PROGRESS", "An M-Pesa prompt is already in flight for this sale", {
          poll_after_ms: 2_000,
        });
      }
      throw err;
    }
    await writeAudit(db, {
      actorType: "DEVICE",
      actorId: device.id,
      action: "payments.stk_push",
      entity: "payment_transaction",
      entityId: id,
      details: { sale_id: sale.id, checkout_request_id: result.checkoutRequestId, amount_cents: Number(sale.total_amount_cents) },
      ip: req.ip,
    });

    reply.status(202).send({
      payment_transaction_id: id,
      checkout_request_id: result.checkoutRequestId,
      merchant_request_id: result.merchantRequestId,
      status: "PENDING",
      amount_cents: Number(sale.total_amount_cents),
      phone_number: phone,
      customer_message: result.customerMessage,
      expires_at: new Date(clock.getTime() + config.STK_TIMEOUT_MINUTES * 60_000).toISOString(),
      poll_after_ms: 2_000,
    });
  });

  // Public endpoint; Daraja cannot set custom headers, so it authenticates with
  // the secret embedded in DARAJA_CALLBACK_URL. Always 200 once authenticated.
  app.post("/payments/daraja/callback", async (req, reply) => {
    const secretHeader = req.headers["x-callback-secret"];
    const query = (req.query ?? {}) as { secret?: string };
    const presented =
      typeof secretHeader === "string" ? secretHeader : typeof query.secret === "string" ? query.secret : null;
    if (presented !== config.DARAJA_CALLBACK_SECRET) {
      throw unauthorized("CALLBACK_UNAUTHORIZED", "Unknown callback secret");
    }

    // Daraja treats anything other than 200 as "retry"; a malformed payload is
    // not worth retrying, so record it and acknowledge.
    const parsed = callbackBody.safeParse(req.body);
    if (!parsed.success) {
      await db
        .query(`INSERT INTO daraja_callback (body) VALUES ($1)`, [JSON.stringify(req.body ?? null)])
        .catch(() => undefined);
      req.log.warn({ issues: parsed.error.issues }, "malformed daraja callback");
      return { ResultCode: 0, ResultDesc: "Ignored" };
    }
    const cb = parsed.data.Body.stkCallback;
    const { rows: logRows } = await db.query<{ id: string }>(
      `INSERT INTO daraja_callback (checkout_request_id, merchant_request_id, body)
       VALUES ($1, $2, $3) RETURNING id`,
      [cb.CheckoutRequestID, cb.MerchantRequestID ?? null, JSON.stringify(req.body)],
    );
    const payloadRef = `daraja_callback:${logRows[0]!.id}`;

    const items = cb.CallbackMetadata?.Item;
    const success = cb.ResultCode === 0;
    const receipt = callbackItem(items, "MpesaReceiptNumber");

    try {
      await db.tx(async (q) => {
        const { rows } = await q.query<{
          id: string;
          sale_id: string;
          status: string;
          mpesa_receipt_number: string | null;
        }>(
          `SELECT id, sale_id, status, mpesa_receipt_number
             FROM payment_transaction WHERE checkout_request_id = $1 FOR UPDATE`,
          [cb.CheckoutRequestID],
        );
        const pt = rows[0];
        if (!pt) return; // Unknown attempt — keep the raw callback and move on.

        if (pt.status === "SUCCESS") return; // Duplicate success: no-op.
        if (pt.status === "FAILED") {
          if (success) {
            // Late success after the attempt was failed/timed out: never resurrect
            // automatically — a human must decide (API_CONTRACT §4.4).
            await q.query(
              `INSERT INTO reconciliation_queue
                 (id, payment_transaction_id, checkout_request_id, mpesa_receipt_number, raw_payload_ref)
               VALUES ($1, $2, $3, $4, $5)
             ON CONFLICT (payment_transaction_id) DO NOTHING`,
              [newId(), pt.id, cb.CheckoutRequestID, receipt, payloadRef],
            );
          }
          return;
        }

        if (!success) {
          await q.query(
            `UPDATE payment_transaction SET status = 'FAILED', result_code = $2, result_desc = $3,
                    raw_payload_ref = $4, updated_at = now() WHERE id = $1`,
            [pt.id, cb.ResultCode, cb.ResultDesc ?? null, payloadRef],
          );
          await setSalePayment(q, pt.sale_id, "FAILED", null);
          return;
        }

        if (!receipt) {
          // A success without a receipt cannot be applied; flag it.
          await q.query(
            `INSERT INTO reconciliation_queue
               (id, payment_transaction_id, checkout_request_id, mpesa_receipt_number, raw_payload_ref)
             VALUES ($1, $2, $3, NULL, $4)
             ON CONFLICT (payment_transaction_id) DO NOTHING`,
            [newId(), pt.id, cb.CheckoutRequestID, payloadRef],
          );
          return;
        }

        // The receipt unique index can fire here; a savepoint keeps the outer
        // transaction usable so the failure can be recorded in the same tx.
        await q.query(`SAVEPOINT claim_receipt`);
        try {
          await q.query(
            `UPDATE payment_transaction SET status = 'SUCCESS', mpesa_receipt_number = $2,
                    result_code = $3, result_desc = $4, raw_payload_ref = $5, updated_at = now()
              WHERE id = $1`,
            [pt.id, receipt, cb.ResultCode, cb.ResultDesc ?? null, payloadRef],
          );
          await q.query(`RELEASE SAVEPOINT claim_receipt`);
        } catch (err) {
          if ((err as { code?: string }).code === "23505") {
            await q.query(`ROLLBACK TO SAVEPOINT claim_receipt`);
            await q.query(`RELEASE SAVEPOINT claim_receipt`);
            // Receipt already claimed by another transaction: a human must reconcile.
            await q.query(
              `UPDATE payment_transaction SET status = 'FAILED',
                      result_desc = 'RECEIPT_ALREADY_CLAIMED', raw_payload_ref = $2, updated_at = now()
                WHERE id = $1`,
              [pt.id, payloadRef],
            );
            await setSalePayment(q, pt.sale_id, "FAILED", null);
            await q.query(
              `INSERT INTO reconciliation_queue
                 (id, payment_transaction_id, checkout_request_id, mpesa_receipt_number, raw_payload_ref)
               VALUES ($1, $2, $3, $4, $5)
             ON CONFLICT (payment_transaction_id) DO NOTHING`,
              [newId(), pt.id, cb.CheckoutRequestID, receipt, payloadRef],
            );
            return;
          }
          throw err;
        }
        await setSalePayment(q, pt.sale_id, "PAID", receipt);
      });
    } catch (err) {
      // Daraja retries non-200s; we always acknowledge and rely on the audit log.
      req.log.error({ err }, "daraja callback processing failed");
    }
    return { ResultCode: 0, ResultDesc: "Accepted" };
  });

  app.get("/payments/:payment_transaction_id", async (req) => {
    const device = await guards.requireDevice(req);
    const id = uuid.parse((req.params as { payment_transaction_id: string }).payment_transaction_id);
    const { rows } = await db.query<{
      id: string;
      sale_id: string;
      branch_id: string;
      status: string;
      amount_cents: string;
      phone_number: string;
      mpesa_receipt_number: string | null;
      result_code: number | null;
      result_desc: string | null;
      created_at: Date;
      payment_status: string;
      sale_receipt: string | null;
    }>(
      `SELECT pt.id, pt.sale_id, s.branch_id, pt.status, pt.amount_cents, pt.phone_number,
              pt.mpesa_receipt_number, pt.result_code, pt.result_desc, pt.created_at,
              s.payment_status, s.mpesa_receipt AS sale_receipt
         FROM payment_transaction pt JOIN sale s ON s.id = pt.sale_id
        WHERE pt.id = $1`,
      [id],
    );
    const pt = rows[0];
    if (!pt) throw notFound("PAYMENT_NOT_FOUND", "Payment transaction does not exist");
    if (pt.branch_id !== device.branchId) {
      throw forbidden("BRANCH_SCOPE_MISMATCH", "This payment belongs to a different branch");
    }
    const expired = pt.status === "PENDING" &&
      pt.created_at.getTime() + config.STK_TIMEOUT_MINUTES * 60_000 <= deps.clock().getTime();
    return {
      payment_transaction_id: pt.id,
      status: pt.status,
      amount_cents: Number(pt.amount_cents),
      phone_number: pt.phone_number,
      mpesa_receipt_number: pt.mpesa_receipt_number ?? pt.sale_receipt,
      result_code: pt.result_code,
      result_desc: pt.result_desc,
      sale: {
        id: pt.sale_id,
        payment_status: pt.payment_status,
        mpesa_receipt: pt.sale_receipt,
      },
      created_at: toIso(pt.created_at),
      expires_at: toIso(new Date(pt.created_at.getTime() + config.STK_TIMEOUT_MINUTES * 60_000)),
      timed_out: expired,
      poll_after_ms: pt.status === "PENDING" ? 2_000 : null,
    };
  });
}
