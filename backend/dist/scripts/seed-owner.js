import pg from "pg";
import { hash as argonHash } from "@node-rs/argon2";
import { loadConfig } from "../config.js";
import { newId } from "../lib/ids.js";
/**
 * Creates (or refreshes) a starting OWNER account.
 *
 * Defaults are for local/demo use only: admin@orange.local / admin1234.
 * Override with SEED_ADMIN_EMAIL, SEED_ADMIN_PASSWORD, SEED_ADMIN_NAME.
 * Safe to re-run: an active account with the same email is updated in place.
 */
const DEFAULT_EMAIL = "admin@orange.local";
const DEFAULT_PASSWORD = "admin1234";
const DEFAULT_NAME = "Admin";
async function main() {
    const email = (process.env.SEED_ADMIN_EMAIL ?? DEFAULT_EMAIL).trim().toLowerCase();
    const password = process.env.SEED_ADMIN_PASSWORD ?? DEFAULT_PASSWORD;
    const name = (process.env.SEED_ADMIN_NAME ?? DEFAULT_NAME).trim() || DEFAULT_NAME;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        throw new Error(`SEED_ADMIN_EMAIL is not a valid email: ${email}`);
    }
    if (password.length < 8) {
        throw new Error("SEED_ADMIN_PASSWORD must be at least 8 characters");
    }
    const config = loadConfig();
    const client = new pg.Client({ connectionString: config.DATABASE_URL });
    await client.connect();
    try {
        const passwordHash = await argonHash(password);
        const existing = await client.query(`SELECT id FROM app_user WHERE lower(email) = $1 AND deleted_at IS NULL`, [email]);
        if (existing.rows[0]) {
            await client.query(`UPDATE app_user
            SET name = $2, password_hash = $3, role = 'OWNER', is_active = TRUE, updated_at = now()
          WHERE id = $1`, [existing.rows[0].id, name, passwordHash]);
            console.log(`updated owner ${email} (${existing.rows[0].id})`);
        }
        else {
            const id = newId();
            await client.query(`INSERT INTO app_user (id, name, email, password_hash, role)
         VALUES ($1, $2, $3, $4, 'OWNER')`, [id, name, email, passwordHash]);
            console.log(`created owner ${email} (${id})`);
        }
        console.log("done — sign in with the seeded email and password");
    }
    finally {
        await client.end();
    }
}
main().catch((err) => {
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
});
//# sourceMappingURL=seed-owner.js.map