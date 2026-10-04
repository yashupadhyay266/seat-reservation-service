package com.seatreservation.seat_reservation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "reservation_seats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    public ReservationSeat(
            UUID reservationId,
            UUID showId,
            String seatNo
    ) {
        this.id = new ReservationSeatId(reservationId, seatNo);
        this.showId = showId;
    }
}