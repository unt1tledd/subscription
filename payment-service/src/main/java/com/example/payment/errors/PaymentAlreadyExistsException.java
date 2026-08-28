package com.example.payment.errors;

public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(String idempotencyKey) {

    }
}
