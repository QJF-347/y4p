import Fastify from "fastify";
import { ZodError } from "zod";
import { Db } from "./lib/db.js";
import { ApiError, badRequest, errorBody, unauthorized } from "./lib/errors.js";
import { registerIdempotency } from "./lib/idempotency.js";
import { TokenService } from "./lib/jwt.js";
import { RateLimiter } from "./lib/rate-limit.js";
import { sha256Hex } from "./lib/ids.js";
import { DarajaClient } from "./payments/daraja.js";
import { registerAuthRoutes } from "./routes/auth.js";
import { registerCatalogRoutes } from "./routes/catalog.js";
import { registerStatsRoutes } from "./routes/stats.js";
import { registerSyncRoutes } from "./routes/sync.js";
import { registerPaymentRoutes } from "./routes/payments.js";
import { registerReconciliationRoutes } from "./routes/reconciliation.js";
export function buildDeps(config, db, clock = () => new Date()) {
    return {
        db: db ?? new Db(config.DATABASE_URL),
        config,
        tokens: new TokenService(config.JWT_SECRET, config.JWT_ISSUER, config.ACCESS_TOKEN_TTL_SEC),
        limiter: new RateLimiter(),
        daraja: new DarajaClient(config),
        clock,
    };
}
function bearer(req) {
    const header = req.headers.authorization;
    if (typeof header !== "string" || !header.startsWith("Bearer "))
        return null;
    return header.slice("Bearer ".length).trim() || null;
}
/** Parse and validate a JSON body with zod, returning a 400 envelope on failure. */
export function parse(schema, value) {
    try {
        return schema.parse(value);
    }
    catch (err) {
        if (err instanceof ZodError) {
            throw badRequest("VALIDATION_ERROR", "Request body failed validation", {
                issues: err.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
            });
        }
        throw err;
    }
}
export function createGuards(deps) {
    async function requireAdmin(req) {
        const token = bearer(req);
        if (!token)
            throw unauthorized("TOKEN_MISSING", "Authorization: Bearer <access token> is required");
        return deps.tokens.verifyAccess(token);
    }
    async function requireOwner(req) {
        const claims = await requireAdmin(req);
        if (claims.role !== "OWNER") {
            throw new ApiError(403, "OWNER_ONLY", "This action requires the OWNER role");
        }
        return claims;
    }
    async function requireDevice(req) {
        const token = req.headers["x-device-token"];
        if (typeof token !== "string" || token.length === 0) {
            throw unauthorized("DEVICE_TOKEN_MISSING", "X-Device-Token header is required");
        }
        const { rows } = await deps.db.query(`SELECT id, branch_id, device_code, is_revoked, token_expires_at
         FROM device WHERE token_hash = $1`, [sha256Hex(token)]);
        const row = rows[0];
        if (!row)
            throw unauthorized("DEVICE_TOKEN_INVALID", "Unknown device token");
        if (row.is_revoked)
            throw unauthorized("DEVICE_REVOKED", "This device has been revoked");
        if (row.token_expires_at.getTime() <= deps.clock().getTime()) {
            throw unauthorized("DEVICE_TOKEN_EXPIRED", "Device token has expired; re-provision the device");
        }
        void deps.db
            .query(`UPDATE device SET last_seen_at = now() WHERE id = $1`, [row.id])
            .catch(() => undefined);
        return { id: row.id, branchId: row.branch_id, code: row.device_code };
    }
    return { requireAdmin, requireOwner, requireDevice };
}
export async function buildApp(deps, opts = {}) {
    const app = Fastify({
        logger: opts.logger ?? false,
        bodyLimit: 5 * 1024 * 1024, // API_CONTRACT §0: 5 MB per sync batch
    });
    app.setErrorHandler((err, req, reply) => {
        if (err instanceof ApiError) {
            reply.status(err.statusCode).send(errorBody(err));
            return;
        }
        // Raw schema.parse() calls in routes (params/query) bypass parse(); keep
        // them on the 400 envelope instead of falling through to 500.
        if (err instanceof ZodError) {
            reply.status(400).send(errorBody(badRequest("VALIDATION_ERROR", "Request failed validation", {
                issues: err.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
            })));
            return;
        }
        const maybeStatus = err.statusCode;
        const message = err instanceof Error ? err.message : "Bad request";
        if (typeof maybeStatus === "number" && maybeStatus >= 400 && maybeStatus < 500) {
            reply.status(maybeStatus).send(errorBody(new ApiError(maybeStatus, "BAD_REQUEST", message)));
            return;
        }
        req.log.error({ err }, "unhandled error");
        reply.status(500).send(errorBody(new ApiError(500, "INTERNAL_ERROR", "Unexpected server error")));
    });
    app.setNotFoundHandler((_req, reply) => {
        reply
            .status(404)
            .send(errorBody(new ApiError(404, "NOT_FOUND", "No such endpoint")));
    });
    const guards = createGuards(deps);
    registerIdempotency(app, deps);
    await app.register(async (api) => {
        registerAuthRoutes(api, deps, guards);
        registerCatalogRoutes(api, deps, guards);
        registerStatsRoutes(api, deps, guards);
        registerSyncRoutes(api, deps, guards);
        registerPaymentRoutes(api, deps, guards);
        registerReconciliationRoutes(api, deps, guards);
        api.get("/health", async () => ({ ok: true, time: deps.clock().toISOString() }));
    }, { prefix: "/api/v1" });
    return app;
}
//# sourceMappingURL=app.js.map