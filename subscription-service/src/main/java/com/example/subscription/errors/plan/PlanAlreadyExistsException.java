package com.example.subscription.errors.plan;

public class PlanAlreadyExistsException extends RuntimeException {

    public PlanAlreadyExistsException(String code) {
        super("Plan with code " + code + " already exists");
    }
}