-- One reconciliation case per payment attempt: callback redelivery must not
-- enqueue duplicates (API_CONTRACT §4.2).
CREATE UNIQUE INDEX IF NOT EXISTS reconciliation_queue_payment_transaction_uq
  ON reconciliation_queue (payment_transaction_id);

-- Reported by POST /devices/heartbeat for fleet diagnostics.
ALTER TABLE device ADD COLUMN IF NOT EXISTS app_version TEXT;
