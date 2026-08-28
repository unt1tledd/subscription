package com.example.payment.usecase;

import com.example.payment.entity.OutboxEvent;
import com.example.payment.entity.OutboxStatus;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.errors.IdempotencyConflictException;
import com.example.payment.errors.PaymentNotFoundException;
import com.example.payment.processor.PaymentProcessor;
import com.example.payment.processor.ProcessingResult;
import com.example.payment.repository.OutboxRepository;
import com.example.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class PaymentService {
    private static final int MAX_STATUS_CHECK_ATTEMPTS = 5;

    private final PaymentRepository paymentRepository;
    private final PaymentProcessor paymentProcessor;
    private final TransactionTemplate transactionTemplate;
    private final OutboxRepository outbox;

    public PaymentService(PaymentRepository paymentRepository, PaymentProcessor paymentProcessor, TransactionTemplate transactionTemplate, OutboxRepository outbox) {
        this.paymentRepository = paymentRepository;
        this.paymentProcessor = paymentProcessor;
        this.transactionTemplate = transactionTemplate;
        this.outbox = outbox;
    }

    private record CreationDecision(
            UUID paymentId,
            boolean created
    ) {}

    public Payment create(
            long userId,
            long amount,
            String currency,
            String paymentMethodId,
            String idempotencyKey
    ) {
        CreationDecision decision;

        try {
            decision = transactionTemplate.execute(tx -> {
                Payment existing = paymentRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

                if (existing != null) {
                    validateIdempotency(
                            existing,
                            userId,
                            amount,
                            currency,
                            idempotencyKey
                    );

                    return new CreationDecision(existing.getId(), false);
                }

                Payment payment = new Payment(
                        userId,
                        amount,
                        currency,
                        idempotencyKey
                );

                paymentRepository.saveAndFlush(payment);

                return new CreationDecision(payment.getId(), true);
            });
        } catch (DataIntegrityViolationException exception) {
            decision = transactionTemplate.execute(tx -> {
                Payment raced = paymentRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> exception);

                validateIdempotency(
                        raced,
                        userId,
                        amount,
                        currency,
                        idempotencyKey
                );

                return new CreationDecision(
                        raced.getId(),
                        false
                );
            });
        }

        if (decision == null) {
            throw new IllegalStateException("Transaction returned null");
        }

        if (!decision.created()) {
            return findPayment(decision.paymentId());
        }

        ProcessingResult result;

        try {
            result = paymentProcessor.process(
                    paymentMethodId,
                    amount,
                    currency,
                    idempotencyKey
            );
        } catch (RuntimeException exception) {
            return findPayment(decision.paymentId());
        }

        CreationDecision finalDecision = decision;
        transactionTemplate.executeWithoutResult(tx -> {
            Payment current = paymentRepository
                    .findByIdForUpdate(finalDecision.paymentId())
                    .orElseThrow(() ->
                            new PaymentNotFoundException(
                                    finalDecision.paymentId()
                            )
                    );

            if (current.getStatus() != PaymentStatus.PENDING) {
                return;
            }

            switch (result.status()) {
                case SUCCEEDED -> {
                    current.markSucceeded();
                    outbox.save(OutboxEvent.paymentStatusChanged(current));
                }

                case FAILED -> {
                    current.markFailed(result.failureCode());
                    outbox.save(OutboxEvent.paymentStatusChanged(current));
                }

                case PENDING -> {}
            }
        });

        return findPayment(decision.paymentId());
    }

    private void validateIdempotency(
            Payment payment,
            long userId,
            long amount,
            String currency,
            String idempotencyKey
    ) {
        if (!payment.hasSameParameters(
                userId,
                amount,
                currency
        )) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
    }

    public Payment get(UUID paymentId) {
        return findPayment(paymentId);
    }

    public void refreshStatus(UUID paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        String idempotencyKey = payment.getIdempotencyKey();

        if (idempotencyKey == null) {
            return;
        }

        ProcessingResult result;

        try {
            result = paymentProcessor.getStatus(idempotencyKey);
        } catch (RuntimeException ignored) {
            recordFailedStatusCheck(paymentId);
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Payment current = paymentRepository
                    .findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentId));

            if (current.getStatus() != PaymentStatus.PENDING) {
                return;
            }

            applyCheckResult(current, result);
        });
    }

    private void applyCheckResult(
            Payment payment,
            ProcessingResult result
    ) {
        switch (result.status()) {
            case SUCCEEDED -> {
                payment.markSucceeded();
                outbox.save(OutboxEvent.paymentStatusChanged(payment));
            }

            case FAILED -> {
                payment.markFailed(result.failureCode());
                outbox.save(OutboxEvent.paymentStatusChanged(payment));
            }

            case PENDING -> {
                payment.incrementStatusCheckAttempts();

                if (payment.getStatusCheckAttempts() >= MAX_STATUS_CHECK_ATTEMPTS) {
                    payment.markUnknown();
                    outbox.save(OutboxEvent.paymentStatusChanged(payment));
                }
            }
        }
    }

    private void recordFailedStatusCheck(UUID paymentId) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            Payment payment = paymentRepository
                    .findByIdForUpdate(paymentId)
                    .orElseThrow(() ->
                            new PaymentNotFoundException(paymentId)
                    );

            if (payment.getStatus() != PaymentStatus.PENDING) {
                return;
            }

            payment.incrementStatusCheckAttempts();

            if (payment.getStatusCheckAttempts() >= MAX_STATUS_CHECK_ATTEMPTS) {
                payment.markUnknown();
                outbox.save(OutboxEvent.paymentStatusChanged(payment));
            }
        });
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

}
