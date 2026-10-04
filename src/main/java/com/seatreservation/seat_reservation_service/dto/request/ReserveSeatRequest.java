package com.seatreservation.seat_reservation_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveSeatRequest(

        @NotEmpty
        List<@NotBlank @Size(max = 30) String> seats,

        @NotBlank
        @Size(max = 200)
        @JsonProperty("idempotency_key")
        String idempotencyKey
) {
}