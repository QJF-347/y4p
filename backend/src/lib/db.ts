import pg from "pg";

export interface Queryable {
  query<R extends pg.QueryResultRow = pg.QueryResultRow>(
    text: string,
    params?: readonly unknown[],
  ): Promise<pg.QueryResult<R>>;
}

export class Db implements Queryable {
  readonly pool: pg.Pool;

  constructor(connection?: string | pg.PoolConfig) {
    this.pool =
      typeof connection === "string" || connection === undefined
        ? new pg.Pool(typeof connection === "string" ? { connectionString: connection } : {})
        : new pg.Pool(connection);
  }

  query<R extends pg.QueryResultRow = pg.QueryResultRow>(
    text: string,
    params?: readonly unknown[],
  ): Promise<pg.QueryResult<R>> {
    return this.pool.query<R>(text, params as unknown[] | undefined);
  }

  /** Run `fn` in a single transaction; rolls back on any thrown error. */
  async tx<T>(fn: (q: Queryable) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const result = await fn({
        query: (text, params) => client.query(text, params as unknown[] | undefined),
      });
      await client.query("COMMIT");
      return result;
    } catch (err) {
      await client.query("ROLLBACK").catch(() => undefined);
      throw err;
    } finally {
      client.release();
    }
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}
