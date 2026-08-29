package com.example.subscription.errors;

import com.example.subscription.dto.ApiErrorResponse;
import com.example.subscription.errors.plan.PlanAlreadyExistsException;
import com.example.subscription.errors.plan.PlanNotFoundException;
import com.example.subscription.errors.subscription.SubscriptionAlreadyExistsException;
import com.example.subscription.errors.subscription.SubscriptionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler({PlanNotFoundException.class, SubscriptionNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handlePlanNotFound(
            RuntimeException exception
    ) {


        ApiErrorResponse response = new ApiErrorResponse(
                "RESOURCE_NOT_FOUND",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }


    @ExceptionHandler({PlanAlreadyExistsException.class, SubscriptionAlreadyExistsException.class})
    public ResponseEntity<ApiErrorResponse> handlePlanAlreadyExists(
            PlanAlreadyExistsException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "RESOURCE_ALREADY_EXISTS",
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}