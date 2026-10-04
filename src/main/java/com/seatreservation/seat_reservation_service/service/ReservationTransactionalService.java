package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.entity.IdempotencyRequest;
import com.seatreservation.seat_reservation_service.entity.Reservation;
import com.seatreservation.seat_reservation_service.entity.ReservationSeat;
import com.seatreservation.seat_reservation_service.entity.Seat;
import com.seatreservation.seat_reservation_service.entity.Show;
import com.seatreservation.seat_reservation_service.entity.UserShowBooking;
import com.seatreservation.seat_reservation_service.enums.IdempotencyState;
import com.seatreservation.seat_reservation_service.enums.ReservationStatus;
import com.seatreservation.seat_reservation_service.enums.SeatStatus;
import com.seatreservation.seat_reservation_service.exception.InvalidSeatException;
import com.seatreservation.seat_reservation_service.exception.ReservationAccessDeniedException;
import com.seatreservation.seat_reservation_service.exception.ReservationConflictException;
import com.seatreservation.seat_reservation_service.exception.ReservationNotFoundException;
import com.seatreservation.seat_reservation_service.exception.ReservationStateException;
import com.seatreservation.seat_reservation_service.exception.ShowNotFoundException;
import com.seatreservation.seat_reservation_service.repository.IdempotencyRequestRepository;
import com.seatreservation.seat_reservation_service.repository.ReservationRepository;
import com.seatreservation.seat_reservation_service.repository.ReservationSeatRepository;
import com.seatreservation.seat_reservation_service.repository.SeatRepository;
import com.seatreservation.seat_reservation_service.repository.ShowRepository;
import com.seatreservation.seat_reservation_service.repository.UserShowBookingRepository;
import com.seatreservation.seat_reservation_service.utils.ReservationRequestHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationTransactionalService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final UserShowBookingRepository userShowBookingRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final ReservationRequestHasher requestHasher;

    @Transactional
    public ReservationResponse reserve(UUID showId, String userId, ReserveSeatRequest request) {

        Show show = showRepository
                .findById(showId)
                .orElseThrow(ShowNotFoundException::new);

        List<String> requestedSeats = normalizeSeats(request.seats());
        String requestHash = requestHasher.hash(showId, requestedSeats);
        idempotencyRequestRepository.insertIfAbsent(
                userId,
                request.idempotencyKey(),
                showId,
                requestHash
        );

        IdempotencyRequest idempotency =
                idempotencyRequestRepository
                        .findForUpdate(
                                userId,
                                request.idempotencyKey()
                        )
                        .orElseThrow();

        if (!idempotency.getRequestHash().equals(requestHash)) {
            throw new ReservationConflictException("IDEMPOTENCY_CONFLICT", "Idempotency key was already used with a different request");
        }

        if (idempotency.getState() == IdempotencyState.COMPLETED) {
            return buildExistingResponse(idempotency.getReservationId());
        }

        userShowBookingRepository.insertIfAbsent(
                showId,
                userId
        );

        UserShowBooking userBooking =
                userShowBookingRepository
                        .findForUpdate(
                                showId,
                                userId
                        )
                        .orElseThrow();

        if (userBooking.getConfirmedSeats() + requestedSeats.size() > show.getPerUserLimit()) {
            throw new ReservationConflictException("PER_USER_LIMIT", "Per-user seat limit exceeded");
        }

        List<Seat> seats = seatRepository.findSeatsForUpdate(showId, requestedSeats);
        if (seats.size() != requestedSeats.size()) {
            throw new InvalidSeatException("One or more requested seats do not exist");
        }

        boolean anySeatTaken = seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE);

        if (anySeatTaken) {

            throw new ReservationConflictException(
                    "SEAT_TAKEN",
                    "One or more requested seats are already taken"
            );
        }

        UUID reservationId =
                UUID.randomUUID();

        long amountPaise =
                Math.multiplyExact(
                        show.getPricePaise(),
                        requestedSeats.size()
                );

        Reservation reservation =
                new Reservation(
                        reservationId,
                        showId,
                        userId,
                        amountPaise
                );

        reservationRepository.save(reservation);

        for (Seat seat : seats) {

            seat.confirm(
                    reservationId,
                    userId
            );
        }

        List<ReservationSeat> reservationSeats =
                requestedSeats.stream()
                        .map(seatNo ->
                                new ReservationSeat(
                                        reservationId,
                                        showId,
                                        seatNo
                                )
                        )
                        .toList();

        reservationSeatRepository.saveAll(
                reservationSeats
        );

        userBooking.addSeats(
                requestedSeats.size()
        );

        idempotency.complete(
                reservationId,
                201
        );

        return new ReservationResponse(
                reservationId,
                showId,
                userId,
                requestedSeats,
                amountPaise,
                "confirmed"
        );
    }

    @Transactional
    public ReservationResponse cancel(
            UUID reservationId,
            String authenticatedUserId
    ) {

        Reservation reservation =
                reservationRepository
                        .findForUpdate(reservationId)
                        .orElseThrow(
                                ReservationNotFoundException::new
                        );

        if (!reservation.getUserId()
                .equals(authenticatedUserId)) {

            throw new ReservationAccessDeniedException();
        }

        List<String> reservedSeatNumbers =
                reservationSeatRepository
                        .findSeatNumbersByReservationId(
                                reservationId
                        )
                        .stream()
                        .sorted()
                        .toList();

        if (reservedSeatNumbers.isEmpty()) {

            throw new ReservationStateException(
                    "Reservation has no associated seats"
            );
        }

        if (reservation.getStatus()
                == ReservationStatus.CANCELLED) {

            return buildExistingResponse(
                    reservationId
            );
        }

        if (reservation.getStatus()
                != ReservationStatus.CONFIRMED) {

            throw new ReservationStateException(
                    "Reservation is not in a cancellable state"
            );
        }

        UserShowBooking userBooking =
                userShowBookingRepository
                        .findForUpdate(
                                reservation.getShowId(),
                                reservation.getUserId()
                        )
                        .orElseThrow(() ->
                                new ReservationStateException(
                                        "User booking counter does not exist"
                                )
                        );

        if (userBooking.getConfirmedSeats()
                < reservedSeatNumbers.size()) {

            throw new ReservationStateException(
                    "User booking counter is inconsistent"
            );
        }

        List<Seat> seats =
                seatRepository.findSeatsForUpdate(
                        reservation.getShowId(),
                        reservedSeatNumbers
                );

        if (seats.size()
                != reservedSeatNumbers.size()) {

            throw new ReservationStateException(
                    "Reservation seat state is inconsistent"
            );
        }

        for (Seat seat : seats) {

            if (seat.getStatus()
                    != SeatStatus.CONFIRMED) {

                throw new ReservationStateException(
                        "Reservation contains a seat that is not confirmed"
                );
            }

            if (!reservationId.equals(
                    seat.getReservationId()
            )) {

                throw new ReservationStateException(
                        "Seat belongs to another reservation"
                );
            }

            if (!authenticatedUserId.equals(
                    seat.getUserId()
            )) {

                throw new ReservationStateException(
                        "Seat belongs to another user"
                );
            }
        }

        for (Seat seat : seats) {

            seat.release(
                    reservationId,
                    authenticatedUserId
            );
        }

        userBooking.removeSeats(
                reservedSeatNumbers.size()
        );

        reservation.cancel();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                reservedSeatNumbers,
                reservation.getAmountPaise(),
                "cancelled"
        );
    }

    private List<String> normalizeSeats(
            List<String> seats
    ) {

        List<String> normalized =
                seats.stream()
                        .map(String::trim)
                        .sorted()
                        .toList();

        if (
                new HashSet<>(normalized).size()
                        != normalized.size()
        ) {

            throw new InvalidSeatException(
                    "Duplicate seats are not allowed"
            );
        }

        return normalized;
    }

    private ReservationResponse buildExistingResponse(
            UUID reservationId
    ) {

        Reservation reservation =
                reservationRepository
                        .findById(reservationId)
                        .orElseThrow();

        List<String> seats =
                reservationSeatRepository
                        .findSeatNumbersByReservationId(
                                reservationId
                        );

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus()
                        .name()
                        .toLowerCase()
        );
    }
}