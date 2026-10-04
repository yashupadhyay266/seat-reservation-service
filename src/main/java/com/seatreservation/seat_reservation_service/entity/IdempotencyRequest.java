package com.seatreservation.seat_reservation_service.entity;

import com.seatreservation.seat_reservation_service.enums.IdempotencyState;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "idempotency_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRequest {

    @EmbeddedId
    private IdempotencyRequestId id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyState state;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public void complete(
            UUID reservationId,
            int responseCode
    ) {
        this.reservationId = reservationId;
        this.responseCode = responseCode;
        this.state = IdempotencyState.COMPLETED;
    }
}