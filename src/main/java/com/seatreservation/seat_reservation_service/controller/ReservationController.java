package com.seatreservation.seat_reservation_service.controller;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.service.ReservationLockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationLockService reservationLockService;

    @PostMapping("/shows/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@PathVariable UUID showId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReserveSeatRequest request) {
        String userId = jwt.getSubject();
        return reservationLockService.reserve(
                showId,
                userId,
                request
        );
    }
}