package com.seatreservation.seat_reservation_service.controller;

import com.seatreservation.seat_reservation_service.dto.request.CreateShowRequest;
import com.seatreservation.seat_reservation_service.dto.response.ShowResponse;
import com.seatreservation.seat_reservation_service.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return showService.createShow(request);
    }

    @GetMapping("/{showId}")
    public ShowResponse getShow(@PathVariable UUID showId) {
        return showService.getShow(showId);
    }
}