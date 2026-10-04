package com.seatreservation.seat_reservation_service.exception;

public class ReservationAccessDeniedException extends RuntimeException {

    public ReservationAccessDeniedException() {
        super("Reservation does not belong to authenticated user");
    }
}