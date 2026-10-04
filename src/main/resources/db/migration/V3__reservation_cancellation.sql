ALTER TABLE reservations
    ADD COLUMN cancelled_at TIMESTAMPTZ;

UPDATE reservations
SET cancelled_at = CURRENT_TIMESTAMP
WHERE status = 'CANCELLED'
  AND cancelled_at IS NULL;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservation_cancelled_at
        CHECK (
            (status = 'CONFIRMED' AND cancelled_at IS NULL)
                OR
            (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
            );

ALTER TABLE seats
    ADD CONSTRAINT chk_seat_assignment_consistency
        CHECK (
            (
                status = 'AVAILABLE'
                    AND reservation_id IS NULL
                    AND user_id IS NULL
                )
                OR
            (
                status = 'CONFIRMED'
                    AND reservation_id IS NOT NULL
                    AND user_id IS NOT NULL
                )
            );