package com.seatreservation.seat_reservation_service.repository;

import com.seatreservation.seat_reservation_service.entity.IdempotencyRequest;
import com.seatreservation.seat_reservation_service.entity.IdempotencyRequestId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, IdempotencyRequestId> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO idempotency_requests(
                        user_id,
                        idempotency_key,
                        show_id,
                        request_hash,
                        state,
                        created_at
                    )
                    VALUES (
                        :userId,
                        :idempotencyKey,
                        :showId,
                        :requestHash,
                        'IN_PROGRESS',
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (user_id, idempotency_key)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("showId") UUID showId,
            @Param("requestHash") String requestHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM IdempotencyRequest i
            WHERE i.id.userId = :userId
              AND i.id.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRequest> findForUpdate(
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}