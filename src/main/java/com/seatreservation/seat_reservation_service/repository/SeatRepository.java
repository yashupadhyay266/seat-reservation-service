package com.seatreservation.seat_reservation_service.repository;


import com.seatreservation.seat_reservation_service.entity.Seat;
import com.seatreservation.seat_reservation_service.entity.SeatId;
import com.seatreservation.seat_reservation_service.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, SeatId> {

    @Query("""
        SELECT s
        FROM Seat s
        WHERE s.id.showId = :showId
        ORDER BY s.id.seatNo
        """)
    List<Seat> findAllByShowId(
            @Param("showId") UUID showId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM Seat s
        WHERE s.id.showId = :showId
          AND s.id.seatNo IN :seatNumbers
        ORDER BY s.id.seatNo
        """)
    List<Seat> findSeatsForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    @Query("""
    SELECT s
    FROM Seat s
    WHERE s.id.showId = :showId
      AND s.id.seatNo IN :seatNumbers
    ORDER BY s.id.seatNo
    """)
    List<Seat> findSeatsForFastCheck(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    long countByStatus(SeatStatus status);
}