import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import pg from "pg";
import { loadConfig } from "../config.js";
const migrationsDir = fileURLToPath(new URL("../../migrations", import.meta.url));
async function main() {
    const config = loadConfig();
    const client = new pg.Client({ connectionString: config.DATABASE_URL });
    await client.connect();
    try {
        await client.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
         name TEXT PRIMARY KEY,
         applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
       )`);
        const files = (await readdir(migrationsDir)).filter((f) => f.endsWith(".sql")).sort();
        for (const file of files) {
            const seen = await client.query(`SELECT 1 FROM schema_migrations WHERE name = $1`, [file]);
            if (seen.rowCount) {
                console.log(`skip  ${file}`);
                continue;
            }
            const sql = await readFile(path.join(migrationsDir, file), "utf8");
            await client.query("BEGIN");
            try {
                await client.query(sql);
                await client.query(`INSERT INTO schema_migrations (name) VALUES ($1)`, [file]);
                await client.query("COMMIT");
                console.log(`apply ${file}`);
            }
            catch (err) {
                await client.query("ROLLBACK");
                throw new Error(`Migration ${file} failed: ${err.message}`, { cause: err });
            }
        }
        console.log("migrations up to date");
    }
    finally {
        await client.end();
    }
}
main().catch((err) => {
    console.error(err);
    process.exit(1);
});
//# sourceMappingURL=migrate.js.map