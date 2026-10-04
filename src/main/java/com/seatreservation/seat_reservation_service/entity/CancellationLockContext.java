package com.seatreservation.seat_reservation_service.entity;

import java.util.List;
import java.util.UUID;

public record CancellationLockContext(
        UUID showId,
        String userId,
        List<String> seats
) {
}