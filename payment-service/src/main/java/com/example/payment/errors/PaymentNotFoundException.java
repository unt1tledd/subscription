package com.example.payment.errors;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super(String.format("Not found payment with id=%s", id));
    }
}
