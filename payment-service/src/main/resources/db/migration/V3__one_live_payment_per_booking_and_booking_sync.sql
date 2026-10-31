-- ST-24: at most one live (PENDING/SUCCESS) payment per booking, enforced by the database.
ALTER TABLE payments ADD COLUMN active_booking_id BIGINT NULL;

-- Backfill: the oldest live payment of each booking keeps the slot (guards the UNIQUE constraint below).
UPDATE payments p
SET p.active_booking_id = p.booking_id
WHERE p.status IN ('PENDING', 'SUCCESS')
  AND p.id = (SELECT MIN(x.id)
              FROM (SELECT id, booking_id FROM payments WHERE status IN ('PENDING', 'SUCCESS')) x
              WHERE x.booking_id = p.booking_id);

ALTER TABLE payments ADD CONSTRAINT uk_payments_active_booking UNIQUE (active_booking_id);

-- ST-55/56: remember that a final result still has to be delivered to the Booking Service.
ALTER TABLE payments ADD COLUMN booking_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_payments_booking_sync ON payments (booking_sync_pending, updated_at);
