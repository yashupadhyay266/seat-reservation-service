package com.seatreservation.seat_reservation_service.controller;

import com.seatreservation.seat_reservation_service.dto.request.LoginRequest;
import com.seatreservation.seat_reservation_service.dto.request.RegisterRequest;
import com.seatreservation.seat_reservation_service.dto.response.LoginResponse;
import com.seatreservation.seat_reservation_service.dto.response.RegisterResponse;
import com.seatreservation.seat_reservation_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}