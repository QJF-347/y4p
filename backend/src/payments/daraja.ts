import type { Config } from "../config.js";
import { badGateway, unprocessable } from "../lib/errors.js";

export interface StkPushRequest {
  /** E.164, e.g. +254712345678 */
  phone: string;
  /** Whole KES — Daraja takes integer shillings (API_CONTRACT §4.1). */
  amountKes: number;
  accountReference: string;
  transactionDesc: string;
}

export interface StkPushResult {
  merchantRequestId: string;
  checkoutRequestId: string;
  customerMessage: string;
}

export class DarajaError extends Error {}

export class DarajaClient {
  private cachedToken: { value: string; expiresAt: number } | null = null;

  constructor(private readonly config: Config) {}

  private timestamp(): { stamp: string; password: string } {
    // Daraja expects Nairobi date-time YYYYMMDDHHmmss; Kenya is UTC+3 with no DST.
    const nairobi = new Date(Date.now() + 3 * 3600 * 1000);
    const stamp = nairobi.toISOString().replace(/[-:T]/g, "").slice(0, 14);
    const password = Buffer.from(
      `${this.config.DARAJA_SHORTCODE}${this.config.DARAJA_PASSKEY}${stamp}`,
    ).toString("base64");
    return { stamp, password };
  }

  private async accessToken(): Promise<string> {
    if (this.cachedToken && this.cachedToken.expiresAt > Date.now() + 30_000) {
      return this.cachedToken.value;
    }
    const basic = Buffer.from(
      `${this.config.DARAJA_CONSUMER_KEY}:${this.config.DARAJA_CONSUMER_SECRET}`,
    ).toString("base64");
    let res: Response;
    try {
      res = await fetch(
        `${this.config.DARAJA_BASE_URL}/oauth/v1/generate?grant_type=client_credentials`,
        { headers: { authorization: `Basic ${basic}` }, signal: AbortSignal.timeout(15_000) },
      );
    } catch {
      throw badGateway("DARAJA_UNAVAILABLE", "Could not reach Daraja to request an access token");
    }
    if (!res.ok) throw badGateway("DARAJA_UNAVAILABLE", `Daraja auth failed (HTTP ${res.status})`);
    const body = (await res.json()) as { access_token?: string; expires_in?: string };
    if (!body.access_token) throw badGateway("DARAJA_UNAVAILABLE", "Daraja auth returned no token");
    const ttl = Number(body.expires_in ?? "3599");
    this.cachedToken = {
      value: body.access_token,
      expiresAt: Date.now() + (Number.isFinite(ttl) ? ttl : 3599) * 1000,
    };
    return body.access_token;
  }

  async stkPush(req: StkPushRequest): Promise<StkPushResult> {
    if (this.config.DARAJA_FAKE) {
      const suffix = Math.random().toString(36).slice(2, 10).toUpperCase();
      return {
        merchantRequestId: `FAKE-MER-${suffix}`,
        checkoutRequestId: `ws_CO_FAKE_${suffix}`,
        customerMessage: "Success. Request accepted for processing (fake mode)",
      };
    }

    const token = await this.accessToken();
    const { stamp, password } = this.timestamp();
    let res: Response;
    try {
      res = await fetch(`${this.config.DARAJA_BASE_URL}/mpesa/stkpush/v1/processrequest`, {
        method: "POST",
        headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
        signal: AbortSignal.timeout(20_000),
        body: JSON.stringify({
          BusinessShortCode: this.config.DARAJA_SHORTCODE,
          Password: password,
          Timestamp: stamp,
          TransactionType: "CustomerPayBillOnline",
          Amount: req.amountKes,
          PartyA: req.phone.replace("+", ""),
          PartyB: this.config.DARAJA_SHORTCODE,
          PhoneNumber: req.phone.replace("+", ""),
          CallBackURL: this.config.DARAJA_CALLBACK_URL,
          AccountReference: req.accountReference.slice(0, 12),
          TransactionDesc: req.transactionDesc.slice(0, 13),
        }),
      });
    } catch {
      throw badGateway("DARAJA_UNAVAILABLE", "Daraja STK push request failed to send");
    }

    const body = (await res.json().catch(() => ({}))) as {
      MerchantRequestID?: string;
      CheckoutRequestID?: string;
      ResponseCode?: string;
      ResponseDescription?: string;
      CustomerMessage?: string;
      errorMessage?: string;
    };

    if (!res.ok || body.ResponseCode !== "0" || !body.CheckoutRequestID) {
      const detail = body.errorMessage ?? body.ResponseDescription ?? `HTTP ${res.status}`;
      if (/invalid.*phone|msisdn/i.test(detail)) {
        throw unprocessable("INVALID_MSISDN", "Daraja rejected the phone number");
      }
      throw badGateway("DARAJA_UNAVAILABLE", `Daraja rejected the STK push: ${detail}`);
    }

    return {
      merchantRequestId: body.MerchantRequestID ?? "",
      checkoutRequestId: body.CheckoutRequestID,
      customerMessage: body.CustomerMessage ?? "",
    };
  }
}
