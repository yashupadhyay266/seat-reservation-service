package com.seatreservation.seat_reservation_service.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
        @NotBlank
        @Size(max = 2000)
        String message,

        @Size(max = 100)
        String conversationId,

        @Size(max = 100)
        String requestId
) {
}
