import { hash as argonHash } from "@node-rs/argon2";
import type { Queryable } from "../lib/db.js";
import { newId } from "../lib/ids.js";

/**
 * Creates the first OWNER on startup when SEED_ADMIN_EMAIL and
 * SEED_ADMIN_PASSWORD are set in the environment. This lets hosts without
 * shell access (e.g. Render's free tier) bootstrap an account from dashboard
 * environment variables. Create-if-missing only: redeploys never reset an
 * existing account's password.
 */
export async function maybeBootstrapOwner(db: Queryable): Promise<"created" | "exists" | "skipped"> {
  const email = process.env.SEED_ADMIN_EMAIL?.trim().toLowerCase();
  const password = process.env.SEED_ADMIN_PASSWORD;
  if (!email || !password) return "skipped";

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error(`SEED_ADMIN_EMAIL is not a valid email: ${email}`);
  }
  if (password.length < 8) {
    throw new Error("SEED_ADMIN_PASSWORD must be at least 8 characters");
  }

  const existing = await db.query<{ id: string }>(
    `SELECT id FROM app_user WHERE lower(email) = $1 AND deleted_at IS NULL`,
    [email],
  );
  if (existing.rows[0]) return "exists";

  const name = (process.env.SEED_ADMIN_NAME ?? "Admin").trim() || "Admin";
  await db.query(
    `INSERT INTO app_user (id, name, email, password_hash, role)
     VALUES ($1, $2, $3, $4, 'OWNER')`,
    [newId(), name, email, await argonHash(password)],
  );
  return "created";
}
