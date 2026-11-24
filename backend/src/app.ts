import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import { ZodError, type ZodType } from "zod";
import type { Config } from "./config.js";
import { Db } from "./lib/db.js";
import { ApiError, badRequest, errorBody, unauthorized } from "./lib/errors.js";
import { registerIdempotency } from "./lib/idempotency.js";
import { TokenService, type AccessClaims } from "./lib/jwt.js";
import { RateLimiter } from "./lib/rate-limit.js";
import { sha256Hex } from "./lib/ids.js";
import { DarajaClient } from "./payments/daraja.js";
import { registerAuthRoutes } from "./routes/auth.js";
import { registerCatalogRoutes } from "./routes/catalog.js";
import { registerStatsRoutes } from "./routes/stats.js";
import { registerSyncRoutes } from "./routes/sync.js";
import { registerPaymentRoutes } from "./routes/payments.js";
import { registerReconciliationRoutes } from "./routes/reconciliation.js";

export interface Deps {
  db: Db;
  config: Config;
  tokens: TokenService;
  limiter: RateLimiter;
  daraja: DarajaClient;
  clock: () => Date;
}

export interface DeviceActor {
  id: string;
  branchId: string;
  code: string;
}

export function buildDeps(config: Config, db?: Db, clock: () => Date = () => new Date()): Deps {
  return {
    db: db ?? new Db(config.DATABASE_URL),
    config,
    tokens: new TokenService(config.JWT_SECRET, config.JWT_ISSUER, config.ACCESS_TOKEN_TTL_SEC),
    limiter: new RateLimiter(),
    daraja: new DarajaClient(config),
    clock,
  };
}

function bearer(req: FastifyRequest): string | null {
  const header = req.headers.authorization;
  if (typeof header !== "string" || !header.startsWith("Bearer ")) return null;
  return header.slice("Bearer ".length).trim() || null;
}

/** Parse and validate a JSON body with zod, returning a 400 envelope on failure. */
export function parse<S extends ZodType>(schema: S, value: unknown): S["_output"] {
  try {
    return schema.parse(value);
  } catch (err) {
    if (err instanceof ZodError) {
      throw badRequest("VALIDATION_ERROR", "Request body failed validation", {
        issues: err.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
      });
    }
    throw err;
  }
}

export function createGuards(deps: Deps) {
  async function requireAdmin(req: FastifyRequest): Promise<AccessClaims> {
    const token = bearer(req);
    if (!token) throw unauthorized("TOKEN_MISSING", "Authorization: Bearer <access token> is required");
    return deps.tokens.verifyAccess(token);
  }

  async function requireOwner(req: FastifyRequest): Promise<AccessClaims> {
    const claims = await requireAdmin(req);
    if (claims.role !== "OWNER") {
      throw new ApiError(403, "OWNER_ONLY", "This action requires the OWNER role");
    }
    return claims;
  }

  async function requireDevice(req: FastifyRequest): Promise<DeviceActor> {
    const token = req.headers["x-device-token"];
    if (typeof token !== "string" || token.length === 0) {
      throw unauthorized("DEVICE_TOKEN_MISSING", "X-Device-Token header is required");
    }
    const { rows } = await deps.db.query<{
      id: string;
      branch_id: string;
      device_code: string;
      is_revoked: boolean;
      token_expires_at: Date;
    }>(
      `SELECT id, branch_id, device_code, is_revoked, token_expires_at
         FROM device WHERE token_hash = $1`,
      [sha256Hex(token)],
    );
    const row = rows[0];
    if (!row) throw unauthorized("DEVICE_TOKEN_INVALID", "Unknown device token");
    if (row.is_revoked) throw unauthorized("DEVICE_REVOKED", "This device has been revoked");
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

export type Guards = ReturnType<typeof createGuards>;

export async function buildApp(deps: Deps, opts: { logger?: boolean } = {}): Promise<FastifyInstance> {
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
      reply.status(400).send(
        errorBody(
          badRequest("VALIDATION_ERROR", "Request failed validation", {
            issues: err.issues.map((i) => ({ path: i.path.join("."), message: i.message })),
          }),
        ),
      );
      return;
    }
    const maybeStatus = (err as { statusCode?: number }).statusCode;
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

  await app.register(
    async (api) => {
      registerAuthRoutes(api, deps, guards);
      registerCatalogRoutes(api, deps, guards);
      registerStatsRoutes(api, deps, guards);
      registerSyncRoutes(api, deps, guards);
      registerPaymentRoutes(api, deps, guards);
      registerReconciliationRoutes(api, deps, guards);

      api.get("/health", async () => ({ ok: true, time: deps.clock().toISOString() }));
    },
    { prefix: "/api/v1" },
  );

  return app;
}
