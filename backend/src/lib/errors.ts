/**
 * Errors use the API_CONTRACT §0 envelope:
 *   {"error": {"code": "MACHINE_CODE", "message": "...", "details": {...}}}
 * Codes are stable; messages are not.
 */
export class ApiError extends Error {
  constructor(
    readonly statusCode: number,
    readonly code: string,
    message: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const badRequest = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(400, code, message, details);

export const unauthorized = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(401, code, message, details);

export const forbidden = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(403, code, message, details);

export const notFound = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(404, code, message, details);

export const conflict = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(409, code, message, details);

export const unprocessable = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(422, code, message, details);

export const tooManyRequests = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(429, code, message, details);

export const badGateway = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiError(502, code, message, details);

export function errorBody(err: ApiError): {
  error: { code: string; message: string; details?: Record<string, unknown> };
} {
  const error: { code: string; message: string; details?: Record<string, unknown> } = {
    code: err.code,
    message: err.message,
  };
  if (err.details) error.details = err.details;
  return { error };
}
