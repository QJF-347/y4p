import { hash as argonHash } from "@node-rs/argon2";
import { newId } from "../lib/ids.js";
/**
 * Ensures the account named by SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD exists
 * and is an active OWNER. This lets hosts without shell access (e.g. Render's
 * free tier) bootstrap or recover an admin account from dashboard environment
 * variables. The environment values are authoritative: if the account already
 * exists it is promoted to OWNER and its password is reset to the env value,
 * which also fixes accounts left over from earlier setups (e.g. MANAGER role).
 * Without the env vars it does nothing.
 */
export async function maybeBootstrapOwner(db) {
    const email = process.env.SEED_ADMIN_EMAIL?.trim().toLowerCase();
    const password = process.env.SEED_ADMIN_PASSWORD;
    if (!email || !password)
        return "skipped";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        throw new Error(`SEED_ADMIN_EMAIL is not a valid email: ${email}`);
    }
    if (password.length < 8) {
        throw new Error("SEED_ADMIN_PASSWORD must be at least 8 characters");
    }
    const name = (process.env.SEED_ADMIN_NAME ?? "Admin").trim() || "Admin";
    const passwordHash = await argonHash(password);
    const existing = await db.query(`SELECT id FROM app_user WHERE lower(email) = $1 AND deleted_at IS NULL`, [email]);
    if (existing.rows[0]) {
        await db.query(`UPDATE app_user
          SET name = $2, password_hash = $3, role = 'OWNER',
              is_active = TRUE, updated_at = now()
        WHERE id = $1`, [existing.rows[0].id, name, passwordHash]);
        return "updated";
    }
    await db.query(`INSERT INTO app_user (id, name, email, password_hash, role)
     VALUES ($1, $2, $3, $4, 'OWNER')`, [newId(), name, email, passwordHash]);
    return "created";
}
//# sourceMappingURL=bootstrap-owner.js.map