package com.seatreservation.seat_reservation_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}