import { createHash, randomBytes } from "node:crypto";
import { v7 as uuidv7, validate as validateUuid, version as uuidVersion } from "uuid";
/** Client-compatible ID generator: UUIDv7 is time-sortable (DATA_CONTRACT §1). */
export function newId() {
    return uuidv7();
}
export function isUuid(value) {
    return typeof value === "string" && validateUuid(value);
}
export function uuidVersionOf(value) {
    return uuidVersion(value);
}
export function sha256Hex(input) {
    return createHash("sha256").update(input).digest("hex");
}
/** Opaque bearer token (device tokens, refresh tokens). Returned once, stored hashed. */
export function newOpaqueToken(prefix = "") {
    return prefix + randomBytes(32).toString("base64url");
}
const DEVICE_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
/** 4-char A–Z0–9 device code embedded in receipt numbers (API_CONTRACT §1.2). */
export function newDeviceCode() {
    const bytes = randomBytes(4);
    let out = "";
    for (let i = 0; i < 4; i++) {
        out += DEVICE_CODE_ALPHABET[bytes[i] % DEVICE_CODE_ALPHABET.length];
    }
    return out;
}
//# sourceMappingURL=ids.js.map