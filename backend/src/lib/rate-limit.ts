/**
 * In-process fixed-window limiter. Sufficient for a single-node v1 deployment;
 * move to Redis when more than one instance serves traffic.
 */
export class RateLimiter {
  private readonly windows = new Map<string, { count: number; resetAt: number }>();

  constructor(private readonly now: () => number = Date.now) {}

  /** Returns true when the call is allowed, false when the limit is exceeded. */
  hit(key: string, limit: number, windowMs: number): boolean {
    const t = this.now();
    const entry = this.windows.get(key);
    if (!entry || entry.resetAt <= t) {
      this.windows.set(key, { count: 1, resetAt: t + windowMs });
      if (this.windows.size > 10_000) this.prune(t);
      return true;
    }
    if (entry.count >= limit) return false;
    entry.count += 1;
    return true;
  }

  private prune(t: number): void {
    for (const [key, entry] of this.windows) {
      if (entry.resetAt <= t) this.windows.delete(key);
    }
  }
}
