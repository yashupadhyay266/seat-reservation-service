package com.seatreservation.seat_reservation_service.ai.service;

import com.seatreservation.seat_reservation_service.dto.response.ReservationResponse;
import com.seatreservation.seat_reservation_service.entity.Reservation;
import com.seatreservation.seat_reservation_service.exception.ReservationAccessDeniedException;
import com.seatreservation.seat_reservation_service.exception.ReservationNotFoundException;
import com.seatreservation.seat_reservation_service.repository.ReservationRepository;
import com.seatreservation.seat_reservation_service.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    @Transactional(readOnly = true)
    public ReservationResponse getOwnedReservation(UUID reservationId, String authenticatedUserId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUserId().equals(authenticatedUserId)) {
            throw new ReservationAccessDeniedException();
        }

        List<String> seats = reservationSeatRepository
                .findSeatNumbersByReservationId(reservationId)
                .stream()
                .sorted()
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus().name().toLowerCase()
        );
    }
}
