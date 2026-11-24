import { z } from "zod";
const Env = z.object({
    PORT: z.coerce.number().int().positive().default(8080),
    HOST: z.string().default("0.0.0.0"),
    DATABASE_URL: z.string().default("postgres://orange:orange@localhost:5432/orange"),
    LOG_LEVEL: z.string().default("info"),
    JWT_SECRET: z.string().min(32).default("dev-only-secret-change-me-0123456789abcdef"),
    JWT_ISSUER: z.string().default("orange-pos-api"),
    ACCESS_TOKEN_TTL_SEC: z.coerce.number().int().positive().default(15 * 60),
    REFRESH_TOKEN_TTL_SEC: z.coerce.number().int().positive().default(30 * 24 * 3600),
    DEVICE_TOKEN_TTL_SEC: z.coerce.number().int().positive().default(180 * 24 * 3600),
    // Daraja (M-Pesa). Values come from .env; never commit real credentials.
    DARAJA_CONSUMER_KEY: z.string().default(""),
    DARAJA_CONSUMER_SECRET: z.string().default(""),
    DARAJA_SHORTCODE: z.string().default("174379"),
    DARAJA_PASSKEY: z.string().default(""),
    DARAJA_BASE_URL: z.string().default("https://sandbox.safaricom.co.ke"),
    DARAJA_CALLBACK_URL: z.string().default("http://localhost:8080/api/v1/payments/daraja/callback"),
    DARAJA_CALLBACK_SECRET: z.string().default("dev-callback-secret"),
    // For tests / local demos: skip the network call and accept every STK push.
    DARAJA_FAKE: z
        .string()
        .default("0")
        .transform((v) => v === "1" || v.toLowerCase() === "true"),
    STK_TIMEOUT_MINUTES: z.coerce.number().int().positive().default(5),
    RAW_PAYLOAD_DIR: z.string().default("./var/raw-payloads"),
});
export function loadConfig(overrides = {}) {
    return Env.parse({ ...process.env, ...overrides });
}
//# sourceMappingURL=config.js.map