package com.example.notification.entity;

import com.example.notification.dto.PaymentStatusChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "event_id",
            nullable = false,
            updatable = false,
            unique = true
    )
    private UUID eventId;

    @Column(
            name = "payment_id",
            nullable = false,
            updatable = false
    )
    private UUID paymentId;

    @Column(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "payment_status",
            nullable = false,
            updatable = false,
            columnDefinition = "incoming_payment_status"
    )
    private PaymentEventStatus paymentStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "payload",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    private Map<String, Object> payload;

    @Column(
            name = "message",
            nullable = false,
            updatable = false
    )
    private String message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "notification_status"
    )
    private NotificationStatus status;

    @Column(
            name = "attempts",
            nullable = false
    )
    private int attempts;

    @Column(
            name = "next_attempt_at",
            nullable = false
    )
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

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

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    private Notification(
            UUID eventId,
            UUID paymentId,
            long userId,
            PaymentEventStatus paymentStatus,
            Map<String, Object> payload,
            String message
    ) {
        this.eventId = Objects.requireNonNull(eventId);
        this.paymentId = Objects.requireNonNull(paymentId);
        this.userId = userId;
        this.paymentStatus = Objects.requireNonNull(paymentStatus);

        this.payload = new LinkedHashMap<>(
                Objects.requireNonNull(payload)
        );

        this.message = Objects.requireNonNull(message);
        this.status = NotificationStatus.NEW;
        this.attempts = 0;
    }

    public static Notification from(
            PaymentStatusChangedEvent event,
            Map<String, Object> payload
    ) {
        String message = switch (event.paymentStatus()) {
            case SUCCEEDED ->
                    "Платёж успешно выполнен";

            case FAILED ->
                    "Не удалось выполнить платёж";

            case UNKNOWN ->
                    "Не удалось определить результат платежа";
        };

        return new Notification(
                event.eventId(),
                event.paymentId(),
                event.payload().userId(),
                event.paymentStatus(),
                payload,
                message
        );
    }

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = NotificationStatus.NEW;
        }

        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getUserId() {
        return userId;
    }

    public PaymentEventStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Map<String, Object> getPayload() {
        return Map.copyOf(payload);
    }

    public String getMessage() {
        return message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}