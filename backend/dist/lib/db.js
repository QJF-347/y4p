import pg from "pg";
export class Db {
    pool;
    constructor(connection) {
        this.pool =
            typeof connection === "string" || connection === undefined
                ? new pg.Pool(typeof connection === "string" ? { connectionString: connection } : {})
                : new pg.Pool(connection);
    }
    query(text, params) {
        return this.pool.query(text, params);
    }
    /** Run `fn` in a single transaction; rolls back on any thrown error. */
    async tx(fn) {
        const client = await this.pool.connect();
        try {
            await client.query("BEGIN");
            const result = await fn({
                query: (text, params) => client.query(text, params),
            });
            await client.query("COMMIT");
            return result;
        }
        catch (err) {
            await client.query("ROLLBACK").catch(() => undefined);
            throw err;
        }
        finally {
            client.release();
        }
    }
    async close() {
        await this.pool.end();
    }
}
//# sourceMappingURL=db.js.map