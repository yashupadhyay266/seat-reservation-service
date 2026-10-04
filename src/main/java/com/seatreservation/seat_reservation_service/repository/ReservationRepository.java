package com.seatreservation.seat_reservation_service.repository;

import com.seatreservation.seat_reservation_service.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdAndUserId(UUID id, String userId);
}