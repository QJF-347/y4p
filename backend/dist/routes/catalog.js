import { z } from "zod";
import { parse } from "../app.js";
import { writeAudit } from "../lib/audit.js";
import { badRequest, conflict, forbidden, notFound, unprocessable } from "../lib/errors.js";
import { newDeviceCode, newId, newOpaqueToken, sha256Hex } from "../lib/ids.js";
import { addSeconds, parseTimestamp, toIso } from "../lib/time.js";
import { LOW_STOCK_THRESHOLD } from "./stats.js";
const uuid = z.string().uuid();
const isoDate = z.string().min(10).max(40);
const branchCreate = z.object({
    id: uuid.optional(),
    name: z.string().trim().min(1).max(120),
    code: z.string().trim().regex(/^[A-Z0-9-]{2,12}$/),
    location: z.string().trim().max(200).nullish(),
    is_active: z.boolean().default(true),
    created_at: isoDate.optional(),
});
const branchPatch = z
    .object({
    name: z.string().trim().min(1).max(120).optional(),
    location: z.string().trim().max(200).nullish(),
    is_active: z.boolean().optional(),
    created_at: isoDate.optional(),
})
    .strict();
const categoryCreate = z.object({
    id: uuid.optional(),
    name: z.string().trim().min(1).max(120),
    sort_order: z.number().int().default(0),
    created_at: isoDate.optional(),
});
const categoryPatch = z.object({
    name: z.string().trim().min(1).max(120).optional(),
    sort_order: z.number().int().optional(),
});
const productCreate = z.object({
    id: uuid.optional(),
    category_id: uuid.nullish(),
    name: z.string().trim().min(1).max(200),
    sku: z.string().trim().max(64).nullish(),
    barcode: z.string().trim().max(64).nullish(),
    price_cents: z.number().int().min(0),
    tax_rate_bp: z.number().int().min(0).max(10000).default(1600),
    cost_price_cents: z.number().int().min(0).nullish(),
    stock_quantity: z.number().int().default(0),
    image_url: z.string().max(2048).nullish(),
    is_active: z.boolean().default(true),
    created_at: isoDate.optional(),
});
const productPatch = z
    .object({
    name: z.string().trim().min(1).max(200).optional(),
    category_id: uuid.nullish(),
    sku: z.string().trim().max(64).nullish(),
    barcode: z.string().trim().max(64).nullish(),
    price_cents: z.number().int().min(0).optional(),
    tax_rate_bp: z.number().int().min(0).max(10000).optional(),
    cost_price_cents: z.number().int().min(0).nullish(),
    image_url: z.string().max(2048).nullish(),
    is_active: z.boolean().optional(),
    // Present only so we can return USE_RESTOCK_ENDPOINT instead of silently ignoring it.
    stock_quantity: z.number().int().optional(),
    // Immutable fields, rejected explicitly rather than stripped.
    id: z.unknown().optional(),
    branch_id: z.unknown().optional(),
    created_at: z.unknown().optional(),
})
    .strict();
const restockBody = z.object({
    stock_quantity: z.number().int(),
    reason: z.string().trim().max(500).optional(),
});
const deviceCreate = z.object({ name: z.string().trim().min(1).max(120) });
const salesQuery = z.object({
    from: z.string().optional(),
    to: z.string().optional(),
    payment_status: z.enum(["PENDING", "PAID", "FAILED"]).optional(),
    limit: z.coerce.number().int().min(1).max(200).default(100),
    cursor: z.string().optional(),
});
/** Throws unless the admin token can see this branch. */
function scopeBranch(claims, branchId) {
    if (claims.role === "OWNER")
        return;
    if (!claims.branch_ids.includes(branchId)) {
        throw forbidden("BRANCH_FORBIDDEN", "Your account does not have access to this branch");
    }
}
async function assertBranch(deps, branchId) {
    const { rows } = await deps.db.query(`SELECT 1 FROM branch WHERE id = $1 AND deleted_at IS NULL`, [branchId]);
    if (rows.length === 0)
        throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
}
function branchJson(r) {
    return {
        id: r.id,
        name: r.name,
        code: r.code,
        location: r.location,
        is_active: r.is_active,
        created_at: toIso(r.created_at),
        updated_at: toIso(r.updated_at),
        deleted_at: toIso(r.deleted_at),
        server_version: Number(r.server_version),
    };
}
function categoryJson(r) {
    return {
        id: r.id,
        branch_id: r.branch_id,
        name: r.name,
        sort_order: r.sort_order,
        created_at: toIso(r.created_at),
        updated_at: toIso(r.updated_at),
        deleted_at: toIso(r.deleted_at),
        server_version: Number(r.server_version),
    };
}
function productJson(r) {
    return {
        id: r.id,
        branch_id: r.branch_id,
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
    };
}
const PRODUCT_COLUMNS = `id, branch_id, category_id, name, sku, barcode, price_cents, tax_rate_bp,
  cost_price_cents, stock_quantity, image_url, is_active, created_at, updated_at, deleted_at, server_version`;
const CATEGORY_COLUMNS = `id, branch_id, name, sort_order, created_at, updated_at, deleted_at, server_version`;
const BRANCH_COLUMNS = `id, name, code, location, is_active, created_at, updated_at, deleted_at, server_version`;
function uniqueViolation(err) {
    const e = err;
    return e.code === "23505" ? (e.constraint ?? "unknown") : null;
}
export function registerCatalogRoutes(app, deps, guards) {
    const { db } = deps;
    // ---------------------------------------------------------------- branches
    app.get("/branches", async (req) => {
        const claims = await guards.requireAdmin(req);
        const includeDeleted = z
            .object({ include_deleted: z.coerce.boolean().default(false) })
            .parse(req.query ?? {}).include_deleted;
        const { rows } = claims.role === "OWNER"
            ? await db.query(`SELECT ${BRANCH_COLUMNS} FROM branch
              WHERE ($1::boolean OR deleted_at IS NULL) ORDER BY created_at`, [includeDeleted])
            : await db.query(`SELECT ${BRANCH_COLUMNS} FROM branch
              WHERE id = ANY($1::uuid[]) AND ($2::boolean OR deleted_at IS NULL) ORDER BY created_at`, [claims.branch_ids, includeDeleted]);
        return { data: rows.map(branchJson) };
    });
    app.post("/branches", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const body = parse(branchCreate, req.body);
        const id = body.id ?? newId();
        const createdAt = parseTimestamp(body.created_at) ?? deps.clock();
        try {
            const { rows } = await db.query(`INSERT INTO branch (id, name, code, location, is_active, created_at, updated_at, server_version)
         VALUES ($1, $2, $3, $4, $5, $6, $6, nextval('server_version_seq'))
         RETURNING ${BRANCH_COLUMNS}`, [id, body.name, body.code, body.location ?? null, body.is_active, createdAt]);
            await writeAudit(db, {
                actorType: "USER",
                actorId: claims.sub,
                action: "branch.create",
                entity: "branch",
                entityId: id,
                ip: req.ip,
            });
            reply.status(201).send(branchJson(rows[0]));
        }
        catch (err) {
            if (uniqueViolation(err) === "branch_code_key") {
                throw conflict("CODE_TAKEN", "Another branch already uses that code");
            }
            throw err;
        }
    });
    app.get("/branches/:id", async (req) => {
        const claims = await guards.requireAdmin(req);
        const id = uuid.parse(req.params.id);
        scopeBranch(claims, id);
        const { rows } = await db.query(`SELECT ${BRANCH_COLUMNS} FROM branch WHERE id = $1`, [id]);
        if (!rows[0])
            throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
        return branchJson(rows[0]);
    });
    app.patch("/branches/:id", async (req) => {
        const claims = await guards.requireOwner(req);
        const id = uuid.parse(req.params.id);
        const body = parse(branchPatch, req.body);
        const { rows } = await db.query(`UPDATE branch SET
         name = COALESCE($2, name),
         location = CASE WHEN $3::boolean THEN $4 ELSE location END,
         is_active = COALESCE($5, is_active),
         updated_at = $6,
         server_updated_at = now(),
         server_version = nextval('server_version_seq')
       WHERE id = $1 AND deleted_at IS NULL
       RETURNING ${BRANCH_COLUMNS}`, [
            id,
            body.name ?? null,
            "location" in body,
            body.location ?? null,
            body.is_active ?? null,
            deps.clock(),
        ]);
        if (!rows[0])
            throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
        await writeAudit(db, {
            actorType: "USER",
            actorId: claims.sub,
            action: "branch.update",
            entity: "branch",
            entityId: id,
            details: body,
            ip: req.ip,
        });
        return branchJson(rows[0]);
    });
    app.delete("/branches/:id", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const id = uuid.parse(req.params.id);
        await db.tx(async (q) => {
            const devices = await q.query(`SELECT 1 FROM device WHERE branch_id = $1 AND NOT is_revoked LIMIT 1`, [id]);
            if (devices.rowCount) {
                throw conflict("BRANCH_IN_USE", "Revoke the branch's devices before deleting it");
            }
            const pending = await q.query(`SELECT 1 FROM sale WHERE branch_id = $1 AND payment_status = 'PENDING' LIMIT 1`, [id]);
            if (pending.rowCount) {
                throw conflict("BRANCH_IN_USE", "The branch has payments still pending");
            }
            const { rows } = await q.query(`UPDATE branch SET deleted_at = $2, is_active = FALSE, updated_at = $2,
                server_updated_at = now(), server_version = nextval('server_version_seq')
          WHERE id = $1 AND deleted_at IS NULL RETURNING id`, [id, deps.clock()]);
            if (!rows[0])
                throw notFound("BRANCH_NOT_FOUND", "Branch does not exist");
            await writeAudit(q, {
                actorType: "USER",
                actorId: claims.sub,
                action: "branch.delete",
                entity: "branch",
                entityId: id,
                ip: req.ip,
            });
        });
        reply.status(204).send();
    });
    // -------------------------------------------------------------- categories
    app.get("/branches/:id/categories", async (req) => {
        const claims = await guards.requireAdmin(req);
        const branchId = uuid.parse(req.params.id);
        scopeBranch(claims, branchId);
        const q = z
            .object({
            updated_since: z.coerce.number().int().min(0).optional(),
            include_deleted: z.coerce.boolean().default(false),
        })
            .parse(req.query ?? {});
        const { rows } = await db.query(`SELECT ${CATEGORY_COLUMNS} FROM category
        WHERE branch_id = $1
          AND ($2::bigint IS NULL OR server_version > $2)
          AND ($3::boolean OR deleted_at IS NULL)
        ORDER BY server_version, sort_order`, [branchId, q.updated_since ?? null, q.include_deleted]);
        return { data: rows.map(categoryJson) };
    });
    app.post("/branches/:id/categories", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const branchId = uuid.parse(req.params.id);
        const body = parse(categoryCreate, req.body);
        await assertBranch(deps, branchId);
        const id = body.id ?? newId();
        const createdAt = parseTimestamp(body.created_at) ?? deps.clock();
        try {
            const { rows } = await db.query(`INSERT INTO category (id, branch_id, name, sort_order, created_at, updated_at, server_version)
         VALUES ($1, $2, $3, $4, $5, $5, nextval('server_version_seq'))
         RETURNING ${CATEGORY_COLUMNS}`, [id, branchId, body.name, body.sort_order, createdAt]);
            await writeAudit(db, {
                actorType: "USER",
                actorId: claims.sub,
                action: "category.create",
                entity: "category",
                entityId: id,
                ip: req.ip,
            });
            reply.status(201).send(categoryJson(rows[0]));
        }
        catch (err) {
            if (uniqueViolation(err) === "category_name_uq") {
                throw conflict("CATEGORY_NAME_TAKEN", "A category with that name already exists in this branch");
            }
            throw err;
        }
    });
    app.patch("/categories/:cid", async (req) => {
        const claims = await guards.requireOwner(req);
        const cid = uuid.parse(req.params.cid);
        const body = parse(categoryPatch, req.body);
        const { rows } = await db.query(`UPDATE category SET
         name = COALESCE($2, name),
         sort_order = COALESCE($3, sort_order),
         updated_at = $4,
         server_updated_at = now(),
         server_version = nextval('server_version_seq')
       WHERE id = $1 AND deleted_at IS NULL
       RETURNING ${CATEGORY_COLUMNS}`, [cid, body.name ?? null, body.sort_order ?? null, deps.clock()]);
        if (!rows[0])
            throw notFound("CATEGORY_NOT_FOUND", "Category does not exist");
        scopeBranch(claims, rows[0].branch_id);
        await writeAudit(db, {
            actorType: "USER",
            actorId: claims.sub,
            action: "category.update",
            entity: "category",
            entityId: cid,
            details: body,
            ip: req.ip,
        });
        return categoryJson(rows[0]);
    });
    app.delete("/categories/:cid", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const cid = uuid.parse(req.params.cid);
        await db.tx(async (q) => {
            const { rows } = await q.query(`SELECT branch_id FROM category WHERE id = $1 AND deleted_at IS NULL`, [cid]);
            const cat = rows[0];
            if (!cat)
                throw notFound("CATEGORY_NOT_FOUND", "Category does not exist");
            const products = await q.query(`SELECT 1 FROM product WHERE category_id = $1 AND deleted_at IS NULL LIMIT 1`, [cid]);
            if (products.rowCount) {
                throw conflict("CATEGORY_IN_USE", "Move or delete the products in this category first");
            }
            await q.query(`UPDATE category SET deleted_at = $2, updated_at = $2, server_updated_at = now(),
                server_version = nextval('server_version_seq') WHERE id = $1`, [cid, deps.clock()]);
            await writeAudit(q, {
                actorType: "USER",
                actorId: claims.sub,
                action: "category.delete",
                entity: "category",
                entityId: cid,
                ip: req.ip,
            });
        });
        reply.status(204).send();
    });
    // ---------------------------------------------------------------- products
    app.get("/branches/:id/products", async (req) => {
        const claims = await guards.requireAdmin(req);
        const branchId = uuid.parse(req.params.id);
        scopeBranch(claims, branchId);
        const q = z
            .object({
            updated_since: z.coerce.number().int().min(0).optional(),
            include_deleted: z.coerce.boolean().default(false),
            search: z.string().trim().max(120).optional(),
        })
            .parse(req.query ?? {});
        const { rows } = await db.query(`SELECT ${PRODUCT_COLUMNS} FROM product
        WHERE branch_id = $1
          AND ($2::bigint IS NULL OR server_version > $2)
          AND ($3::boolean OR deleted_at IS NULL)
          AND ($4::text IS NULL OR name ILIKE '%' || $4 || '%' OR sku ILIKE '%' || $4 || '%'
               OR barcode ILIKE '%' || $4 || '%')
        ORDER BY server_version`, [branchId, q.updated_since ?? null, q.include_deleted, q.search ?? null]);
        return { data: rows.map(productJson) };
    });
    app.post("/branches/:id/products", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const branchId = uuid.parse(req.params.id);
        const body = parse(productCreate, req.body);
        await assertBranch(deps, branchId);
        if (body.category_id) {
            const cat = await db.query(`SELECT 1 FROM category WHERE id = $1 AND branch_id = $2 AND deleted_at IS NULL`, [
                body.category_id,
                branchId,
            ]);
            if (!cat.rowCount)
                throw unprocessable("CATEGORY_INVALID", "category_id must belong to this branch");
        }
        const id = body.id ?? newId();
        const createdAt = parseTimestamp(body.created_at) ?? deps.clock();
        try {
            const { rows } = await db.query(`INSERT INTO product (id, branch_id, category_id, name, sku, barcode, price_cents, tax_rate_bp,
                              cost_price_cents, stock_quantity, image_url, is_active,
                              created_at, updated_at, server_version)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $13, nextval('server_version_seq'))
         RETURNING ${PRODUCT_COLUMNS}`, [
                id,
                branchId,
                body.category_id ?? null,
                body.name,
                body.sku ?? null,
                body.barcode ?? null,
                body.price_cents,
                body.tax_rate_bp,
                body.cost_price_cents ?? null,
                body.stock_quantity,
                body.image_url ?? null,
                body.is_active,
                createdAt,
            ]);
            await writeAudit(db, {
                actorType: "USER",
                actorId: claims.sub,
                action: "product.create",
                entity: "product",
                entityId: id,
                ip: req.ip,
            });
            reply.status(201).send(productJson(rows[0]));
        }
        catch (err) {
            const constraint = uniqueViolation(err);
            if (constraint === "product_sku_uq")
                throw conflict("SKU_TAKEN", "Another product uses that SKU");
            if (constraint === "product_barcode_uq")
                throw conflict("BARCODE_TAKEN", "Another product uses that barcode");
            throw err;
        }
    });
    app.patch("/products/:pid", async (req) => {
        const claims = await guards.requireOwner(req);
        const pid = uuid.parse(req.params.pid);
        const body = parse(productPatch, req.body);
        if ("id" in body || "branch_id" in body || "created_at" in body) {
            throw conflict("IMMUTABLE_FIELD", "id, branch_id, and created_at cannot be changed");
        }
        if (body.stock_quantity !== undefined) {
            await writeAudit(db, {
                actorType: "USER",
                actorId: claims.sub,
                action: "product.stock_write_rejected",
                entity: "product",
                entityId: pid,
                details: { attempted: body.stock_quantity },
                ip: req.ip,
            });
            throw conflict("USE_RESTOCK_ENDPOINT", "Stock changes must go through POST /products/{pid}/restock");
        }
        const existing = await db.query(`SELECT branch_id FROM product WHERE id = $1 AND deleted_at IS NULL`, [pid]);
        if (!existing.rows[0])
            throw notFound("PRODUCT_NOT_FOUND", "Product does not exist");
        scopeBranch(claims, existing.rows[0].branch_id);
        if (body.category_id) {
            const cat = await db.query(`SELECT 1 FROM category WHERE id = $1 AND deleted_at IS NULL`, [body.category_id]);
            if (!cat.rowCount)
                throw unprocessable("CATEGORY_INVALID", "category_id does not exist");
        }
        try {
            const { rows } = await db.query(`UPDATE product SET
           name = COALESCE($2, name),
           category_id = CASE WHEN $3::boolean THEN $4 ELSE category_id END,
           sku = CASE WHEN $5::boolean THEN $6 ELSE sku END,
           barcode = CASE WHEN $7::boolean THEN $8 ELSE barcode END,
           price_cents = COALESCE($9, price_cents),
           tax_rate_bp = COALESCE($10, tax_rate_bp),
           cost_price_cents = CASE WHEN $11::boolean THEN $12 ELSE cost_price_cents END,
           image_url = CASE WHEN $13::boolean THEN $14 ELSE image_url END,
           is_active = COALESCE($15, is_active),
           updated_at = $16,
           server_updated_at = now(),
           server_version = nextval('server_version_seq')
         WHERE id = $1 AND deleted_at IS NULL
         RETURNING ${PRODUCT_COLUMNS}`, [
                pid,
                body.name ?? null,
                "category_id" in body,
                body.category_id ?? null,
                "sku" in body,
                body.sku ?? null,
                "barcode" in body,
                body.barcode ?? null,
                body.price_cents ?? null,
                body.tax_rate_bp ?? null,
                "cost_price_cents" in body,
                body.cost_price_cents ?? null,
                "image_url" in body,
                body.image_url ?? null,
                body.is_active ?? null,
                deps.clock(),
            ]);
            await writeAudit(db, {
                actorType: "USER",
                actorId: claims.sub,
                action: "product.update",
                entity: "product",
                entityId: pid,
                details: body,
                ip: req.ip,
            });
            return productJson(rows[0]);
        }
        catch (err) {
            const constraint = uniqueViolation(err);
            if (constraint === "product_sku_uq")
                throw conflict("SKU_TAKEN", "Another product uses that SKU");
            if (constraint === "product_barcode_uq")
                throw conflict("BARCODE_TAKEN", "Another product uses that barcode");
            throw err;
        }
    });
    app.post("/products/:pid/restock", async (req) => {
        const claims = await guards.requireOwner(req);
        const pid = uuid.parse(req.params.pid);
        const body = parse(restockBody, req.body);
        const existing = await db.query(`SELECT branch_id FROM product WHERE id = $1 AND deleted_at IS NULL`, [pid]);
        if (!existing.rows[0])
            throw notFound("PRODUCT_NOT_FOUND", "Product does not exist");
        scopeBranch(claims, existing.rows[0].branch_id);
        const { rows } = await db.query(`UPDATE product SET stock_quantity = $2, updated_at = $3, server_updated_at = now(),
              server_version = nextval('server_version_seq')
        WHERE id = $1 AND deleted_at IS NULL RETURNING ${PRODUCT_COLUMNS}`, [pid, body.stock_quantity, deps.clock()]);
        await writeAudit(db, {
            actorType: "USER",
            actorId: claims.sub,
            action: "product.restock",
            entity: "product",
            entityId: pid,
            details: { stock_quantity: body.stock_quantity, reason: body.reason ?? null },
            ip: req.ip,
        });
        return productJson(rows[0]);
    });
    app.delete("/products/:pid", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const pid = uuid.parse(req.params.pid);
        const existing = await db.query(`SELECT branch_id FROM product WHERE id = $1 AND deleted_at IS NULL`, [pid]);
        if (!existing.rows[0])
            throw notFound("PRODUCT_NOT_FOUND", "Product does not exist");
        scopeBranch(claims, existing.rows[0].branch_id);
        await db.tx(async (q) => {
            await q.query(`UPDATE product SET deleted_at = $2, updated_at = $2, server_updated_at = now(),
                server_version = nextval('server_version_seq') WHERE id = $1`, [pid, deps.clock()]);
            await writeAudit(q, {
                actorType: "USER",
                actorId: claims.sub,
                action: "product.delete",
                entity: "product",
                entityId: pid,
                ip: req.ip,
            });
        });
        reply.status(204).send();
    });
    // ----------------------------------------------------------------- devices
    app.post("/branches/:id/devices", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const branchId = uuid.parse(req.params.id);
        const body = parse(deviceCreate, req.body);
        await assertBranch(deps, branchId);
        const id = newId();
        const token = newOpaqueToken("dvc_");
        // device_code is 4 chars, so retry the (rare) collision a few times.
        let code = "";
        for (let attempt = 0; attempt < 10; attempt++) {
            code = newDeviceCode();
            try {
                await db.query(`INSERT INTO device (id, branch_id, name, device_code, token_hash, token_expires_at)
           VALUES ($1, $2, $3, $4, $5, $6)`, [
                    id,
                    branchId,
                    body.name,
                    code,
                    sha256Hex(token),
                    addSeconds(deps.clock(), deps.config.DEVICE_TOKEN_TTL_SEC),
                ]);
                break;
            }
            catch (err) {
                if (uniqueViolation(err) !== "device_code_key" || attempt === 9)
                    throw err;
            }
        }
        await writeAudit(db, {
            actorType: "USER",
            actorId: claims.sub,
            action: "device.create",
            entity: "device",
            entityId: id,
            details: { branch_id: branchId, name: body.name },
            ip: req.ip,
        });
        reply.status(201).send({ device_id: id, device_code: code, device_token: token, branch_id: branchId });
    });
    app.get("/branches/:id/devices", async (req) => {
        const claims = await guards.requireAdmin(req);
        const branchId = uuid.parse(req.params.id);
        scopeBranch(claims, branchId);
        const { rows } = await db.query(`SELECT id, name, device_code, is_revoked, last_seen_at, created_at
         FROM device WHERE branch_id = $1 ORDER BY created_at`, [branchId]);
        return {
            data: rows.map((r) => ({
                device_id: r.id,
                name: r.name,
                device_code: r.device_code,
                is_revoked: r.is_revoked,
                last_seen_at: toIso(r.last_seen_at),
                created_at: toIso(r.created_at),
            })),
        };
    });
    app.delete("/devices/:device_id", async (req, reply) => {
        const claims = await guards.requireOwner(req);
        const deviceId = uuid.parse(req.params.device_id);
        const { rows } = await db.query(`SELECT branch_id FROM device WHERE id = $1`, [deviceId]);
        const device = rows[0];
        if (!device)
            throw notFound("DEVICE_NOT_FOUND", "Device does not exist");
        scopeBranch(claims, device.branch_id);
        await db.query(`UPDATE device SET is_revoked = TRUE, revoked_at = now() WHERE id = $1 AND NOT is_revoked`, [
            deviceId,
        ]);
        await writeAudit(db, {
            actorType: "USER",
            actorId: claims.sub,
            action: "device.revoke",
            entity: "device",
            entityId: deviceId,
            ip: req.ip,
        });
        reply.status(204).send();
    });
    // ----------------------------------------------------------- device ops
    const heartbeatBody = z.object({
        app_version: z.string().trim().max(40).optional(),
        outbox_pending: z.number().int().min(0).default(0),
    });
    app.post("/devices/heartbeat", async (req) => {
        const device = await guards.requireDevice(req);
        const body = parse(heartbeatBody, req.body ?? {});
        await db.query(`UPDATE device SET last_seen_at = now(), app_version = COALESCE($2, app_version) WHERE id = $1`, [device.id, body.app_version ?? null]);
        return {
            server_time: deps.clock().toISOString(),
            config: {
                stk_timeout_minutes: deps.config.STK_TIMEOUT_MINUTES,
                low_stock_threshold: LOW_STOCK_THRESHOLD,
                sync_push_max_records: 200,
                sync_pull_page_size: 1000,
            },
        };
    });
    // ----------------------------------------------------------- sales read
    app.get("/branches/:id/sales", async (req) => {
        const claims = await guards.requireAdmin(req);
        const branchId = uuid.parse(req.params.id);
        scopeBranch(claims, branchId);
        const q = salesQuery.parse(req.query ?? {});
        let cursorCreated = null;
        let cursorId = null;
        if (q.cursor) {
            try {
                const decoded = JSON.parse(Buffer.from(q.cursor, "base64url").toString("utf8"));
                cursorCreated = decoded.created_at;
                cursorId = decoded.id;
            }
            catch {
                throw badRequest("CURSOR_INVALID", "cursor is not a valid pagination token");
            }
        }
        const { rows } = await db.query(`SELECT id, receipt_number, cashier_name, payment_method, total_amount_cents,
              payment_status, mpesa_receipt, created_at
         FROM sale
        WHERE branch_id = $1
          AND ($2::timestamptz IS NULL OR created_at >= $2)
          AND ($3::timestamptz IS NULL OR created_at < $3)
          AND ($4::text IS NULL OR payment_status = $4)
          AND ($5::timestamptz IS NULL OR (created_at, id) < ($5, $6::uuid))
        ORDER BY created_at DESC, id DESC
        LIMIT $7`, [
            branchId,
            parseTimestamp(q.from) ?? null,
            parseTimestamp(q.to) ?? null,
            q.payment_status ?? null,
            cursorCreated,
            cursorId,
            q.limit,
        ]);
        const last = rows[rows.length - 1];
        const next_cursor = rows.length === q.limit && last
            ? Buffer.from(JSON.stringify({ created_at: toIso(last.created_at), id: last.id })).toString("base64url")
            : null;
        return {
            data: rows.map((r) => ({
                id: r.id,
                receipt_number: r.receipt_number,
                cashier_name: r.cashier_name,
                payment_method: r.payment_method,
                total_amount_cents: Number(r.total_amount_cents),
                payment_status: r.payment_status,
                mpesa_receipt: r.mpesa_receipt,
                created_at: toIso(r.created_at),
            })),
            next_cursor,
        };
    });
}
//# sourceMappingURL=catalog.js.map