package com.example.subscription.errors.plan;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(String code) {
        super("Plan with code '%s' was not found".formatted(code));
    }
}