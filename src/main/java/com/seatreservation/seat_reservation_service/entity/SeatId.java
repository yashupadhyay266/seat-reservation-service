package com.seatreservation.seat_reservation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SeatId implements Serializable {

    @Column(name = "show_id")
    private UUID showId;

    @Column(name = "seat_no")
    private String seatNo;

    protected SeatId() {
    }

    public SeatId(UUID showId, String seatNo) {
        this.showId = showId;
        this.seatNo = seatNo;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getSeatNo() {
        return seatNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SeatId seatId)) {
            return false;
        }

        return Objects.equals(showId, seatId.showId)
                && Objects.equals(seatNo, seatId.seatNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showId, seatNo);
    }
}