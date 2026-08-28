package com.example.payment.errors;

public class IdempotencyConflictException
        extends RuntimeException {

    public IdempotencyConflictException(
            String idempotencyKey
    ) {
        super(
                "Idempotency key is already used "
                        + "for another payment: "
                        + idempotencyKey
        );
    }
}