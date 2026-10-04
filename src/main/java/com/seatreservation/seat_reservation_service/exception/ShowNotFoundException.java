package com.seatreservation.seat_reservation_service.exception;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException() {
        super("Show not found");
    }
}