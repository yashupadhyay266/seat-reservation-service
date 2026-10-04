package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.exception.InvalidSeatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationLockService {

    private final RedissonClient redissonClient;
    private final ReservationTransactionalService transactionalService;

    public ReservationResponse reserve(
            UUID showId,
            String userId,
            ReserveSeatRequest request
    ) {
        List<String> seats = request.seats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        if (new HashSet<>(seats).size() != seats.size()) {
            throw new InvalidSeatException("Duplicate seats are not allowed");
        }

        List<RLock> locks = new ArrayList<>();

        locks.add(redissonClient.getLock(
                "reservation:idempotency:" + userId + ":" + request.idempotencyKey()
        ));

        locks.add(redissonClient.getLock(
                "reservation:user:" + showId + ":" + userId
        ));

        for (String seat : seats) {
            locks.add(redissonClient.getLock(
                    "reservation:seat:" + showId + ":" + seat
            ));
        }

        RLock multiLock = redissonClient.getMultiLock(
                locks.toArray(RLock[]::new)
        );

        boolean locked = false;

        try {
            try {
                multiLock.lock();
                locked = true;
            } catch (RedisException exception) {
                log.warn(
                        "Redis locking unavailable. Falling back to PostgreSQL locking. showId={}, userId={}",
                        showId,
                        userId,
                        exception
                );

                return transactionalService.reserve(showId, userId, request);
            }

            return transactionalService.reserve(showId, userId, request);

        } finally {
            if (locked) {
                try {
                    if (multiLock.isHeldByCurrentThread()) {
                        multiLock.unlock();
                    }
                } catch (RedisException exception) {
                    log.error(
                            "Failed to release Redis reservation lock. showId={}, userId={}",
                            showId,
                            userId,
                            exception
                    );
                }
            }
        }
    }
}