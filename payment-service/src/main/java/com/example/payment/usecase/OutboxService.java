package com.example.payment.usecase;

import com.example.payment.dto.kafka.ClaimedOutboxEvent;
import com.example.payment.entity.OutboxEvent;
import com.example.payment.entity.OutboxStatus;
import com.example.payment.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;

    public OutboxService(
            OutboxRepository outboxRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ClaimedOutboxEvent> claimBatch(int limit) {
        List<ClaimedOutboxEvent> result = transactionTemplate.execute(tx -> {
            List<OutboxEvent> events = outboxRepository.findReadyForProcessing(limit);

            events.forEach(OutboxEvent::markProcessing);

            return events.stream()
                    .map(ClaimedOutboxEvent::from)
                    .toList();
        });

        if (result == null) {
            throw new IllegalStateException("Transaction returned null");
        }

        return result;
    }

    public void markPublished(UUID eventId) {
        transactionTemplate.executeWithoutResult(tx -> {
            OutboxEvent event = findForUpdate(eventId);

            if (event.getStatus() != OutboxStatus.PROCESSING) return;

            event.markPublished();
        });
    }

    public void markFailed(
            UUID eventId,
            String error
    ) {
        transactionTemplate.executeWithoutResult(tx -> {
            OutboxEvent event = findForUpdate(eventId);

            if (event.getStatus() != OutboxStatus.PROCESSING) return;

            event.markFailedWithBackoff(error);
        });
    }

    public int recoverStuckEvents(Duration processingTimeout) {
        Integer recovered = transactionTemplate.execute(tx ->
                outboxRepository.recoverStuckProcessing(Instant.now().minus(processingTimeout))
        );

        return recovered == null ? 0 : recovered;
    }

    private OutboxEvent findForUpdate(
            UUID eventId
    ) {
        return outboxRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                    new IllegalStateException("Outbox event not found: " + eventId)
                );
    }
}