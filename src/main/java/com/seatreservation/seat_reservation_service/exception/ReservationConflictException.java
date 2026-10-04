package com.seatreservation.seat_reservation_service.exception;

import lombok.Getter;

@Getter
public class ReservationConflictException
        extends RuntimeException {

    private final String code;

    public ReservationConflictException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }
}