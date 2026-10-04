package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.entity.CancellationLockContext;
import com.seatreservation.seat_reservation_service.exception.InvalidSeatException;
import com.seatreservation.seat_reservation_service.exception.SeatUnavailableException;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationLockService {

    private final RedissonClient redissonClient;
    private final ReservationTransactionalService transactionalService;
    private final ReservationLookupService reservationLookupService;
    private final ReservationFastPathService reservationFastPathService;

    public ReservationResponse reserve(
            UUID showId,
            String userId,
            ReserveSeatRequest request
    ) {

        log.debug(
                "Reservation request received showId={} userId={} seats={} idempotencyKey={}",
                showId,
                userId,
                request.seats(),
                request.idempotencyKey()
        );

        log.debug(
                "Checking reservation fast path showId={} userId={} seats={}",
                showId,
                userId,
                request.seats()
        );

        if (reservationFastPathService.canRejectImmediately(showId, userId, request)) {

            log.debug(
                    "Reservation rejected by fast path because one or more seats are unavailable showId={} userId={} seats={}",
                    showId,
                    userId,
                    request.seats()
            );

            throw new SeatUnavailableException(
                    "One or more requested seats are no longer available"
            );
        }

        log.debug(
                "Reservation passed fast path showId={} userId={} seats={}",
                showId,
                userId,
                request.seats()
        );

        List<String> seats = request.seats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        log.debug(
                "Reservation seats normalized and sorted showId={} userId={} seats={}",
                showId,
                userId,
                seats
        );

        if (new HashSet<>(seats).size() != seats.size()) {

            log.debug(
                    "Reservation rejected because duplicate seats were requested showId={} userId={} seats={}",
                    showId,
                    userId,
                    seats
            );

            throw new InvalidSeatException(
                    "Duplicate seats are not allowed"
            );
        }

        List<RLock> locks = new ArrayList<>();

        log.debug(
                "Preparing Redis idempotency lock showId={} userId={} idempotencyKey={}",
                showId,
                userId,
                request.idempotencyKey()
        );

        locks.add(
                redissonClient.getLock(
                        "reservation:idempotency:"
                                + userId
                                + ":"
                                + request.idempotencyKey()
                )
        );

        log.debug(
                "Preparing Redis user lock showId={} userId={}",
                showId,
                userId
        );

        locks.add(
                redissonClient.getLock(
                        "reservation:user:"
                                + showId
                                + ":"
                                + userId
                )
        );

        for (String seat : seats) {

            log.debug(
                    "Preparing Redis seat lock showId={} userId={} seat={}",
                    showId,
                    userId,
                    seat
            );

            locks.add(
                    redissonClient.getLock(
                            "reservation:seat:"
                                    + showId
                                    + ":"
                                    + seat
                    )
            );
        }

        log.debug(
                "Creating Redis multi-lock showId={} userId={} seats={} lockCount={}",
                showId,
                userId,
                seats,
                locks.size()
        );

        RLock multiLock =
                redissonClient.getMultiLock(
                        locks.toArray(RLock[]::new)
                );

        boolean locked = false;

        try {

            try {

                log.debug(
                        "Attempting bounded Redis reservation lock showId={} userId={} seats={}",
                        showId,
                        userId,
                        seats
                );

                boolean acquired =
                        multiLock.tryLock(
                                500,
                                60_000,
                                TimeUnit.MILLISECONDS
                        );

                if (!acquired) {

                    log.debug(
                            "Redis reservation lock not acquired within wait period showId={} userId={} seats={}",
                            showId,
                            userId,
                            seats
                    );

                    log.debug(
                            "Rechecking fast path after Redis contention showId={} userId={} seats={}",
                            showId,
                            userId,
                            seats
                    );

                    if (reservationFastPathService.canRejectImmediately(
                            showId,
                            userId,
                            request
                    )) {

                        log.debug(
                                "Reservation rejected after Redis contention because seat is now unavailable showId={} userId={} seats={}",
                                showId,
                                userId,
                                seats
                        );

                        throw new SeatUnavailableException(
                                "One or more requested seats are no longer available"
                        );
                    }

                    log.debug(
                            "Seat still appears available after Redis contention. Falling back to PostgreSQL locking showId={} userId={} seats={}",
                            showId,
                            userId,
                            seats
                    );

                    return transactionalService.reserve(
                            showId,
                            userId,
                            request
                    );
                }

                locked = true;

                log.debug(
                        "Redis reservation lock acquired showId={} userId={} seats={}",
                        showId,
                        userId,
                        seats
                );

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();

                log.warn(
                        "Redis reservation lock wait interrupted. Falling back to PostgreSQL locking. showId={} userId={}",
                        showId,
                        userId,
                        exception
                );

                return transactionalService.reserve(
                        showId,
                        userId,
                        request
                );

            } catch (RedisException exception) {

                log.warn(
                        "Redis locking unavailable. Falling back to PostgreSQL locking. showId={} userId={}",
                        showId,
                        userId,
                        exception
                );

                return transactionalService.reserve(
                        showId,
                        userId,
                        request
                );
            }

            log.debug(
                    "Executing transactional reservation showId={} userId={} seats={}",
                    showId,
                    userId,
                    seats
            );

            return transactionalService.reserve(
                    showId,
                    userId,
                    request
            );

        } finally {

            if (locked) {

                log.debug(
                        "Checking Redis reservation lock before release showId={} userId={} seats={}",
                        showId,
                        userId,
                        seats
                );

                try {

                    if (multiLock.isHeldByCurrentThread()) {

                        log.debug(
                                "Releasing Redis reservation lock showId={} userId={} seats={}",
                                showId,
                                userId,
                                seats
                        );

                        multiLock.unlock();

                        log.debug(
                                "Redis reservation lock released showId={} userId={} seats={}",
                                showId,
                                userId,
                                seats
                        );
                    }

                } catch (RedisException exception) {

                    log.error(
                            "Failed to release Redis reservation lock. showId={} userId={}",
                            showId,
                            userId,
                            exception
                    );
                }
            }
        }
    }

    public ReservationResponse cancel(UUID reservationId, String userId) {
        log.debug("Cancellation request received reservationId={} userId={}", reservationId, userId);

        CancellationLockContext context = reservationLookupService.getCancellationContext(reservationId, userId);
        log.debug("Cancellation lock context loaded reservationId={} showId={} userId={} seats={}", reservationId, context.showId(), context.userId(), context.seats());

        List<RLock> locks = new ArrayList<>();
        locks.add(redissonClient.getLock("reservation:user:" + context.showId() + ":" + context.userId()));

        context.seats().stream().sorted().forEach(seat ->
                locks.add(redissonClient.getLock("reservation:seat:" + context.showId() + ":" + seat))
        );

        RLock multiLock = redissonClient.getMultiLock(locks.toArray(RLock[]::new));
        boolean locked = false;

        try {
            try {
                log.debug("Attempting bounded Redis cancellation lock reservationId={} showId={} userId={} seats={}", reservationId, context.showId(), userId, context.seats());

                boolean acquired = multiLock.tryLock(500, 60_000, TimeUnit.MILLISECONDS);

                if (!acquired) {
                    log.debug("Redis cancellation lock not acquired within wait period. Falling back to PostgreSQL reservationId={} userId={}", reservationId, userId);
                    return transactionalService.cancel(reservationId, userId);
                }

                locked = true;
                log.debug("Redis cancellation lock acquired reservationId={} showId={} userId={} seats={}", reservationId, context.showId(), userId, context.seats());

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Redis cancellation lock wait interrupted. Falling back to PostgreSQL reservationId={} userId={}", reservationId, userId, exception);
                return transactionalService.cancel(reservationId, userId);

            } catch (RedisException exception) {
                log.warn("Redis unavailable during cancellation. Falling back to PostgreSQL locking. reservationId={} userId={}", reservationId, userId, exception);
                return transactionalService.cancel(reservationId, userId);
            }

            log.debug("Executing transactional cancellation reservationId={} showId={} userId={} seats={}", reservationId, context.showId(), userId, context.seats());
            return transactionalService.cancel(reservationId, userId);

        } finally {
            if (locked) {
                try {
                    if (multiLock.isHeldByCurrentThread()) {
                        multiLock.unlock();
                        log.debug("Redis cancellation lock released reservationId={}", reservationId);
                    }
                } catch (RedisException exception) {
                    log.error("Failed to release Redis cancellation lock reservationId={}", reservationId, exception);
                }
            }
        }
    }
}