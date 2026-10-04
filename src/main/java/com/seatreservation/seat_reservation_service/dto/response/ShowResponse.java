package com.seatreservation.seat_reservation_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record ShowResponse(

        UUID id,

        String name,

        @JsonProperty("price_paise")
        long pricePaise,

        @JsonProperty("per_user_limit")
        int perUserLimit,

        @JsonProperty("total_seats")
        int totalSeats,

        Counts counts,

        List<SeatResponse> seats

) {

    public record Counts(
            int available,
            int held,
            int confirmed
    ) {
    }
}