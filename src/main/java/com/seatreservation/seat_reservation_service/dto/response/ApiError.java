package com.seatreservation.seat_reservation_service.dto.response;

public record ApiError(
        String code,
        String message
) {
}