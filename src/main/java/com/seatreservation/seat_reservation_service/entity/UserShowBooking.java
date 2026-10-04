package com.seatreservation.seat_reservation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_show_booking")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserShowBooking {

    @EmbeddedId
    private UserShowBookingId id;

    @Column(name = "confirmed_seats", nullable = false)
    private int confirmedSeats;

    public void addSeats(int count) {
        this.confirmedSeats += count;
    }

    public void removeSeats(int count) {

        if (confirmedSeats - count < 0) {
            throw new IllegalStateException(
                    "Confirmed seat count cannot be negative"
            );
        }

        this.confirmedSeats -= count;
    }
}