package com.seatreservation.seat_reservation_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CreateShowRequest(

        @NotBlank
        String name,

        @NotEmpty
        List<@NotBlank String> seats,

        @JsonProperty("price_paise")
        @PositiveOrZero
        long pricePaise

) {
}