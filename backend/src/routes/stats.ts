import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { type Deps, type Guards } from "../app.js";
import { forbidden, notFound } from "../lib/errors.js";
import type { AccessClaims } from "../lib/jwt.js";

/** Matches the admin app's "low stock" badge threshold. */
export const LOW_STOCK_THRESHOLD = 5;

const uuid = z.string().uuid();

function scopeBranch(claims: AccessClaims, branchId: string): void {
  if (claims.role === "OWNER") return;
  if (!claims.branch_ids.includes(branchId)) {
    throw forbidden("BRANCH_FORBIDDEN", "Your account does not have access to this branch");
  }
}

interface Today {
  sales_total_cents: number;
  sales_count: number;
  low_stock_count: number;
  devices_count: number;
  top_sellers: {
    product_id: string;
    name: string;
    quantity_sold: number;
    revenue_cents: number;
  }[];
}

const DAY_MS = 86_400_000;

/** UTC day boundaries for "today" — matching the report timezone the CLI apps use. */
function dayBounds(now: Date): { from: Date; to: Date } {
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  return { from, to: new Date(from.getTime() + DAY_MS) };
}

const REPORT_WINDOW = z.object({ days: z.coerce.number().int().min(1).max(90).default(7) });

/** Inclusive window of whole UTC days ending today. */
function windowBounds(now: Date, days: number): { from: Date; to: Date } {
  const today = dayBounds(now).from;
  return { from: new Date(today.getTime() - (days - 1) * DAY_MS), to: new Date(today.getTime() + DAY_MS) };
}

/** The UTC calendar date a window bound falls on, e.g. "2026-11-25". */
function isoDate(at: Date): string {
  return at.toISOString().slice(0, 10);
}

async function branchToday(deps: Deps, branchId: string): Promise<Today> {
  const { from, to } = dayBounds(deps.clock());
  const { rows } = await deps.db.query<{
    sales_total_cents: string;
    sales_count: string;
  }>(
    `SELECT COALESCE(SUM(total_amount_cents) FILTER (WHERE payment_status = 'PAID'), 0) AS sales_total_cents,
            COUNT(*) FILTER (WHERE payment_status = 'PAID') AS sales_count
       FROM sale
      WHERE branch_id = $1 AND created_at >= $2 AND created_at < $3`,
    [branchId, from, to],
  );

  const { rows: lowRows } = await deps.db.query<{ low_stock_count: string }>(
    `SELECT COUNT(*) AS low_stock_count FROM product
      WHERE branch_id = $1 AND deleted_at IS NULL AND is_active
        AND stock_quantity <= $2`,
    [branchId, LOW_STOCK_THRESHOLD],
  );

  const { rows: deviceRows } = await deps.db.query<{ devices_count: string }>(
    `SELECT COUNT(*) AS devices_count FROM device WHERE branch_id = $1 AND NOT is_revoked`,
    [branchId],
  );

  const { rows: topRows } = await deps.db.query<{
    product_id: string;
    name: string;
    quantity_sold: string;
    revenue_cents: string;
  }>(
    `SELECT si.product_id, COALESCE(MIN(p.name), MIN(si.product_name)) AS name,
            SUM(si.quantity) AS quantity_sold, SUM(si.line_total_cents) AS revenue_cents
       FROM sale_item si
       JOIN sale s ON s.id = si.sale_id
       LEFT JOIN product p ON p.id = si.product_id
      WHERE s.branch_id = $1 AND s.created_at >= $2 AND s.created_at < $3
      GROUP BY si.product_id
      ORDER BY quantity_sold DESC
      LIMIT 5`,
    [branchId, from, to],
  );

  const summary = rows[0]!;
  return {
    sales_total_cents: Number(summary.sales_total_cents),
    sales_count: Number(summary.sales_count),
    low_stock_count: Number(lowRows[0]!.low_stock_count),
    devices_count: Number(deviceRows[0]!.devices_count),
    top_sellers: topRows.map((r) => ({
      product_id: r.product_id,
      name: r.name,
      quantity_sold: Number(r.quantity_sold),
      revenue_cents: Number(r.revenue_cents),
    })),
  };
}

export function registerStatsRoutes(app: FastifyInstance, deps: Deps, guards: Guards): void {
  const { db } = deps;

  app.get("/branches/:id/stats/today", async (req) => {
    const claims = await guards.requireAdmin(req);
    const branchId = uuid.parse((req.params as { id: string }).id);
    scopeBranch(claims, branchId);
    const branch = await db.query(`SELECT 1 FROM branch WHERE id = $1 AND deleted_at IS NULL`, [branchId]);
    if (!branch.rowCount) throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
    return branchToday(deps, branchId);
  });

  app.get("/dashboard/summary", async (req) => {
    const claims = await guards.requireAdmin(req);
    const { from, to } = dayBounds(deps.clock());
    const branchIds = claims.role === "OWNER" ? null : claims.branch_ids;

    // For OWNER, branchIds is null and the scope predicate matches everything.
    const { rows } = await db.query<{
      branches_count: string;
      active_devices: string;
      sales_today_cents: string;
      sales_today_count: string;
      pending_payments: string;
      low_stock_items: string;
    }>(
      `SELECT
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
            AND ($1::uuid[] IS NULL OR branch_id = ANY($1::uuid[]))) AS low_stock_items`,
      [branchIds, from, to, LOW_STOCK_THRESHOLD],
    );
    const r = rows[0]!;
    return {
      branches_count: Number(r.branches_count),
      active_devices: Number(r.active_devices),
      sales_today_cents: Number(r.sales_today_cents),
      sales_today_count: Number(r.sales_today_count),
      pending_payments: Number(r.pending_payments),
      low_stock_items: Number(r.low_stock_items),
    };
  });

  // Daily paid-sales totals for one branch, zero-filled for days with no sales.
  app.get("/branches/:id/reports/sales-trend", async (req) => {
    const claims = await guards.requireAdmin(req);
    const branchId = uuid.parse((req.params as { id: string }).id);
    scopeBranch(claims, branchId);
    const { days } = REPORT_WINDOW.parse(req.query);

    const branch = await db.query<{ name: string }>(
      `SELECT name FROM branch WHERE id = $1 AND deleted_at IS NULL`,
      [branchId],
    );
    if (!branch.rowCount) throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");

    const { from, to } = windowBounds(deps.clock(), days);
    const { rows } = await db.query<{
      day: string;
      sales_total_cents: string;
      sales_count: string;
    }>(
      // Series bounds are separate date parameters: reusing the timestamptz
      // bounds would let Postgres infer them as `date` and truncate the window
      // in the session timezone.
      `WITH series AS (
         SELECT generate_series($4::date, $5::date, interval '1 day')::date AS day
       ), agg AS (
         SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                COALESCE(SUM(total_amount_cents) FILTER (WHERE payment_status = 'PAID'), 0) AS total,
                COUNT(*) FILTER (WHERE payment_status = 'PAID') AS cnt
           FROM sale
          WHERE branch_id = $1 AND created_at >= $2 AND created_at < $3
          GROUP BY 1
       )
       SELECT to_char(s.day, 'YYYY-MM-DD') AS day,
              COALESCE(a.total, 0) AS sales_total_cents,
              COALESCE(a.cnt, 0) AS sales_count
         FROM series s
         LEFT JOIN agg a ON a.day = s.day
        ORDER BY s.day`,
      [branchId, from, to, isoDate(from), isoDate(new Date(to.getTime() - DAY_MS))],
    );

    return {
      branch_id: branchId,
      branch_name: branch.rows[0]!.name,
      days,
      from: from.toISOString(),
      to: to.toISOString(),
      points: rows.map((r) => ({
        date: r.day,
        sales_total_cents: Number(r.sales_total_cents),
        sales_count: Number(r.sales_count),
      })),
    };
  });

  // Revenue, sale count, low stock and live devices per branch for the same window.
  app.get("/reports/branch-comparison", async (req) => {
    const claims = await guards.requireAdmin(req);
    const { days } = REPORT_WINDOW.parse(req.query);
    const { from, to } = windowBounds(deps.clock(), days);
    const branchIds = claims.role === "OWNER" ? null : claims.branch_ids;

    const { rows } = await db.query<{
      branch_id: string;
      name: string;
      sales_total_cents: string;
      sales_count: string;
      low_stock_items: string;
      devices_count: string;
    }>(
      `SELECT b.id AS branch_id, b.name,
              COALESCE(SUM(s.total_amount_cents) FILTER (WHERE s.payment_status = 'PAID'), 0) AS sales_total_cents,
              COUNT(s.id) FILTER (WHERE s.payment_status = 'PAID') AS sales_count,
              (SELECT COUNT(*) FROM product p
                WHERE p.branch_id = b.id AND p.deleted_at IS NULL AND p.is_active
                  AND p.stock_quantity <= $4) AS low_stock_items,
              (SELECT COUNT(*) FROM device d WHERE d.branch_id = b.id AND NOT d.is_revoked) AS devices_count
         FROM branch b
         LEFT JOIN sale s ON s.branch_id = b.id AND s.created_at >= $2 AND s.created_at < $3
        WHERE b.deleted_at IS NULL AND ($1::uuid[] IS NULL OR b.id = ANY($1::uuid[]))
        GROUP BY b.id, b.name
        ORDER BY sales_total_cents DESC, b.name`,
      [branchIds, from, to, LOW_STOCK_THRESHOLD],
    );

    return {
      days,
      from: from.toISOString(),
      to: to.toISOString(),
      branches: rows.map((r) => ({
        branch_id: r.branch_id,
        name: r.name,
        sales_total_cents: Number(r.sales_total_cents),
        sales_count: Number(r.sales_count),
        low_stock_items: Number(r.low_stock_items),
        devices_count: Number(r.devices_count),
      })),
    };
  });
}
