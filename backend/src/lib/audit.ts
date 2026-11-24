import type { Queryable } from "./db.js";

export type ActorType = "USER" | "DEVICE" | "SYSTEM";

export interface AuditEntry {
  actorType: ActorType;
  actorId: string;
  action: string;
  entity: string;
  entityId?: string | null;
  details?: Record<string, unknown> | null;
  ip?: string | null;
}

export async function writeAudit(q: Queryable, entry: AuditEntry): Promise<void> {
  await q.query(
    `INSERT INTO audit_log (actor_type, actor_id, action, entity, entity_id, details, ip)
     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
    [
      entry.actorType,
      entry.actorId,
      entry.action,
      entry.entity,
      entry.entityId ?? null,
      entry.details ? JSON.stringify(entry.details) : null,
      entry.ip ?? null,
    ],
  );
}
