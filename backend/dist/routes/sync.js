import { z } from "zod";
import { parse } from "../app.js";
import { forbidden } from "../lib/errors.js";
import { toIso } from "../lib/time.js";
const uuid = z.string().uuid();
const timestampText = z.string().min(10).max(40);
/** DATA_CONTRACT §8 — catalog rows arrive keyed by client UUID and are LWW by updated_at. */
const categoryIn = z.object({
    id: uuid,
    name: z.string().trim().min(1).max(120),
    sort_order: z.number().int().default(0),
    created_at: timestampText,
    updated_at: timestampText,
    deleted_at: timestampText.nullish(),
});
const productIn = z.object({
    id: uuid,
    category_id: uuid.nullish(),
    name: z.string().trim().min(1).max(200),
    sku: z.string().trim().max(64).nullish(),
    barcode: z.string().trim().max(64).nullish(),
    price_cents: z.number().int().min(0),
    tax_rate_bp: z.number().int().min(0).max(10000),
    cost_price_cents: z.number().int().min(0).nullish(),
    stock_quantity: z.number().int(),
    image_url: z.string().max(2048).nullish(),
    is_active: z.boolean().default(true),
    created_at: timestampText,
    updated_at: timestampText,
    deleted_at: timestampText.nullish(),
});
const saleItemIn = z.object({
    id: uuid,
    product_id: uuid,
    product_name: z.string().trim().min(1).max(200),
    quantity: z.number().int().positive(),
    unit_price_cents: z.number().int().min(0),
    line_total_cents: z.number().int().min(0),
});
const saleIn = z.object({
    id: uuid,
    receipt_number: z.string().trim().min(1).max(64),
    device_id: z.string().min(1).max(64).optional(),
    cashier_id: uuid,
    cashier_name: z.string().trim().min(1).max(120),
    payment_method: z.enum(["CASH", "MPESA", "CARD"]),
    subtotal_cents: z.number().int().min(0),
    tax_amount_cents: z.number().int().min(0),
    total_amount_cents: z.number().int().min(0),
    amount_received_cents: z.number().int().min(0),
    change_amount_cents: z.number().int().min(0).default(0),
    payment_status: z.enum(["PENDING", "PAID", "FAILED"]),
    mpesa_receipt: z.string().trim().max(32).nullish(),
    created_at: timestampText,
    updated_at: timestampText.optional(),
    deleted_at: timestampText.nullish(),
    items: z.array(saleItemIn).min(1),
});
const tombstoneIn = z.object({
    entity: z.enum(["branch", "category", "product", "sale"]),
    id: uuid,
    deleted_at: timestampText,
});
const pushBody = z.object({
    batch_id: z.string().min(1).max(100),
    device_id: z.string().min(1).max(64),
    branch_id: uuid,
    categories: z.array(categoryIn).max(200).default([]),
    products: z.array(productIn).max(200).default([]),
    sales: z.array(saleIn).max(50).default([]),
    tombstones: z.array(tombstoneIn).max(200).default([]),
});
/** Run q's statements under a savepoint so a unique violation does not abort the batch. */
async function inSavepoint(q, name, fn) {
    await q.query(`SAVEPOINT ${name}`);
    try {
        const out = await fn();
        await q.query(`RELEASE SAVEPOINT ${name}`);
        return out;
    }
    catch (err) {
        await q.query(`ROLLBACK TO SAVEPOINT ${name}`);
        await q.query(`RELEASE SAVEPOINT ${name}`);
        throw err;
    }
}
/** Compare a replayed sale against the stored copy (DATA_CONTRACT §3 immutable fields). */
async function saleDrift(q, branchId, deviceCode, sale, stored) {
    const diffs = [];
    if (stored.receipt_number !== sale.receipt_number)
        diffs.push("receipt_number");
    if (stored.branch_id !== branchId)
        diffs.push("branch_id");
    if (stored.device_id !== (sale.device_id ?? deviceCode))
        diffs.push("device_id");
    if (stored.cashier_id !== sale.cashier_id)
        diffs.push("cashier_id");
    if (stored.cashier_name !== sale.cashier_name)
        diffs.push("cashier_name");
    if (stored.payment_method !== sale.payment_method)
        diffs.push("payment_method");
    const amounts = [
        ["subtotal_cents", stored.subtotal_cents, sale.subtotal_cents],
        ["tax_amount_cents", stored.tax_amount_cents, sale.tax_amount_cents],
        ["total_amount_cents", stored.total_amount_cents, sale.total_amount_cents],
        ["amount_received_cents", stored.amount_received_cents, sale.amount_received_cents],
        ["change_amount_cents", stored.change_amount_cents, sale.change_amount_cents],
    ];
    for (const [field, actual, expected] of amounts) {
        if (Number(actual) !== expected)
            diffs.push(field);
    }
    if (stored.created_at.getTime() !== new Date(sale.created_at).getTime())
        diffs.push("created_at");
    const { rows: items } = await q.query(`SELECT id, product_id, product_name, quantity, unit_price_cents, line_total_cents
       FROM sale_item WHERE sale_id = $1`, [sale.id]);
    const byId = new Map(items.map((i) => [i.id, i]));
    let itemsMatch = items.length === sale.items.length;
    if (itemsMatch) {
        for (const item of sale.items) {
            const row = byId.get(item.id);
            if (!row ||
                row.product_id !== item.product_id ||
                row.product_name !== item.product_name ||
                Number(row.quantity) !== item.quantity ||
                Number(row.unit_price_cents) !== item.unit_price_cents ||
                Number(row.line_total_cents) !== item.line_total_cents) {
                itemsMatch = false;
                break;
            }
        }
    }
    if (!itemsMatch)
        diffs.push("items");
    return diffs;
}
function drifted(a, b) {
    return (a ?? null) !== (b ?? null);
}
/** Apply the sale's line deltas exactly once; receipt uniqueness enforces the once. */
async function applySale(q, branchId, deviceCode, sale, clock) {
    const warnings = [];
    const versions = [];
    // M-Pesa payment state is server-owned (API_CONTRACT §3): a device cannot push
    // a sale as already paid, nor supply a receipt for one.
    const paymentStatus = sale.payment_method === "MPESA" ? "PENDING" : sale.payment_status;
    const mpesaReceipt = sale.payment_method === "MPESA" ? null : sale.mpesa_receipt ?? null;
    await q.query(`SAVEPOINT apply_sale`);
    const { rows: saleRows } = await q.query(`INSERT INTO sale (id, receipt_number, branch_id, device_id, cashier_id, cashier_name,
                       payment_method, subtotal_cents, tax_amount_cents, total_amount_cents,
                       amount_received_cents, change_amount_cents, payment_status, mpesa_receipt,
                       created_at, received_at, server_version)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16,
             nextval('server_version_seq'))
     RETURNING server_version`, [
        sale.id,
        sale.receipt_number,
        branchId,
        sale.device_id ?? deviceCode,
        sale.cashier_id,
        sale.cashier_name,
        sale.payment_method,
        sale.subtotal_cents,
        sale.tax_amount_cents,
        sale.total_amount_cents,
        sale.amount_received_cents,
        sale.change_amount_cents,
        paymentStatus,
        mpesaReceipt,
        sale.created_at,
        clock,
    ]);
    const saleVersion = Number(saleRows[0].server_version);
    versions.push(saleVersion);
    for (const item of sale.items) {
        await q.query(`INSERT INTO sale_item (id, sale_id, product_id, product_name, quantity, unit_price_cents, line_total_cents)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`, [item.id, sale.id, item.product_id, item.product_name, item.quantity, item.unit_price_cents, item.line_total_cents]);
        const { rows } = await q.query(`UPDATE product SET stock_quantity = stock_quantity - $2, updated_at = $3,
              server_updated_at = now(), server_version = nextval('server_version_seq')
        WHERE id = $1 AND branch_id = $4 AND deleted_at IS NULL
        RETURNING server_version, price_cents`, [item.product_id, item.quantity, clock, branchId]);
        const product = rows[0];
        if (!product) {
            warnings.push({ product_id: item.product_id, code: "FK_MISSING" });
            continue;
        }
        versions.push(Number(product.server_version));
        if (Number(product.price_cents) !== item.unit_price_cents) {
            warnings.push({ product_id: item.product_id, code: "PRICE_DRIFT" });
        }
    }
    await q.query(`RELEASE SAVEPOINT apply_sale`);
    return { versions, saleVersion, warnings, paymentStatus };
}
export function registerSyncRoutes(app, deps, guards) {
    const { db } = deps;
    app.post("/sync/push", async (req) => {
        const device = await guards.requireDevice(req);
        const body = parse(pushBody, req.body);
        if (body.branch_id !== device.branchId) {
            throw forbidden("BRANCH_SCOPE_MISMATCH", "Payload branch_id does not match this device's branch");
        }
        if (body.device_id !== device.id) {
            throw forbidden("DEVICE_SCOPE_MISMATCH", "Payload device_id does not match the presented device token");
        }
        for (const sale of body.sales) {
            if (sale.device_id !== undefined && sale.device_id !== device.id) {
                throw forbidden("DEVICE_SCOPE_MISMATCH", "A sale in the batch belongs to a different device");
            }
        }
        const results = [];
        const versions = [];
        const clock = deps.clock();
        await db.tx(async (q) => {
            // -------------------------------------------------------- categories
            for (const cat of body.categories) {
                if (cat.deleted_at) {
                    const { rows } = await q.query(`UPDATE category SET deleted_at = $3, updated_at = $3, server_updated_at = now(),
                    server_version = nextval('server_version_seq')
              WHERE id = $1 AND branch_id = $2 AND (updated_at IS NULL OR updated_at < $3)
              RETURNING server_version`, [cat.id, device.branchId, cat.deleted_at]);
                    if (rows[0]) {
                        versions.push(Number(rows[0].server_version));
                        results.push({ entity: "category", id: cat.id, status: "SYNCED", server_version: Number(rows[0].server_version) });
                    }
                    else {
                        results.push({ entity: "category", id: cat.id, status: "CONFLICT", code: "STALE_WRITE", message: "A newer version exists" });
                    }
                    continue;
                }
                const existing = await q.query(`SELECT updated_at FROM category WHERE id = $1 AND branch_id = $2`, [cat.id, device.branchId]);
                if (existing.rows[0] && existing.rows[0].updated_at > new Date(cat.updated_at)) {
                    results.push({
                        entity: "category",
                        id: cat.id,
                        status: "CONFLICT",
                        code: "LAST_WRITE_WINS",
                        message: "The server has a newer copy of this category",
                    });
                    continue;
                }
                try {
                    const row = await inSavepoint(q, "cat_upsert", async () => {
                        const { rows } = await q.query(`INSERT INTO category (id, branch_id, name, sort_order, created_at, updated_at, deleted_at,
                                     server_updated_at, server_version)
               VALUES ($1, $2, $3, $4, $5, $6, $7, now(), nextval('server_version_seq'))
               ON CONFLICT (id) DO UPDATE SET
                 name = EXCLUDED.name, sort_order = EXCLUDED.sort_order,
                 deleted_at = EXCLUDED.deleted_at, updated_at = EXCLUDED.updated_at,
                 server_updated_at = now(), server_version = nextval('server_version_seq')
                 WHERE category.updated_at < EXCLUDED.updated_at
                   AND category.branch_id = EXCLUDED.branch_id
               RETURNING server_version`, [cat.id, device.branchId, cat.name, cat.sort_order, cat.created_at, cat.updated_at, cat.deleted_at ?? null]);
                        return rows[0];
                    });
                    if (!row) {
                        results.push({ entity: "category", id: cat.id, status: "CONFLICT", code: "LAST_WRITE_WINS", message: "The server has a newer copy" });
                        continue;
                    }
                    versions.push(Number(row.server_version));
                    results.push({ entity: "category", id: cat.id, status: "SYNCED", server_version: Number(row.server_version) });
                }
                catch (err) {
                    if (err.code === "23505") {
                        results.push({ entity: "category", id: cat.id, status: "REJECTED", code: "NAME_TAKEN", message: "Category name already exists" });
                    }
                    else {
                        throw err;
                    }
                }
            }
            // ---------------------------------------------------------- products
            for (const prod of body.products) {
                if (prod.deleted_at) {
                    const { rows } = await q.query(`UPDATE product SET deleted_at = $3, updated_at = $3, server_updated_at = now(),
                    server_version = nextval('server_version_seq')
              WHERE id = $1 AND branch_id = $2 AND (updated_at IS NULL OR updated_at < $3)
              RETURNING server_version`, [prod.id, device.branchId, prod.deleted_at]);
                    if (rows[0]) {
                        versions.push(Number(rows[0].server_version));
                        results.push({ entity: "product", id: prod.id, status: "SYNCED", server_version: Number(rows[0].server_version) });
                    }
                    else {
                        results.push({ entity: "product", id: prod.id, status: "CONFLICT", code: "STALE_WRITE", message: "A newer version exists" });
                    }
                    continue;
                }
                const existing = await q.query(`SELECT updated_at, price_cents FROM product WHERE id = $1 AND branch_id = $2`, [prod.id, device.branchId]);
                const prev = existing.rows[0];
                if (prev && prev.updated_at > new Date(prod.updated_at)) {
                    results.push({
                        entity: "product",
                        id: prod.id,
                        status: "CONFLICT",
                        code: "LAST_WRITE_WINS",
                        message: "The server has a newer copy of this product",
                    });
                    continue;
                }
                if (prod.category_id) {
                    const cat = await q.query(`SELECT 1 FROM category WHERE id = $1 AND branch_id = $2 AND deleted_at IS NULL`, [prod.category_id, device.branchId]);
                    if (!cat.rowCount) {
                        results.push({
                            entity: "product",
                            id: prod.id,
                            status: "REJECTED",
                            code: "FK_MISSING",
                            message: "category_id does not exist in this branch",
                        });
                        continue;
                    }
                }
                try {
                    // stock_quantity travels with new products; for existing rows the sale
                    // deltas own the column (DATA_CONTRACT §6.4), so we leave it alone.
                    const row = await inSavepoint(q, "prod_upsert", async () => {
                        const { rows } = await q.query(`INSERT INTO product (id, branch_id, category_id, name, sku, barcode, price_cents, tax_rate_bp,
                                  cost_price_cents, stock_quantity, image_url, is_active,
                                  created_at, updated_at, deleted_at, server_updated_at, server_version)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, now(),
                     nextval('server_version_seq'))
             ON CONFLICT (id) DO UPDATE SET
               category_id = EXCLUDED.category_id, name = EXCLUDED.name, sku = EXCLUDED.sku,
               barcode = EXCLUDED.barcode, price_cents = EXCLUDED.price_cents,
               tax_rate_bp = EXCLUDED.tax_rate_bp, cost_price_cents = EXCLUDED.cost_price_cents,
               image_url = EXCLUDED.image_url, is_active = EXCLUDED.is_active,
               deleted_at = EXCLUDED.deleted_at, updated_at = EXCLUDED.updated_at,
               server_updated_at = now(), server_version = nextval('server_version_seq')
               WHERE product.updated_at < EXCLUDED.updated_at
                 AND product.branch_id = EXCLUDED.branch_id
             RETURNING server_version`, [
                            prod.id,
                            device.branchId,
                            prod.category_id ?? null,
                            prod.name,
                            prod.sku ?? null,
                            prod.barcode ?? null,
                            prod.price_cents,
                            prod.tax_rate_bp,
                            prod.cost_price_cents ?? null,
                            prod.stock_quantity,
                            prod.image_url ?? null,
                            prod.is_active,
                            prod.created_at,
                            prod.updated_at,
                            prod.deleted_at ?? null,
                        ]);
                        return rows[0];
                    });
                    if (!row) {
                        results.push({ entity: "product", id: prod.id, status: "CONFLICT", code: "LAST_WRITE_WINS", message: "The server has a newer copy" });
                        continue;
                    }
                    versions.push(Number(row.server_version));
                    results.push({ entity: "product", id: prod.id, status: "SYNCED", server_version: Number(row.server_version) });
                }
                catch (err) {
                    const code = err.code;
                    const constraint = err.constraint;
                    if (code === "23505" && constraint === "product_sku_uq") {
                        results.push({ entity: "product", id: prod.id, status: "REJECTED", code: "SKU_TAKEN", message: "SKU already used in this branch" });
                    }
                    else if (code === "23505" && constraint === "product_barcode_uq") {
                        results.push({ entity: "product", id: prod.id, status: "REJECTED", code: "BARCODE_TAKEN", message: "Barcode already used" });
                    }
                    else {
                        throw err;
                    }
                }
            }
            // ------------------------------------------------------------- sales
            for (const sale of body.sales) {
                // Sales are append-only (DATA_CONTRACT §6.2): a repeated id is a replay
                // only when every immutable field matches; edits are rejected.
                const existing = await q.query(`SELECT receipt_number, branch_id, device_id, cashier_id, cashier_name, payment_method,
                  subtotal_cents, tax_amount_cents, total_amount_cents, amount_received_cents,
                  change_amount_cents, created_at, payment_status, server_version
             FROM sale WHERE id = $1`, [sale.id]);
                const stored = existing.rows[0];
                if (stored) {
                    const drift = await saleDrift(q, device.branchId, device.code, sale, stored);
                    if (drift.length) {
                        results.push({
                            entity: "sale",
                            id: sale.id,
                            status: "CONFLICT",
                            code: "SALE_IMMUTABLE",
                            message: `attempted to modify ${drift.join(", ")} of an existing sale`,
                        });
                    }
                    else {
                        versions.push(Number(stored.server_version));
                        results.push({
                            entity: "sale",
                            id: sale.id,
                            status: "SYNCED",
                            code: "ALREADY_APPLIED",
                            message: "Sale was already recorded",
                            server_version: Number(stored.server_version),
                            payment_status: stored.payment_status,
                        });
                    }
                    continue;
                }
                try {
                    const applied = await applySale(q, device.branchId, device.code, sale, clock);
                    versions.push(...applied.versions);
                    results.push({
                        entity: "sale",
                        id: sale.id,
                        status: "SYNCED",
                        server_version: applied.saleVersion,
                        payment_status: applied.paymentStatus,
                        ...(applied.warnings.some((w) => w.code === "PRICE_DRIFT") ? { warning: "PRICE_DRIFT" } : {}),
                    });
                }
                catch (err) {
                    const e = err;
                    await q.query(`ROLLBACK TO SAVEPOINT apply_sale`);
                    await q.query(`RELEASE SAVEPOINT apply_sale`);
                    if (e.code === "23505" && e.constraint === "sale_pkey") {
                        const { rows: existing } = await q.query(`SELECT server_version FROM sale WHERE id = $1`, [sale.id]);
                        results.push({
                            entity: "sale",
                            id: sale.id,
                            status: "SYNCED",
                            code: "ALREADY_APPLIED",
                            message: "Sale was already recorded",
                            server_version: existing[0] ? Number(existing[0].server_version) : undefined,
                        });
                    }
                    else if (e.code === "23505" && e.constraint === "sale_branch_id_receipt_number_key") {
                        results.push({
                            entity: "sale",
                            id: sale.id,
                            status: "REJECTED",
                            code: "RECEIPT_DUPLICATE",
                            message: "That receipt_number already exists in this branch",
                        });
                    }
                    else if (e.code === "23503") {
                        results.push({ entity: "sale", id: sale.id, status: "REJECTED", code: "FK_MISSING", message: "A referenced row does not exist" });
                    }
                    else if (e.code === "23514") {
                        results.push({ entity: "sale", id: sale.id, status: "REJECTED", code: "VALIDATION_FAILED", message: "Sale failed a database constraint" });
                    }
                    else {
                        throw err;
                    }
                }
            }
            // -------------------------------------------------------- tombstones
            for (const stone of body.tombstones) {
                if (stone.entity !== "category" && stone.entity !== "product") {
                    results.push({
                        entity: stone.entity,
                        id: stone.id,
                        status: "REJECTED",
                        code: "UNSUPPORTED_ENTITY",
                        message: "Only category and product rows can be deleted from a device",
                    });
                    continue;
                }
                const table = stone.entity === "category" ? "category" : "product";
                const { rows } = await q.query(`UPDATE ${table}
              SET deleted_at = $3, updated_at = $3, server_updated_at = now(),
                  server_version = nextval('server_version_seq')
            WHERE id = $1 AND branch_id = $2 AND (updated_at IS NULL OR updated_at < $3)
            RETURNING server_version`, [stone.id, device.branchId, stone.deleted_at]);
                if (rows[0]) {
                    versions.push(Number(rows[0].server_version));
                    results.push({ entity: stone.entity, id: stone.id, status: "SYNCED", server_version: Number(rows[0].server_version) });
                }
                else {
                    results.push({ entity: stone.entity, id: stone.id, status: "CONFLICT", code: "STALE_WRITE", message: "A newer version exists" });
                }
            }
        });
        const maxVersion = versions.length ? Math.max(...versions) : null;
        return {
            batch_id: body.batch_id,
            server_version: maxVersion,
            results,
        };
    });
    app.get("/sync/pull", async (req) => {
        const device = await guards.requireDevice(req);
        const q = z
            .object({
            since: z.coerce.number().int().min(0).default(0),
            branch_id: uuid.optional(),
        })
            .parse(req.query ?? {});
        if (q.branch_id && q.branch_id !== device.branchId) {
            throw forbidden("BRANCH_SCOPE_MISMATCH", "You can only pull this device's branch");
        }
        const branchId = device.branchId;
        const branches = await db.query(`SELECT id, name, code, location, is_active, created_at, updated_at, deleted_at, server_version
         FROM branch WHERE id = $1 AND server_version > $2 LIMIT 1`, [branchId, q.since]);
        const categories = await db.query(`SELECT id, name, sort_order, created_at, updated_at, deleted_at, server_version
         FROM category WHERE branch_id = $1 AND server_version > $2 ORDER BY server_version LIMIT 1000`, [branchId, q.since]);
        const products = await db.query(`SELECT id, category_id, name, sku, barcode, price_cents, tax_rate_bp, cost_price_cents,
              stock_quantity, image_url, is_active, created_at, updated_at, deleted_at, server_version
         FROM product WHERE branch_id = $1 AND server_version > $2 ORDER BY server_version LIMIT 1000`, [branchId, q.since]);
        // Sales only flow down when their payment status changed (e.g. STK confirmed).
        const sales = await db.query(`SELECT id, payment_status, mpesa_receipt, payment_updated_at, server_version
         FROM sale WHERE branch_id = $1 AND server_version > $2 AND payment_updated_at IS NOT NULL
        ORDER BY server_version LIMIT 1000`, [branchId, q.since]);
        // Soft-deleted rows surface as tombstones so clients can purge their copies.
        const stones = await db.query(`SELECT 'category'::text AS entity, id, deleted_at, server_version FROM category
        WHERE branch_id = $1 AND deleted_at IS NOT NULL AND server_version > $2
       UNION ALL
       SELECT 'product'::text AS entity, id, deleted_at, server_version FROM product
        WHERE branch_id = $1 AND deleted_at IS NOT NULL AND server_version > $2
       ORDER BY server_version LIMIT 1000`, [branchId, q.since]);
        let maxVersion = q.since;
        const sets = [branches.rows, categories.rows, products.rows, sales.rows, stones.rows];
        for (const set of sets) {
            for (const row of set)
                maxVersion = Math.max(maxVersion, Number(row.server_version));
        }
        return {
            server_version: maxVersion,
            has_more: sets.some((set) => set.length === 1000),
            changes: {
                branches: branches.rows.map((r) => ({
                    id: r.id,
                    name: r.name,
                    code: r.code,
                    location: r.location,
                    is_active: r.is_active,
                    created_at: toIso(r.created_at),
                    updated_at: toIso(r.updated_at),
                    deleted_at: toIso(r.deleted_at),
                    server_version: Number(r.server_version),
                })),
                categories: categories.rows.map((r) => ({
                    id: r.id,
                    name: r.name,
                    sort_order: r.sort_order,
                    created_at: toIso(r.created_at),
                    updated_at: toIso(r.updated_at),
                    deleted_at: toIso(r.deleted_at),
                    server_version: Number(r.server_version),
                })),
                products: products.rows.map((r) => ({
                    id: r.id,
                    category_id: r.category_id,
                    name: r.name,
                    sku: r.sku,
                    barcode: r.barcode,
                    price_cents: Number(r.price_cents),
                    tax_rate_bp: r.tax_rate_bp,
                    cost_price_cents: r.cost_price_cents === null ? null : Number(r.cost_price_cents),
                    stock_quantity: r.stock_quantity,
                    image_url: r.image_url,
                    is_active: r.is_active,
                    created_at: toIso(r.created_at),
                    updated_at: toIso(r.updated_at),
                    deleted_at: toIso(r.deleted_at),
                    server_version: Number(r.server_version),
                })),
                sales: sales.rows.map((r) => ({
                    id: r.id,
                    payment_status: r.payment_status,
                    mpesa_receipt: r.mpesa_receipt,
                    payment_updated_at: toIso(r.payment_updated_at),
                    server_version: Number(r.server_version),
                })),
            },
            tombstones: stones.rows.map((r) => ({
                entity: r.entity,
                id: r.id,
                deleted_at: toIso(r.deleted_at),
                server_version: Number(r.server_version),
            })),
        };
    });
}
//# sourceMappingURL=sync.js.map