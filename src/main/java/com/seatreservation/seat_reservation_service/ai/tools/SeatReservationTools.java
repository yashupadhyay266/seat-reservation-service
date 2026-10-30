package com.seatreservation.seat_reservation_service.ai.tools;

import com.seatreservation.seat_reservation_service.ai.model.SeatStatusResult;
import com.seatreservation.seat_reservation_service.ai.service.ReservationQueryService;
import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.dto.response.SeatResponse;
import com.seatreservation.seat_reservation_service.dto.response.ShowResponse;
import com.seatreservation.seat_reservation_service.exception.InvalidSeatException;
import com.seatreservation.seat_reservation_service.service.ReservationLockService;
import com.seatreservation.seat_reservation_service.service.ShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReservationTools {

    public static final String USER_ID_CONTEXT = "authenticatedUserId";
    public static final String REQUEST_ID_CONTEXT = "assistantRequestId";

    private final ShowService showService;
    private final ReservationLockService reservationLockService;
    private final ReservationQueryService reservationQueryService;

    @Tool(description = "Get the complete current state of a show, including every seat status and available/held/confirmed counts. Use this for questions about show inventory or overall availability.")
    public ShowResponse getShowState(
            @ToolParam(description = "UUID of the show") UUID showId
    ) {
        log.info("AI tool getShowState showId={}", showId);
        return showService.getShow(showId);
    }

    @Tool(description = "Get the current status of one specific seat in a show. Use this when the user asks whether a particular seat is available, confirmed, booked, or reserved.")
    public SeatStatusResult getSeatStatus(
            @ToolParam(description = "UUID of the show") UUID showId,
            @ToolParam(description = "Seat number such as A12") String seatNumber
    ) {
        ShowResponse show = showService.getShow(showId);

        SeatResponse seat = show.seats().stream()
                .filter(current -> current.seat().equalsIgnoreCase(seatNumber.trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidSeatException("Seat does not exist in this show"));

        log.info("AI tool getSeatStatus showId={} seat={} status={}", showId, seat.seat(), seat.status());
        return new SeatStatusResult(showId, seat.seat(), seat.status());
    }

    @Tool(description = "Reserve one or more seats for the currently authenticated user. Call this tool ONLY when the user explicitly asks to book or reserve seats. Never use it for availability-only questions. The authenticated user identity is supplied securely by the application and cannot be chosen by the model.")
    public ReservationResponse reserveSeats(
            @ToolParam(description = "UUID of the show") UUID showId,
            @ToolParam(description = "Exact seat numbers to reserve, for example [A12, A13]") List<String> seats,
            ToolContext toolContext
    ) {
        String userId = requiredContext(toolContext, USER_ID_CONTEXT);
        String requestId = requiredContext(toolContext, REQUEST_ID_CONTEXT);

        String idempotencyKey = "ai:" + requestId + ":reserve";

        log.info("AI tool reserveSeats showId={} userId={} seats={} requestId={}", showId, userId, seats, requestId);

        return reservationLockService.reserve(
                showId,
                userId,
                new ReserveSeatRequest(seats, idempotencyKey)
        );
    }

    @Tool(description = "Get a reservation only if it belongs to the currently authenticated user. Use this when the user asks about one of their reservation IDs. The user identity is supplied by the application, not by the model.")
    public ReservationResponse getMyReservation(
            @ToolParam(description = "Reservation UUID") UUID reservationId,
            ToolContext toolContext
    ) {
        String userId = requiredContext(toolContext, USER_ID_CONTEXT);
        log.info("AI tool getMyReservation reservationId={} userId={}", reservationId, userId);
        return reservationQueryService.getOwnedReservation(reservationId, userId);
    }

    @Tool(description = "Cancel a reservation belonging to the currently authenticated user. Call this tool ONLY when the user explicitly asks to cancel a reservation. The user identity is supplied securely by the application and cannot be chosen by the model.")
    public ReservationResponse cancelMyReservation(
            @ToolParam(description = "Reservation UUID to cancel") UUID reservationId,
            ToolContext toolContext
    ) {
        String userId = requiredContext(toolContext, USER_ID_CONTEXT);
        log.info("AI tool cancelMyReservation reservationId={} userId={}", reservationId, userId);
        return reservationLockService.cancel(reservationId, userId);
    }

    private String requiredContext(ToolContext toolContext, String key) {
        Object value = toolContext.getContext().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Missing secure tool context: " + key);
        }
        return value.toString();
    }
}
