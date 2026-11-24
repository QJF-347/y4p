export async function writeAudit(q, entry) {
    await q.query(`INSERT INTO audit_log (actor_type, actor_id, action, entity, entity_id, details, ip)
     VALUES ($1, $2, $3, $4, $5, $6, $7)`, [
        entry.actorType,
        entry.actorId,
        entry.action,
        entry.entity,
        entry.entityId ?? null,
        entry.details ? JSON.stringify(entry.details) : null,
        entry.ip ?? null,
    ]);
}
//# sourceMappingURL=audit.js.map