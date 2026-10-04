package com.seatreservation.seat_reservation_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRequestId implements Serializable {

    @Column(name = "user_id")
    private String userId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;
}