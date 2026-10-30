package com.seatreservation.seat_reservation_service.ai.model;

import java.util.UUID;

public record SeatStatusResult(
        UUID showId,
        String seat,
        String status
) {
}
