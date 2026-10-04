package com.seatreservation.seat_reservation_service.repository;


import com.seatreservation.seat_reservation_service.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {
}