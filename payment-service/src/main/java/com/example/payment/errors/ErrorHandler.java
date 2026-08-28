package com.example.payment.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePlanNotFound(
            PaymentNotFoundException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "PAYMENT_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }


    @ExceptionHandler(PaymentAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handlePlanAlreadyExists(
            PaymentNotFoundException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "PAYMENT_ALREADY_EXISTS",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "IDEMPOTENCY_KEY_CONFLICT",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}