package com.seatreservation.seat_reservation_service.ai.service;

import com.seatreservation.seat_reservation_service.ai.dto.AssistantChatResponse;
import com.seatreservation.seat_reservation_service.ai.tools.SeatReservationTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SeatReservationAssistantService {

    private static final String SYSTEM_PROMPT = """
            You are an AI operations assistant for a seat-reservation platform.

            Treat seat inventory like operational state: AVAILABLE means free inventory and CONFIRMED means already booked.

            Rules:
            1. For any show-specific or seat-specific factual question, use the provided tools. Never invent show IDs, seat states, reservation IDs, prices, or counts.
            2. Never ask for or accept a userId from the user. The backend injects authenticated identity securely.
            3. Call reserveSeats only when the user clearly and explicitly asks to reserve/book specific seats.
            4. Call cancelMyReservation only when the user clearly and explicitly asks to cancel a specific reservation.
            5. Availability/status questions are read-only and must never trigger a reservation or cancellation.
            6. If the user asks to reserve multiple seats, preserve the application's all-or-nothing behavior.
            7. If a tool reports a conflict or unavailable seat, explain it as a clean business outcome rather than an internal server failure.
            8. Keep answers concise and operational. State which seats are available/confirmed when relevant.
            """;

    private final ChatClient seatAssistantChatClient;
    private final SeatReservationTools seatReservationTools;

    public AssistantChatResponse chat(
            String authenticatedUserId,
            String conversationId,
            String requestId,
            String message
    ) {
        log.info("AI assistant request userId={} conversationId={} requestId={}", authenticatedUserId, conversationId, requestId);

        String response = seatAssistantChatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(seatReservationTools)
                .toolContext(Map.of(
                        SeatReservationTools.USER_ID_CONTEXT, authenticatedUserId,
                        SeatReservationTools.REQUEST_ID_CONTEXT, requestId
                ))
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return new AssistantChatResponse(conversationId, requestId, response);
    }
}
