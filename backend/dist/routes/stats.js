import { z } from "zod";
import { forbidden, notFound } from "../lib/errors.js";
/** Matches the admin app's "low stock" badge threshold. */
export const LOW_STOCK_THRESHOLD = 5;
const uuid = z.string().uuid();
function scopeBranch(claims, branchId) {
    if (claims.role === "OWNER")
        return;
    if (!claims.branch_ids.includes(branchId)) {
        throw forbidden("BRANCH_FORBIDDEN", "Your account does not have access to this branch");
    }
}
const DAY_MS = 86_400_000;
/** UTC day boundaries for "today" — matching the report timezone the CLI apps use. */
function dayBounds(now) {
    const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    return { from, to: new Date(from.getTime() + DAY_MS) };
}
async function branchToday(deps, branchId) {
    const { from, to } = dayBounds(deps.clock());
    const { rows } = await deps.db.query(`SELECT COALESCE(SUM(total_amount_cents) FILTER (WHERE payment_status = 'PAID'), 0) AS sales_total_cents,
            COUNT(*) FILTER (WHERE payment_status = 'PAID') AS sales_count
       FROM sale
      WHERE branch_id = $1 AND created_at >= $2 AND created_at < $3`, [branchId, from, to]);
    const { rows: lowRows } = await deps.db.query(`SELECT COUNT(*) AS low_stock_count FROM product
      WHERE branch_id = $1 AND deleted_at IS NULL AND is_active
        AND stock_quantity <= $2`, [branchId, LOW_STOCK_THRESHOLD]);
    const { rows: deviceRows } = await deps.db.query(`SELECT COUNT(*) AS devices_count FROM device WHERE branch_id = $1 AND NOT is_revoked`, [branchId]);
    const { rows: topRows } = await deps.db.query(`SELECT si.product_id, COALESCE(MIN(p.name), MIN(si.product_name)) AS name,
            SUM(si.quantity) AS quantity_sold, SUM(si.line_total_cents) AS revenue_cents
       FROM sale_item si
       JOIN sale s ON s.id = si.sale_id
       LEFT JOIN product p ON p.id = si.product_id
      WHERE s.branch_id = $1 AND s.created_at >= $2 AND s.created_at < $3
      GROUP BY si.product_id
      ORDER BY quantity_sold DESC
      LIMIT 5`, [branchId, from, to]);
    const summary = rows[0];
    return {
        sales_total_cents: Number(summary.sales_total_cents),
        sales_count: Number(summary.sales_count),
        low_stock_count: Number(lowRows[0].low_stock_count),
        devices_count: Number(deviceRows[0].devices_count),
        top_sellers: topRows.map((r) => ({
            product_id: r.product_id,
            name: r.name,
            quantity_sold: Number(r.quantity_sold),
            revenue_cents: Number(r.revenue_cents),
        })),
    };
}
export function registerStatsRoutes(app, deps, guards) {
    const { db } = deps;
    app.get("/branches/:id/stats/today", async (req) => {
        const claims = await guards.requireAdmin(req);
        const branchId = uuid.parse(req.params.id);
        scopeBranch(claims, branchId);
        const branch = await db.query(`SELECT 1 FROM branch WHERE id = $1 AND deleted_at IS NULL`, [branchId]);
        if (!branch.rowCount)
            throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
        return branchToday(deps, branchId);
    });
    app.get("/dashboard/summary", async (req) => {
        const claims = await guards.requireAdmin(req);
        const { from, to } = dayBounds(deps.clock());
        const branchIds = claims.role === "OWNER" ? null : claims.branch_ids;
        // For OWNER, branchIds is null and the scope predicate matches everything.
        const { rows } = await db.query(`SELECT
         (SELECT COUNT(*) FROM branch WHERE deleted_at IS NULL
            AND ($1::uuid[] IS NULL OR id = ANY($1::uuid[]))) AS branches_count,
         (SELECT COUNT(*) FROM device WHERE NOT is_revoked
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS active_devices,
         (SELECT COALESCE(SUM(total_amount_cents) FILTER (WHERE payment_status = 'PAID'), 0)
            FROM sale WHERE created_at >= $2 AND created_at < $3
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS sales_today_cents,
         (SELECT COUNT(*) FILTER (WHERE payment_status = 'PAID')
            FROM sale WHERE created_at >= $2 AND created_at < $3
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS sales_today_count,
         (SELECT COUNT(*) FROM sale WHERE payment_status = 'PENDING'
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS pending_payments,
         (SELECT COUNT(*) FROM product WHERE deleted_at IS NULL AND is_active AND stock_quantity <= $4
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS low_stock_items`, [branchIds, from, to, LOW_STOCK_THRESHOLD]);
        const r = rows[0];
        return {
            branches_count: Number(r.branches_count),
            active_devices: Number(r.active_devices),
            sales_today_cents: Number(r.sales_today_cents),
            sales_today_count: Number(r.sales_today_count),
            pending_payments: Number(r.pending_payments),
            low_stock_items: Number(r.low_stock_items),
        };
    });
}
//# sourceMappingURL=stats.js.map