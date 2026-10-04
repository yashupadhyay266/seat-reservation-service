package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.entity.CancellationLockContext;
import com.seatreservation.seat_reservation_service.entity.Reservation;
import com.seatreservation.seat_reservation_service.exception.ReservationAccessDeniedException;
import com.seatreservation.seat_reservation_service.exception.ReservationNotFoundException;
import com.seatreservation.seat_reservation_service.exception.ReservationStateException;
import com.seatreservation.seat_reservation_service.repository.ReservationRepository;
import com.seatreservation.seat_reservation_service.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationLookupService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    @Transactional(readOnly = true)
    public CancellationLockContext getCancellationContext(
            UUID reservationId,
            String authenticatedUserId
    ) {

        Reservation reservation =
                reservationRepository
                        .findById(reservationId)
                        .orElseThrow(
                                ReservationNotFoundException::new
                        );

        if (!reservation.getUserId()
                .equals(authenticatedUserId)) {

            throw new ReservationAccessDeniedException();
        }

        List<String> seats =
                reservationSeatRepository
                        .findSeatNumbersByReservationId(
                                reservationId
                        )
                        .stream()
                        .sorted()
                        .toList();

        if (seats.isEmpty()) {
            throw new ReservationStateException(
                    "Reservation has no associated seats"
            );
        }

        return new CancellationLockContext(
                reservation.getShowId(),
                reservation.getUserId(),
                seats
        );
    }
}