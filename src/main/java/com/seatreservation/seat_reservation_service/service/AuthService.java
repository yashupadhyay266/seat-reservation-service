package com.seatreservation.seat_reservation_service.service;

import com.seatreservation.seat_reservation_service.dto.request.LoginRequest;
import com.seatreservation.seat_reservation_service.dto.request.RegisterRequest;
import com.seatreservation.seat_reservation_service.dto.response.LoginResponse;
import com.seatreservation.seat_reservation_service.dto.response.RegisterResponse;
import com.seatreservation.seat_reservation_service.entity.AppUser;
import com.seatreservation.seat_reservation_service.enums.UserRole;
import com.seatreservation.seat_reservation_service.repository.UserRepository;
import com.seatreservation.seat_reservation_service.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String username = request.username().trim();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        AppUser user = new AppUser(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(request.password()),
                UserRole.USER
        );

        AppUser savedUser = userRepository.save(user);
        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getRole().name()
        );
    }

    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(),
                        request.password()
                ));

        AppUser user = userRepository
                .findByUsername(request.username())
                .orElseThrow();

        String token = jwtTokenService.createToken(
                user.getId().toString(),
                user.getRole().name()
        );

        return new LoginResponse(
                token,
                "Bearer",
                3600,
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}