package com.example.payment.processor;

public record ProcessingResult(
        Status status,
        String failureCode
) {
    public enum Status {
        SUCCEEDED,
        FAILED,
        PENDING
    }

    public static ProcessingResult succeeded() {
        return new ProcessingResult(
                Status.SUCCEEDED,
                null
        );
    }

    public static ProcessingResult failed(
            String failureCode
    ) {
        return new ProcessingResult(
                Status.FAILED,
                failureCode
        );
    }

    public static ProcessingResult pending() {
        return new ProcessingResult(
                Status.PENDING,
                null
        );
    }
}