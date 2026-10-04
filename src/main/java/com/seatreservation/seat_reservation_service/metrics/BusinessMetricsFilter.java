package com.seatreservation.seat_reservation_service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BusinessMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        boolean reservationRequest = isReservationRequest(request);
        boolean cancellationRequest = isCancellationRequest(request);

        Timer.Sample sample = null;

        if (reservationRequest) {
            meterRegistry.counter("seat.reservation.requests").increment();
            sample = Timer.start(meterRegistry);
        }

        if (cancellationRequest) {
            meterRegistry.counter("seat.cancellation.requests").increment();
            sample = Timer.start(meterRegistry);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {

            int status = response.getStatus();

            if (reservationRequest) {

                String outcome = reservationOutcome(status);

                meterRegistry.counter(
                        "seat.reservation.responses",
                        "outcome",
                        outcome,
                        "status",
                        String.valueOf(status)
                ).increment();

                if (sample != null) {
                    sample.stop(
                            Timer.builder("seat.reservation.duration")
                                    .tag("outcome", outcome)
                                    .register(meterRegistry)
                    );
                }
            }

            if (cancellationRequest) {

                String outcome = cancellationOutcome(status);

                meterRegistry.counter(
                        "seat.cancellation.responses",
                        "outcome",
                        outcome,
                        "status",
                        String.valueOf(status)
                ).increment();

                if (sample != null) {
                    sample.stop(
                            Timer.builder("seat.cancellation.duration")
                                    .tag("outcome", outcome)
                                    .register(meterRegistry)
                    );
                }
            }
        }
    }

    private boolean isReservationRequest(HttpServletRequest request) {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        return request.getRequestURI().matches("^/shows/[^/]+/reserve$");
    }

    private boolean isCancellationRequest(HttpServletRequest request) {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        return request.getRequestURI().matches("^/reservations/[^/]+/cancel$");
    }

    private String reservationOutcome(int status) {

        if (status == 201) {
            return "success";
        }

        if (status == 409) {
            return "conflict";
        }

        if (status >= 500) {
            return "server_error";
        }

        if (status >= 400) {
            return "client_error";
        }

        return "other";
    }

    private String cancellationOutcome(int status) {

        if (status == 200) {
            return "success";
        }

        if (status == 409) {
            return "conflict";
        }

        if (status >= 500) {
            return "server_error";
        }

        if (status >= 400) {
            return "client_error";
        }

        return "other";
    }
}