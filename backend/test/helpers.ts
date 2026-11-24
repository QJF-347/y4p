import pg from "pg";
import { hash } from "@node-rs/argon2";
import { buildApp, buildDeps, type Deps } from "../src/app.js";
import { loadConfig, type Config } from "../src/config.js";
import { Db } from "../src/lib/db.js";
import { newId } from "../src/lib/ids.js";

export const DATABASE_URL =
  process.env.TEST_DATABASE_URL ?? "postgres://orange@127.0.0.1:55432/orange_test";

export const pool = new pg.Pool({ connectionString: DATABASE_URL });

const appPools: pg.Pool[] = [];

export async function closePool(): Promise<void> {
  await Promise.all(appPools.map((p) => p.end()));
  await pool.end();
}

export function makeConfig(overrides: Partial<Record<keyof Config, unknown>> = {}): Config {
  return loadConfig({
    DATABASE_URL,
    JWT_SECRET: "test-secret-0123456789abcdefghijklmnop",
    DARAJA_FAKE: "1",
    DARAJA_CALLBACK_SECRET: "test-callback-secret",
    STK_TIMEOUT_MINUTES: 5,
    ...overrides,
  });
}

export async function makeApp(overrides: Partial<Record<keyof Config, unknown>> = {}) {
  const config = makeConfig(overrides);
  const deps = buildDeps(config, new Db(config.DATABASE_URL));
  appPools.push(deps.db.pool);
  const app = await buildApp(deps);
  return { app, deps, config };
}

export async function resetDb(): Promise<void> {
  await pool.query(`TRUNCATE app_user_branch, refresh_token, idempotency_key, audit_log,
    sale_item, sale, payment_transaction, reconciliation_queue, daraja_callback,
    product, category, device, branch, app_user RESTART IDENTITY CASCADE`);
}

export async function seedUser(opts: {
  email?: string;
  password?: string;
  role?: "OWNER" | "MANAGER";
  branchIds?: string[];
  name?: string;
}): Promise<string> {
  const id = newId();
  await pool.query(
    `INSERT INTO app_user (id, name, email, password_hash, role) VALUES ($1, $2, $3, $4, $5)`,
    [id, opts.name ?? "Test User", opts.email ?? "user@example.com", await hash(opts.password ?? "password123"), opts.role ?? "OWNER"],
  );
  for (const branchId of opts.branchIds ?? []) {
    await pool.query(`INSERT INTO app_user_branch (user_id, branch_id) VALUES ($1, $2)`, [id, branchId]);
  }
  return id;
}

export async function seedBranch(name = "Main Branch", code = "MAIN"): Promise<string> {
  const id = newId();
  await pool.query(
    `INSERT INTO branch (id, name, code, created_at, updated_at, server_version)
     VALUES ($1, $2, $3, now(), now(), nextval('server_version_seq'))`,
    [id, name, code],
  );
  return id;
}

export async function login(
  app: Awaited<ReturnType<typeof makeApp>>["app"],
  email = "owner@example.com",
  password = "password123",
): Promise<{ access: string; refresh: string; user: Record<string, unknown> }> {
  const res = await app.inject({
    method: "POST",
    url: "/api/v1/auth/login",
    payload: { email, password },
  });
  if (res.statusCode !== 200) throw new Error(`login failed: ${res.statusCode} ${res.body}`);
  const body = res.json() as { access_token: string; refresh_token: string; user: Record<string, unknown> };
  return { access: body.access_token, refresh: body.refresh_token, user: body.user };
}

export async function provisionDevice(
  app: Awaited<ReturnType<typeof makeApp>>["app"],
  access: string,
  branchId: string,
  name = "Till 1",
): Promise<{ deviceId: string; deviceCode: string; deviceToken: string }> {
  const res = await app.inject({
    method: "POST",
    url: `/api/v1/branches/${branchId}/devices`,
    headers: { authorization: `Bearer ${access}` },
    payload: { name },
  });
  if (res.statusCode !== 201) throw new Error(`device provisioning failed: ${res.statusCode} ${res.body}`);
  const body = res.json() as { device_id: string; device_code: string; device_token: string };
  return { deviceId: body.device_id, deviceCode: body.device_code, deviceToken: body.device_token };
}

export function iso(offsetMs = 0): string {
  return new Date(Date.now() + offsetMs).toISOString();
}

export function productPayload(branchId: string, over: Record<string, unknown> = {}) {
  return {
    id: newId(),
    branch_id: branchId,
    category_id: null,
    name: "Nyama Choma",
    sku: `SKU-${Math.random().toString(36).slice(2, 8).toUpperCase()}`,
    barcode: null,
    price_cents: 50_000,
    tax_rate_bp: 1600,
    cost_price_cents: 30_000,
    stock_quantity: 10,
    image_url: null,
    is_active: true,
    created_at: iso(-60_000),
    updated_at: iso(-30_000),
    deleted_at: null,
    ...over,
  };
}

export function salePayload(
  branchId: string,
  deviceId: string,
  product: { id: string; name?: string; price_cents?: number },
  over: Record<string, unknown> = {},
) {
  const unit = product.price_cents ?? 50_000;
  const quantity = 2;
  return {
    id: newId(),
    receipt_number: `M1-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    branch_id: branchId,
    device_id: deviceId,
    cashier_id: newId(),
    cashier_name: "Amina",
    payment_method: "CASH",
    subtotal_cents: unit * quantity,
    tax_amount_cents: 0,
    total_amount_cents: unit * quantity,
    amount_received_cents: unit * quantity,
    change_amount_cents: 0,
    payment_status: "PAID",
    mpesa_receipt: null,
    created_at: iso(-10_000),
    items: [
      {
        id: newId(),
        product_id: product.id,
        product_name: product.name ?? "Nyama Choma",
        quantity,
        unit_price_cents: unit,
        line_total_cents: unit * quantity,
      },
    ],
    ...over,
  };
}

export async function pushBatch(
  app: Awaited<ReturnType<typeof makeApp>>["app"],
  deviceToken: string,
  payload: Record<string, unknown>,
) {
  return app.inject({
    method: "POST",
    url: "/api/v1/sync/push",
    headers: { "x-device-token": deviceToken },
    payload: { batch_id: newId(), ...payload },
  });
}
