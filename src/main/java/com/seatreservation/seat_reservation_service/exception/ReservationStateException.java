package com.seatreservation.seat_reservation_service.exception;

public class ReservationStateException extends RuntimeException {

    public ReservationStateException(String message) {
        super(message);
    }
}