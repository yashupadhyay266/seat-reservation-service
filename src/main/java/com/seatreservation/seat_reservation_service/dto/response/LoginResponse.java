package com.seatreservation.seat_reservation_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LoginResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("user_id")
        UUID userId,

        String username,

        String role

) {
}