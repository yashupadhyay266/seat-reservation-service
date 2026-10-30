package com.seatreservation.seat_reservation_service.ai.controller;

import com.seatreservation.seat_reservation_service.ai.dto.AssistantChatRequest;
import com.seatreservation.seat_reservation_service.ai.dto.AssistantChatResponse;
import com.seatreservation.seat_reservation_service.ai.service.SeatReservationAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AssistantController {

    private final SeatReservationAssistantService assistantService;

    @PostMapping("/chat")
    public AssistantChatResponse chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssistantChatRequest request
    ) {
        String conversationId = normalizeOrGenerate(request.conversationId());
        String requestId = normalizeOrGenerate(request.requestId());

        return assistantService.chat(
                jwt.getSubject(),
                conversationId,
                requestId,
                request.message().trim()
        );
    }

    private String normalizeOrGenerate(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }
}
