package com.example.payment.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(
            nullable = false,
            length = 3
    )
    private String currency;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            nullable = false,
            columnDefinition = "payment_status"
    )
    private PaymentStatus status;

    @NotBlank
    @Size(max = 128)
    @Column(
            name = "idempotency_key",
            nullable = false,
            unique = true,
            updatable = false,
            length = 128
    )
    private String idempotencyKey;

    @Column(
            name = "failure_code",
            length = 64
    )
    private String failureCode;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(
            name = "status_check_attempts",
            nullable = false
    )
    private int statusCheckAttempts;

    protected Payment() {}

    public Payment(
            long userId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive"
            );
        }

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive"
            );
        }

        if (currency == null
                || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "currency must contain three uppercase letters"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "idempotencyKey must not be blank"
            );
        }

        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
        this.statusCheckAttempts = 0;
    }

    public void markSucceeded() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending payment can succeed"
            );
        }

        this.status = PaymentStatus.SUCCEEDED;
        this.failureCode = null;
    }

    public void markFailed(String failureCode) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending payment can fail"
            );
        }

        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException(
                    "failureCode must not be blank"
            );
        }

        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();

        if (status == null) {
            status = PaymentStatus.PENDING;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasSameParameters(
            long userId,
            long amount,
            String currency
    ) {
        return this.userId == userId
                && this.amount == amount
                && this.currency.equals(currency);
    }

    public void incrementStatusCheckAttempts() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending payment can be checked"
            );
        }

        this.statusCheckAttempts++;
    }

    public void markUnknown() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending payment can become unknown"
            );
        }

        this.status = PaymentStatus.UNKNOWN;
        this.failureCode = "STATUS_CHECK_RETRIES_EXHAUSTED";
    }

    public int getStatusCheckAttempts() {
        return statusCheckAttempts;
    }
}
