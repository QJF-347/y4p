/** RFC 3339 UTC with millisecond precision, e.g. 2026-11-21T09:31:07.412Z. */
export function toIso(value) {
    if (value === null || value === undefined)
        return null;
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime()))
        return null;
    return d.toISOString();
}
export function nowIso(clock = () => new Date()) {
    return clock().toISOString();
}
/** Parse an incoming RFC 3339 string; returns null when absent/invalid. */
export function parseTimestamp(value) {
    if (typeof value !== "string" || value.length === 0)
        return null;
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
}
export function addSeconds(date, seconds) {
    return new Date(date.getTime() + seconds * 1000);
}
//# sourceMappingURL=time.js.map