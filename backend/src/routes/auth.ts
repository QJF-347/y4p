import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { hash as argonHash, verify as argonVerify } from "@node-rs/argon2";
import { parse, type Deps, type Guards } from "../app.js";
import { writeAudit } from "../lib/audit.js";
import { badRequest, conflict, unauthorized, unprocessable } from "../lib/errors.js";
import { newId, newOpaqueToken, sha256Hex } from "../lib/ids.js";
import type { Role } from "../lib/jwt.js";
import { addSeconds } from "../lib/time.js";

const emailSchema = z.string().trim().toLowerCase().email().max(320);
const passwordSchema = z.string().min(8).max(200);

const loginBody = z.object({ email: emailSchema, password: z.string().min(1) });
const refreshBody = z.object({ refresh_token: z.string().min(20) });
const deleteBody = z.object({ password: z.string().min(1) });
const createUserBody = z.object({
  name: z.string().trim().min(1).max(120),
  email: emailSchema,
  password: passwordSchema,
  role: z.enum(["OWNER", "MANAGER"]),
  branch_ids: z.array(z.string().uuid()).default([]),
});

interface UserRow {
  id: string;
  name: string;
  email: string;
  password_hash: string;
  role: Role;
  is_active: boolean;
  deleted_at: Date | null;
}

async function branchIdsFor(deps: Deps, userId: string, role: Role): Promise<string[]> {
  if (role === "OWNER") {
    const { rows } = await deps.db.query<{ id: string }>(
      `SELECT id FROM branch WHERE deleted_at IS NULL ORDER BY created_at`,
    );
    return rows.map((r) => r.id);
  }
  const { rows } = await deps.db.query<{ branch_id: string }>(
    `SELECT branch_id FROM app_user_branch WHERE user_id = $1 ORDER BY branch_id`,
    [userId],
  );
  return rows.map((r) => r.branch_id);
}

async function issueSession(
  deps: Deps,
  user: UserRow,
  req: FastifyRequest,
  familyId: string = newId(),
): Promise<{ access_token: string; refresh_token: string }> {
  const branchIds = await branchIdsFor(deps, user.id, user.role);
  const access_token = await deps.tokens.signAccess({
    sub: user.id,
    role: user.role,
    branch_ids: branchIds,
  });
  const refresh = newOpaqueToken("rt_");
  await deps.db.query(
    `INSERT INTO refresh_token (id, user_id, family_id, token_hash, expires_at, user_agent, ip)
     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
    [
      newId(),
      user.id,
      familyId,
      sha256Hex(refresh),
      addSeconds(deps.clock(), deps.config.REFRESH_TOKEN_TTL_SEC),
      req.headers["user-agent"] ?? null,
      req.ip ?? null,
    ],
  );
  return { access_token, refresh_token: refresh };
}

async function publicUser(deps: Deps, user: UserRow) {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    role: user.role,
    branch_ids: await branchIdsFor(deps, user.id, user.role),
  };
}

export function registerAuthRoutes(app: FastifyInstance, deps: Deps, guards: Guards): void {
  const { db, config } = deps;

  app.post("/auth/login", async (req, reply) => {
    if (!deps.limiter.hit(`login:${req.ip}`, 10, 60_000)) {
      return reply.status(429).send({
        error: { code: "RATE_LIMITED", message: "Too many login attempts; try again later" },
      });
    }
    const body = parse(loginBody, req.body);
    const { rows } = await db.query<UserRow>(
      `SELECT id, name, email, password_hash, role, is_active, deleted_at
         FROM app_user WHERE lower(email) = $1 AND deleted_at IS NULL`,
      [body.email],
    );
    const user = rows[0];
    // Constant-ish work whether or not the account exists, to avoid a user oracle.
    const ok = user
      ? await argonVerify(user.password_hash, body.password)
      : await argonVerify(
          "$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHR2YWx1ZQ$ZmFrZWhhc2hmb3JtaXNzaW5ndXNlcg",
          body.password,
        ).catch(() => false);
    if (!user || !ok || !user.is_active) {
      throw unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    const session = await issueSession(deps, user, req);
    await writeAudit(db, {
      actorType: "USER",
      actorId: user.id,
      action: "auth.login",
      entity: "app_user",
      entityId: user.id,
      ip: req.ip,
    });
    return { ...session, user: await publicUser(deps, user) };
  });

  app.post("/auth/refresh", async (req, reply) => {
    const body = parse(refreshBody, req.body);
    const hash = sha256Hex(body.refresh_token);
    const { rows } = await db.query<{
      id: string;
      user_id: string;
      family_id: string;
      expires_at: Date;
      rotated_at: Date | null;
      revoked_at: Date | null;
    }>(
      `SELECT id, user_id, family_id, expires_at, rotated_at, revoked_at
         FROM refresh_token WHERE token_hash = $1`,
      [hash],
    );
    const token = rows[0];
    if (!token) throw unauthorized("REFRESH_TOKEN_INVALID", "Refresh token is not recognised");

    if (token.revoked_at || token.rotated_at) {
      // Reuse of a rotated/revoked token: assume theft and revoke the whole family.
      await db.query(`UPDATE refresh_token SET revoked_at = now() WHERE family_id = $1 AND revoked_at IS NULL`, [
        token.family_id,
      ]);
      await writeAudit(db, {
        actorType: "SYSTEM",
        actorId: "auth",
        action: "auth.refresh_reuse_detected",
        entity: "refresh_token",
        entityId: token.id,
        details: { family_id: token.family_id, user_id: token.user_id },
        ip: req.ip,
      });
      throw unauthorized(
        "REFRESH_TOKEN_REUSED",
        "Refresh token was already used; all sessions in this family were revoked",
      );
    }
    if (token.expires_at.getTime() <= deps.clock().getTime()) {
      throw unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired; log in again");
    }

    const { rows: userRows } = await db.query<UserRow>(
      `SELECT id, name, email, password_hash, role, is_active, deleted_at
         FROM app_user WHERE id = $1 AND deleted_at IS NULL`,
      [token.user_id],
    );
    const user = userRows[0];
    if (!user || !user.is_active) throw unauthorized("USER_DISABLED", "This account is no longer active");

    const next = newOpaqueToken("rt_");
    const nextId = newId();
    await db.tx(async (q) => {
      await q.query(
        `UPDATE refresh_token SET rotated_at = now(), revoked_at = now(), replaced_by = $2 WHERE id = $1`,
        [token.id, nextId],
      );
      await q.query(
        `INSERT INTO refresh_token (id, user_id, family_id, token_hash, expires_at, user_agent, ip)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [
          nextId,
          user.id,
          token.family_id,
          sha256Hex(next),
          addSeconds(deps.clock(), config.REFRESH_TOKEN_TTL_SEC),
          req.headers["user-agent"] ?? null,
          req.ip ?? null,
        ],
      );
    });

    const branchIds = await branchIdsFor(deps, user.id, user.role);
    return {
      access_token: await deps.tokens.signAccess({ sub: user.id, role: user.role, branch_ids: branchIds }),
      refresh_token: next,
      user: await publicUser(deps, user),
    };
  });

  app.post("/auth/logout", async (req, reply) => {
    const claims = await guards.requireAdmin(req);
    const body = parse(refreshBody, req.body);
    const { rows } = await db.query<{ family_id: string; user_id: string }>(
      `SELECT family_id, user_id FROM refresh_token WHERE token_hash = $1`,
      [sha256Hex(body.refresh_token)],
    );
    const token = rows[0];
    if (token && token.user_id === claims.sub) {
      await db.query(`UPDATE refresh_token SET revoked_at = now() WHERE family_id = $1 AND revoked_at IS NULL`, [
        token.family_id,
      ]);
    }
    reply.status(204).send();
  });

  app.get("/auth/me", async (req) => {
    const claims = await guards.requireAdmin(req);
    const { rows } = await db.query<UserRow>(
      `SELECT id, name, email, password_hash, role, is_active, deleted_at
         FROM app_user WHERE id = $1 AND deleted_at IS NULL`,
      [claims.sub],
    );
    const user = rows[0];
    if (!user) throw unauthorized("USER_NOT_FOUND", "Account no longer exists");
    return { user: await publicUser(deps, user) };
  });

  app.delete("/auth/account", async (req, reply) => {
    const claims = await guards.requireAdmin(req);
    const body = parse(deleteBody, req.body);
    const { rows } = await db.query<UserRow>(
      `SELECT id, name, email, password_hash, role, is_active, deleted_at
         FROM app_user WHERE id = $1 AND deleted_at IS NULL`,
      [claims.sub],
    );
    const user = rows[0];
    if (!user) throw unauthorized("USER_NOT_FOUND", "Account no longer exists");
    if (!(await argonVerify(user.password_hash, body.password))) {
      throw unauthorized("INVALID_CREDENTIALS", "Password is incorrect");
    }
    await db.tx(async (q) => {
      await q.query(`UPDATE app_user SET deleted_at = now(), is_active = FALSE WHERE id = $1`, [user.id]);
      await q.query(`UPDATE refresh_token SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`, [
        user.id,
      ]);
      await writeAudit(q, {
        actorType: "USER",
        actorId: user.id,
        action: "auth.account_deleted",
        entity: "app_user",
        entityId: user.id,
        ip: req.ip,
      });
    });
    reply.status(204).send();
  });

  // Register stays invite-only: an OWNER creates accounts (API_CONTRACT §1.1).
  app.post("/auth/users", async (req, reply) => {
    const claims = await guards.requireOwner(req);
    const body = parse(createUserBody, req.body);
    if (body.role === "MANAGER" && body.branch_ids.length === 0) {
      throw unprocessable("BRANCH_REQUIRED", "A MANAGER must be assigned at least one branch");
    }
    if (body.branch_ids.length > 0) {
      const { rows } = await db.query<{ id: string }>(
        `SELECT id FROM branch WHERE id = ANY($1::uuid[]) AND deleted_at IS NULL`,
        [body.branch_ids],
      );
      if (rows.length !== new Set(body.branch_ids).size) {
        throw unprocessable("BRANCH_NOT_FOUND", "One or more branch_ids do not exist");
      }
    }
    const passwordHash = await argonHash(body.password);
    const id = newId();
    try {
      await db.tx(async (q) => {
        await q.query(
          `INSERT INTO app_user (id, name, email, password_hash, role)
           VALUES ($1, $2, $3, $4, $5)`,
          [id, body.name, body.email, passwordHash, body.role],
        );
        for (const branchId of new Set(body.branch_ids)) {
          await q.query(`INSERT INTO app_user_branch (user_id, branch_id) VALUES ($1, $2)`, [id, branchId]);
        }
        await writeAudit(q, {
          actorType: "USER",
          actorId: claims.sub,
          action: "auth.user_created",
          entity: "app_user",
          entityId: id,
          details: { role: body.role, branch_ids: body.branch_ids },
          ip: req.ip,
        });
      });
    } catch (err) {
      if ((err as { code?: string }).code === "23505") {
        throw conflict("EMAIL_TAKEN", "An account with that email already exists");
      }
      throw err;
    }
    const created = await db.query<UserRow>(
      `SELECT id, name, email, password_hash, role, is_active, deleted_at FROM app_user WHERE id = $1`,
      [id],
    );
    reply.status(201).send({ user: await publicUser(deps, created.rows[0]!) });
  });
}
