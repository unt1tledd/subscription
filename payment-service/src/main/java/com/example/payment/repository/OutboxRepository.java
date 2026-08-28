package com.example.payment.repository;

import com.example.payment.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(
            value = """
                    SELECT *
                    FROM outbox_events
                    WHERE status IN ('NEW', 'FAILED')
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY next_attempt_at, created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEvent> findReadyForProcessing(@Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.id = :id
            """)
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(
            value = """
                    UPDATE outbox_events
                    SET status = 'FAILED',
                        attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP,
                        last_error = 'PROCESSING_TIMEOUT',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE status = 'PROCESSING'
                      AND updated_at < :cutoff
                    """,
            nativeQuery = true
    )
    int recoverStuckProcessing(@Param("cutoff") Instant cutoff);

    List<OutboxEvent> findAllByPaymentId(UUID paymentId);
}
