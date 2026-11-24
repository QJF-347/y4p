import { buildApp, buildDeps } from "./app.js";
import { loadConfig } from "./config.js";
import { expirePendingStk } from "./routes/payments.js";

const config = loadConfig();
const deps = buildDeps(config);
const app = await buildApp(deps, { logger: true });

// Fail PENDING STK attempts a little past their Daraja timeout (API_CONTRACT §4.4).
const sweepEveryMs = 30_000;
const sweeper = setInterval(() => {
  expirePendingStk(deps)
    .then((n) => {
      if (n > 0) app.log.info({ expired: n }, "expired stale STK attempts");
    })
    .catch((err) => app.log.error({ err }, "STK timeout sweep failed"));
}, sweepEveryMs);
sweeper.unref();

async function shutdown(signal: string): Promise<void> {
  app.log.info({ signal }, "shutting down");
  clearInterval(sweeper);
  await app.close();
  await deps.db.close();
  process.exit(0);
}

process.on("SIGINT", () => void shutdown("SIGINT"));
process.on("SIGTERM", () => void shutdown("SIGTERM"));

await app.listen({ port: config.PORT, host: config.HOST });
