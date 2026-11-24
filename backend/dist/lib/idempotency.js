import { sha256Hex } from "./ids.js";
/**
 * API_CONTRACT §0: every mutating endpoint accepts an optional Idempotency-Key.
 * The same key + actor + endpoint replays the stored response instead of
 * re-running the handler; the same key with a different body is a 422.
 */
const MIN_KEY_LENGTH = 8;
function actorOf(req) {
    const auth = req.headers.authorization;
    if (typeof auth === "string" && auth.startsWith("Bearer "))
        return `user:${sha256Hex(auth.slice(7).trim())}`;
    const device = req.headers["x-device-token"];
    if (typeof device === "string")
        return `device:${sha256Hex(device)}`;
    return `ip:${req.ip}`;
}
function endpointOf(req) {
    return `${req.method} ${req.routeOptions.url ?? req.url.split("?")[0]}`;
}
function requestHashOf(req) {
    return sha256Hex(JSON.stringify({ body: req.body ?? null, query: req.query ?? null }));
}
export function registerIdempotency(app, deps) {
    // Remember which requests were keyed so onSend knows to persist the outcome.
    const keyed = new WeakMap();
    // preValidation runs after body parsing, so the request hash can include the body.
    app.addHook("preValidation", async (req, reply) => {
        if (req.method === "GET" || req.method === "HEAD" || req.method === "OPTIONS")
            return;
        const key = req.headers["idempotency-key"];
        if (typeof key !== "string" || key.length < MIN_KEY_LENGTH)
            return;
        const actor = actorOf(req);
        const endpoint = endpointOf(req);
        const hash = requestHashOf(req);
        keyed.set(req, { key, actor, endpoint, hash });
        const { rows } = await deps.db.query(`SELECT request_hash, status_code, response
         FROM idempotency_key WHERE key = $1 AND actor_id = $2 AND endpoint = $3`, [key, actor, endpoint]);
        const stored = rows[0];
        if (!stored)
            return;
        if (stored.request_hash !== hash) {
            reply
                .status(422)
                .send({ error: { code: "IDEMPOTENCY_KEY_REUSED", message: "This Idempotency-Key was used with a different request" } });
            return reply;
        }
        if (stored.status_code === null)
            return; // Still in flight on another request.
        reply.header("idempotent-replay", "true").status(stored.status_code).send(stored.response);
        return reply;
    });
    app.addHook("onSend", async (req, reply, payload) => {
        const ctx = keyed.get(req);
        if (!ctx)
            return payload;
        // Don't cache server errors or transient outcomes; the client should retry.
        if (reply.statusCode >= 500 || [401, 408, 409, 429].includes(reply.statusCode))
            return payload;
        if (typeof payload !== "string" || payload.length === 0)
            return payload;
        let parsed;
        try {
            parsed = JSON.parse(payload);
        }
        catch {
            return payload;
        }
        await deps.db
            .query(`INSERT INTO idempotency_key (key, actor_id, endpoint, request_hash, status_code, response)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (key, actor_id, endpoint) DO NOTHING`, [ctx.key, ctx.actor, ctx.endpoint, ctx.hash, reply.statusCode, JSON.stringify(parsed)])
            .catch(() => undefined);
        return payload;
    });
}
//# sourceMappingURL=idempotency.js.map