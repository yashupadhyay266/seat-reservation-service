package com.seatreservation.seat_reservation_service.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String username,
        String role
) {
}