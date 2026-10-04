package com.seatreservation.seat_reservation_service.repository;

import com.seatreservation.seat_reservation_service.entity.UserShowBooking;
import com.seatreservation.seat_reservation_service.entity.UserShowBookingId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserShowBookingRepository extends JpaRepository<UserShowBooking, UserShowBookingId> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO user_show_booking(
                        show_id,
                        user_id,
                        confirmed_seats
                    )
                    VALUES (:showId, :userId, 0)
                    ON CONFLICT (show_id, user_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM UserShowBooking u
            WHERE u.id.showId = :showId
              AND u.id.userId = :userId
            """)
    Optional<UserShowBooking> findForUpdate(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );
}