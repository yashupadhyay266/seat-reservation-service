package com.seatreservation.seat_reservation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeatId implements Serializable {

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "seat_no")
    private String seatNo;
}