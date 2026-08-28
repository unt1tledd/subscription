package com.example.notification.errors;

public class InvalidPaymentEventException extends RuntimeException {
    public InvalidPaymentEventException(String message) {
        super(message);
    }
}
