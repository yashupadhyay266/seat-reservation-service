CREATE TABLE shows
(
    id UUID PRIMARY KEY,

    name VARCHAR(200) NOT NULL,

    price_paise BIGINT NOT NULL
        CHECK (price_paise >= 0),

    per_user_limit INTEGER NOT NULL DEFAULT 4
        CHECK (per_user_limit > 0),

    total_seats INTEGER NOT NULL
        CHECK (total_seats >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE reservations
(
    id UUID PRIMARY KEY,

    show_id UUID NOT NULL
        REFERENCES shows(id),

    user_id VARCHAR(100) NOT NULL,

    amount_paise BIGINT NOT NULL
        CHECK (amount_paise >= 0),

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);


CREATE TABLE seats
(
    show_id UUID NOT NULL
        REFERENCES shows(id),

    seat_no VARCHAR(30) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',

    reservation_id UUID
        REFERENCES reservations(id),

    user_id VARCHAR(100),

    PRIMARY KEY (show_id, seat_no),

    CONSTRAINT chk_seat_status
        CHECK (status IN ('AVAILABLE', 'CONFIRMED'))
);


CREATE TABLE reservation_seats
(
    reservation_id UUID NOT NULL
        REFERENCES reservations(id),

    show_id UUID NOT NULL,

    seat_no VARCHAR(30) NOT NULL,

    PRIMARY KEY (reservation_id, seat_no),

    FOREIGN KEY (show_id, seat_no)
        REFERENCES seats(show_id, seat_no)
);


CREATE TABLE user_show_booking
(
    show_id UUID NOT NULL
        REFERENCES shows(id),

    user_id VARCHAR(100) NOT NULL,

    confirmed_seats INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (show_id, user_id),

    CHECK (confirmed_seats >= 0)
);


CREATE TABLE idempotency_requests
(
    user_id VARCHAR(100) NOT NULL,

    idempotency_key VARCHAR(200) NOT NULL,

    show_id UUID NOT NULL
        REFERENCES shows(id),

    request_hash VARCHAR(64) NOT NULL,

    state VARCHAR(30) NOT NULL,

    reservation_id UUID
        REFERENCES reservations(id),

    response_code INTEGER,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, idempotency_key),

    CONSTRAINT chk_idempotency_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED'))
);


CREATE INDEX idx_reservations_show_user
    ON reservations(show_id, user_id);

CREATE INDEX idx_seats_reservation
    ON seats(reservation_id);