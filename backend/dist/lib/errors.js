/**
 * Errors use the API_CONTRACT §0 envelope:
 *   {"error": {"code": "MACHINE_CODE", "message": "...", "details": {...}}}
 * Codes are stable; messages are not.
 */
export class ApiError extends Error {
    statusCode;
    code;
    details;
    constructor(statusCode, code, message, details) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.details = details;
        this.name = "ApiError";
    }
}
export const badRequest = (code, message, details) => new ApiError(400, code, message, details);
export const unauthorized = (code, message, details) => new ApiError(401, code, message, details);
export const forbidden = (code, message, details) => new ApiError(403, code, message, details);
export const notFound = (code, message, details) => new ApiError(404, code, message, details);
export const conflict = (code, message, details) => new ApiError(409, code, message, details);
export const unprocessable = (code, message, details) => new ApiError(422, code, message, details);
export const tooManyRequests = (code, message, details) => new ApiError(429, code, message, details);
export const badGateway = (code, message, details) => new ApiError(502, code, message, details);
export function errorBody(err) {
    const error = {
        code: err.code,
        message: err.message,
    };
    if (err.details)
        error.details = err.details;
    return { error };
}
//# sourceMappingURL=errors.js.map