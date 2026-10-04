package com.seatreservation.seat_reservation_service.entity;

import com.seatreservation.seat_reservation_service.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Reservation(
            UUID id,
            UUID showId,
            String userId,
            long amountPaise
    ) {
        this.id = id;
        this.showId = showId;
        this.userId = userId;
        this.amountPaise = amountPaise;
        this.status = ReservationStatus.CONFIRMED;
        this.createdAt = Instant.now();
        this.cancelledAt = null;
    }

    public void cancel() {

        if (status == ReservationStatus.CANCELLED) {
            return;
        }

        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only confirmed reservations can be cancelled"
            );
        }

        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

}