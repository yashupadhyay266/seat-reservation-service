package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.ReserveSeatRequest;
import com.seatreservation.seat_reservation_service.entity.IdempotencyRequestId;
import com.seatreservation.seat_reservation_service.entity.Seat;
import com.seatreservation.seat_reservation_service.enums.SeatStatus;
import com.seatreservation.seat_reservation_service.repository.IdempotencyRequestRepository;
import com.seatreservation.seat_reservation_service.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationFastPathService {

    private final SeatRepository seatRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;

    @Transactional(readOnly = true)
    public boolean canRejectImmediately(
            UUID showId,
            String userId,
            ReserveSeatRequest request
    ) {

        List<String> seatNumbers = request.seats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        if (seatNumbers.isEmpty()) {
            return false;
        }

        if (new HashSet<>(seatNumbers).size() != seatNumbers.size()) {
            return false;
        }

        List<Seat> seats =
                seatRepository.findSeatsForFastCheck(
                        showId,
                        seatNumbers
                );

        if (seats.size() != seatNumbers.size()) {
            return false;
        }

        boolean allAvailable =
                seats.stream()
                        .allMatch(
                                seat ->
                                        seat.getStatus()
                                                == SeatStatus.AVAILABLE
                        );

        if (allAvailable) {
            return false;
        }

        IdempotencyRequestId id =
                new IdempotencyRequestId(
                        userId,
                        request.idempotencyKey()
                );

        boolean existingIdempotencyRequest = idempotencyRequestRepository.existsById(id);
        return !existingIdempotencyRequest;
    }
}