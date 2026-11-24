import { z } from "zod";
import { parse } from "../app.js";
import { writeAudit } from "../lib/audit.js";
import { conflict, notFound } from "../lib/errors.js";
import { toIso } from "../lib/time.js";
const uuid = z.string().uuid();
const listQuery = z.object({
    status: z.enum(["open", "resolved", "all"]).default("open"),
    limit: z.coerce.number().int().min(1).max(200).default(50),
});
const resolveBody = z.object({
    action: z.enum(["APPLY_TO_SALE", "MARK_REFUND", "DISCARD"]),
    note: z.string().trim().max(500).optional(),
});
function encodeCursor(row) {
    return Buffer.from(JSON.stringify({ d: row.detected_at.toISOString(), i: row.id })).toString("base64url");
}
function decodeCursor(raw) {
    const parsed = JSON.parse(Buffer.from(raw, "base64url").toString("utf8"));
    return { d: new Date(parsed.d), i: parsed.i };
}
export function registerReconciliationRoutes(app, deps, guards) {
    const { db } = deps;
    app.get("/reconciliation/payments", async (req) => {
        await guards.requireOwner(req);
        const q = listQuery.extend({ cursor: z.string().optional() }).parse(req.query ?? {});
        const params = [];
        const where = [];
        if (q.status === "open")
            where.push(`rq.resolved_at IS NULL`);
        if (q.status === "resolved")
            where.push(`rq.resolved_at IS NOT NULL`);
        if (q.cursor) {
            const c = decodeCursor(q.cursor);
            params.push(c.d, c.i);
            where.push(`(rq.detected_at, rq.id) < ($1::timestamptz, $2::uuid)`);
        }
        params.push(q.limit + 1);
        const { rows } = await db.query(`SELECT rq.id, rq.payment_transaction_id, pt.sale_id, s.receipt_number,
              rq.checkout_request_id, rq.mpesa_receipt_number, rq.detected_at,
              rq.resolved_at, rq.resolved_by, rq.action
         FROM reconciliation_queue rq
         JOIN payment_transaction pt ON pt.id = rq.payment_transaction_id
         JOIN sale s ON s.id = pt.sale_id
        ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
        ORDER BY rq.detected_at DESC, rq.id DESC
        LIMIT $${params.length}`, params);
        const more = rows.length > q.limit;
        const items = rows.slice(0, q.limit);
        return {
            items: items.map((r) => ({
                id: r.id,
                payment_transaction_id: r.payment_transaction_id,
                sale_id: r.sale_id,
                receipt_number: r.receipt_number,
                checkout_request_id: r.checkout_request_id,
                mpesa_receipt_number: r.mpesa_receipt_number,
                detected_at: toIso(r.detected_at),
                resolved_at: toIso(r.resolved_at),
                resolved_by: r.resolved_by,
                action: r.action,
            })),
            next_cursor: more ? encodeCursor(items[items.length - 1]) : null,
        };
    });
    app.post("/reconciliation/payments/:id/resolve", async (req) => {
        const claims = await guards.requireOwner(req);
        const id = uuid.parse(req.params.id);
        const body = parse(resolveBody, req.body);
        return db.tx(async (q) => {
            const { rows } = await q.query(`SELECT rq.id, rq.payment_transaction_id, rq.mpesa_receipt_number, rq.resolved_at,
                pt.sale_id, pt.status AS pt_status, s.payment_status
           FROM reconciliation_queue rq
           JOIN payment_transaction pt ON pt.id = rq.payment_transaction_id
           JOIN sale s ON s.id = pt.sale_id
          WHERE rq.id = $1 FOR UPDATE`, [id]);
            const row = rows[0];
            if (!row)
                throw notFound("RECONCILIATION_NOT_FOUND", "No such reconciliation item");
            if (row.resolved_at)
                throw conflict("ALREADY_RESOLVED", "This item was already resolved");
            if (body.action === "APPLY_TO_SALE") {
                const receipt = row.mpesa_receipt_number;
                if (!receipt)
                    throw conflict("NO_RECEIPT", "This item has no M-Pesa receipt to apply");
                try {
                    await q.query(`UPDATE payment_transaction
                SET status = 'SUCCESS', mpesa_receipt_number = COALESCE(mpesa_receipt_number, $2),
                    result_desc = 'RECONCILED', updated_at = now()
              WHERE id = $1`, [row.payment_transaction_id, receipt]);
                }
                catch (err) {
                    if (err.code === "23505") {
                        throw conflict("RECEIPT_IN_USE", "That M-Pesa receipt is already attached to another payment");
                    }
                    throw err;
                }
                await q.query(`UPDATE sale SET payment_status = 'PAID', mpesa_receipt = COALESCE(mpesa_receipt, $2),
                  payment_updated_at = now(), server_version = nextval('server_version_seq')
            WHERE id = $1`, [row.sale_id, receipt]);
            }
            else if (body.action === "MARK_REFUND") {
                await q.query(`UPDATE payment_transaction SET status = 'FAILED', result_desc = 'MARKED_REFUND', updated_at = now()
            WHERE id = $1 AND status <> 'SUCCESS'`, [row.payment_transaction_id]);
                await q.query(`UPDATE sale SET payment_status = 'FAILED', payment_updated_at = now(),
                  server_version = nextval('server_version_seq')
            WHERE id = $1 AND payment_status <> 'PAID'`, [row.sale_id]);
            }
            // DISCARD leaves the transaction/sale untouched; it only closes the queue item.
            await q.query(`UPDATE reconciliation_queue SET resolved_at = now(), resolved_by = $2, action = $3 WHERE id = $1`, [id, claims.sub, body.action]);
            await writeAudit(q, {
                actorType: "USER",
                actorId: claims.sub,
                action: `reconciliation.${body.action.toLowerCase()}`,
                entity: "reconciliation_queue",
                entityId: id,
                details: {
                    payment_transaction_id: row.payment_transaction_id,
                    sale_id: row.sale_id,
                    note: body.note ?? null,
                },
                ip: req.ip,
            });
            return { id, action: body.action, resolved: true };
        });
    });
}
//# sourceMappingURL=reconciliation.js.map