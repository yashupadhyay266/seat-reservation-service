package com.seatreservation.seat_reservation_service.repository;

import com.seatreservation.seat_reservation_service.entity.ReservationSeat;
import com.seatreservation.seat_reservation_service.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    @Query("""
            SELECT rs.id.seatNo
            FROM ReservationSeat rs
            WHERE rs.id.reservationId = :reservationId
            ORDER BY rs.id.seatNo
            """)
    List<String> findSeatNumbersByReservationId(
            @Param("reservationId") UUID reservationId
    );
}