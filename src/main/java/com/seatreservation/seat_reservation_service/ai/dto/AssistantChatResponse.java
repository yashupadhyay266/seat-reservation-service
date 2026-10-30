package com.seatreservation.seat_reservation_service.ai.dto;

public record AssistantChatResponse(
        String conversationId,
        String requestId,
        String response
) {
}
