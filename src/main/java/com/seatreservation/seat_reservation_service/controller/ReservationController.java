package com.seatreservation.seat_reservation_service.controller;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.service.ReservationLockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationLockService reservationLockService;

    @PostMapping("/shows/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @PathVariable UUID showId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReserveSeatRequest request
    ) {
        String userId = jwt.getSubject();
        return reservationLockService.reserve(
                showId,
                userId,
                request
        );
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        return reservationLockService.cancel(
                reservationId,
                userId
        );
    }
}