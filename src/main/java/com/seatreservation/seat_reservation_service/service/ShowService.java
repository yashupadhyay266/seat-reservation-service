package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.CreateShowRequest;
import com.seatreservation.seat_reservation_service.dto.response.SeatResponse;
import com.seatreservation.seat_reservation_service.dto.response.ShowResponse;
import com.seatreservation.seat_reservation_service.entity.Seat;
import com.seatreservation.seat_reservation_service.entity.SeatId;
import com.seatreservation.seat_reservation_service.entity.Show;
import com.seatreservation.seat_reservation_service.enums.SeatStatus;
import com.seatreservation.seat_reservation_service.repository.SeatRepository;
import com.seatreservation.seat_reservation_service.repository.ShowRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ShowService {

    private static final int DEFAULT_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public ShowService(
            ShowRepository showRepository,
            SeatRepository seatRepository
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {

        List<String> normalizedSeats =
                request.seats()
                        .stream()
                        .map(String::trim)
                        .sorted()
                        .toList();

        if (new HashSet<>(normalizedSeats).size()
                != normalizedSeats.size()) {

            throw new IllegalArgumentException(
                    "Duplicate seat numbers are not allowed"
            );
        }

        UUID showId = UUID.randomUUID();

        Show show = new Show(
                showId,
                request.name(),
                request.pricePaise(),
                DEFAULT_PER_USER_LIMIT,
                normalizedSeats.size(),
                Instant.now()
        );

        showRepository.save(show);

        List<Seat> seats =
                normalizedSeats.stream()
                        .map(seat ->
                                new Seat(
                                        new SeatId(
                                                showId,
                                                seat
                                        )
                                )
                        )
                        .toList();

        seatRepository.saveAll(seats);

        return toResponse(show, seats);
    }

    @Transactional
    public ShowResponse getShow(UUID showId) {

        Show show =
                showRepository.findById(showId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Show not found"
                                )
                        );

        List<Seat> seats =
                seatRepository.findAllByShowId(showId);

        return toResponse(show, seats);
    }

    private ShowResponse toResponse(
            Show show,
            List<Seat> seats
    ) {

        int available =
                (int) seats.stream()
                        .filter(s ->
                                s.getStatus()
                                        == SeatStatus.AVAILABLE
                        )
                        .count();

        int confirmed =
                (int) seats.stream()
                        .filter(s ->
                                s.getStatus()
                                        == SeatStatus.CONFIRMED
                        )
                        .count();

        int held = 0;

        List<SeatResponse> seatResponses =
                seats.stream()
                        .map(seat ->
                                new SeatResponse(
                                        seat.getId().getSeatNo(),
                                        seat.getStatus()
                                                .name()
                                                .toLowerCase()
                                )
                        )
                        .toList();

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),

                new ShowResponse.Counts(
                        available,
                        held,
                        confirmed
                ),

                seatResponses
        );
    }
}