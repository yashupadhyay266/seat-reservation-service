package com.seatreservation.seat_reservation_service.utils;

import com.seatreservation.seat_reservation_service.dto.response.ApiError;
import com.seatreservation.seat_reservation_service.exception.InvalidSeatException;
import com.seatreservation.seat_reservation_service.exception.ReservationConflictException;
import com.seatreservation.seat_reservation_service.exception.ShowNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ReservationConflictException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(InvalidSeatException.class)
    public ResponseEntity<ApiError> handleInvalidSeat(InvalidSeatException exception) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError("INVALID_SEAT", exception.getMessage()));
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ResponseEntity<ApiError> handleShowNotFound(ShowNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError("SHOW_NOT_FOUND", exception.getMessage()));
    }
}