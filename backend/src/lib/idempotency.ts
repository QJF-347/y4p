import type { FastifyInstance, FastifyRequest } from "fastify";
import { sha256Hex } from "./ids.js";
import type { Deps } from "../app.js";

/**
 * API_CONTRACT §0: every mutating endpoint accepts an optional Idempotency-Key.
 * The same key + actor + endpoint replays the stored response instead of
 * re-running the handler; the same key with a different body is a 422.
 */
const MIN_KEY_LENGTH = 8;

function actorOf(req: FastifyRequest): string {
  const auth = req.headers.authorization;
  if (typeof auth === "string" && auth.startsWith("Bearer ")) return `user:${sha256Hex(auth.slice(7).trim())}`;
  const device = req.headers["x-device-token"];
  if (typeof device === "string") return `device:${sha256Hex(device)}`;
  return `ip:${req.ip}`;
}

function endpointOf(req: FastifyRequest): string {
  return `${req.method} ${req.routeOptions.url ?? req.url.split("?")[0]}`;
}

function requestHashOf(req: FastifyRequest): string {
  return sha256Hex(JSON.stringify({ body: req.body ?? null, query: req.query ?? null }));
}

interface StoredRow {
  request_hash: string;
  status_code: number | null;
  response: unknown;
}

interface KeyedRequest {
  key: string;
  actor: string;
  endpoint: string;
  hash: string;
  /** Set by preSerialization when the outcome is worth caching. */
  cache?: { statusCode: number; payload: unknown };
}

/** Statuses that should not be cached: auth, conflicts, timeouts, rate limits. */
const UNCACHEABLE = new Set([401, 408, 409, 422, 429]);

export function registerIdempotency(app: FastifyInstance, deps: Deps): void {
  // Remember which requests were keyed so we can consult and persist their outcome.
  const keyed = new WeakMap<FastifyRequest, KeyedRequest>();

  // preValidation runs after body parsing, so the request hash can include the body.
  app.addHook("preValidation", async (req, reply) => {
    if (req.method === "GET" || req.method === "HEAD" || req.method === "OPTIONS") return;
    const key = req.headers["idempotency-key"];
    if (typeof key !== "string" || key.length < MIN_KEY_LENGTH) return;

    const actor = actorOf(req);
    const endpoint = endpointOf(req);
    const hash = requestHashOf(req);
    keyed.set(req, { key, actor, endpoint, hash });

    const { rows } = await deps.db.query<StoredRow>(
      `SELECT request_hash, status_code, response
         FROM idempotency_key WHERE key = $1 AND actor_id = $2 AND endpoint = $3`,
      [key, actor, endpoint],
    );
    const stored = rows[0];
    if (!stored) return;
    if (stored.request_hash !== hash) {
      reply
        .status(422)
        .send({ error: { code: "IDEMPOTENCY_KEY_REUSED", message: "This Idempotency-Key was used with a different request" } });
      return reply;
    }
    if (stored.status_code === null) return; // Still in flight on another request.
    reply.header("idempotent-replay", "true").status(stored.status_code).send(stored.response);
    return reply;
  });

  // Stash the outcome here, then persist it in onResponse. These hooks must stay
  // synchronous: awaiting inside the send lifecycle keeps `reply.sent` false, and
  // an async handler that already called reply.send() then resolves to undefined,
  // which makes Fastify send a second (empty) response.
  app.addHook("preSerialization", (req, reply, payload, done) => {
    const ctx = keyed.get(req);
    // Don't cache server errors or transient outcomes; the client should retry.
    if (
      ctx &&
      reply.statusCode < 500 &&
      !UNCACHEABLE.has(reply.statusCode) &&
      typeof payload === "object" &&
      payload !== null
    ) {
      ctx.cache = { statusCode: reply.statusCode, payload };
    }
    // Sync hooks advance the pipeline through the callback, not a return value.
    done(null, payload);
  });

  app.addHook("onResponse", (req) => {
    const ctx = keyed.get(req);
    if (!ctx?.cache) return;
    // The response is already sent; a failed cache write should not surface.
    void deps.db
      .query(
        `INSERT INTO idempotency_key (key, actor_id, endpoint, request_hash, status_code, response)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (key, actor_id, endpoint) DO NOTHING`,
        [ctx.key, ctx.actor, ctx.endpoint, ctx.hash, ctx.cache.statusCode, JSON.stringify(ctx.cache.payload)],
      )
      .catch(() => undefined);
  });
}
