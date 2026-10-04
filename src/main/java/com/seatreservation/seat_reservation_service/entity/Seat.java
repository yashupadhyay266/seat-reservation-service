package com.seatreservation.seat_reservation_service.entity;

import com.seatreservation.seat_reservation_service.enums.SeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    @EmbeddedId
    private SeatId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "user_id")
    private String userId;

    protected Seat() {
    }

    public Seat(SeatId id) {
        this.id = id;
        this.status = SeatStatus.AVAILABLE;
    }

    public SeatId getId() {
        return id;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getUserId() {
        return userId;
    }

    public void confirm(
            UUID reservationId,
            String userId
    ) {

        if (status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Seat is not available"
            );
        }

        this.status = SeatStatus.CONFIRMED;
        this.reservationId = reservationId;
        this.userId = userId;
    }

    public void release(
            UUID expectedReservationId,
            String expectedUserId
    ) {

        if (status != SeatStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Seat is not confirmed"
            );
        }

        if (!expectedReservationId.equals(reservationId)) {
            throw new IllegalStateException(
                    "Seat belongs to another reservation"
            );
        }

        if (!expectedUserId.equals(userId)) {
            throw new IllegalStateException(
                    "Seat belongs to another user"
            );
        }

        this.status = SeatStatus.AVAILABLE;
        this.reservationId = null;
        this.userId = null;
    }
}