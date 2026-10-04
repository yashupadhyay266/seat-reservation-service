package com.seatreservation.seat_reservation_service.exception;

public class InvalidSeatException extends RuntimeException {

    public InvalidSeatException(String message) {
        super(message);
    }
}