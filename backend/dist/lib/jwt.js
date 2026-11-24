import { SignJWT, jwtVerify } from "jose";
import { unauthorized } from "./errors.js";
export class TokenService {
    issuer;
    accessTtlSec;
    key;
    constructor(secret, issuer, accessTtlSec) {
        this.issuer = issuer;
        this.accessTtlSec = accessTtlSec;
        this.key = new TextEncoder().encode(secret);
    }
    async signAccess(claims) {
        const now = Math.floor(Date.now() / 1000);
        return new SignJWT({ role: claims.role, branch_ids: claims.branch_ids, typ: "access" })
            .setProtectedHeader({ alg: "HS256", typ: "JWT" })
            .setSubject(claims.sub)
            .setIssuer(this.issuer)
            .setIssuedAt(now)
            .setExpirationTime(now + this.accessTtlSec)
            .sign(this.key);
    }
    async verifyAccess(token) {
        try {
            const { payload } = await jwtVerify(token, this.key, {
                issuer: this.issuer,
                algorithms: ["HS256"],
            });
            if (payload.typ !== "access" || typeof payload.sub !== "string") {
                throw new Error("not an access token");
            }
            const role = payload.role;
            if (role !== "OWNER" && role !== "MANAGER")
                throw new Error("bad role");
            const branchIds = Array.isArray(payload.branch_ids)
                ? payload.branch_ids.filter((v) => typeof v === "string")
                : [];
            return { sub: payload.sub, role, branch_ids: branchIds };
        }
        catch {
            throw unauthorized("TOKEN_INVALID", "Access token is missing, expired, or invalid");
        }
    }
}
//# sourceMappingURL=jwt.js.map