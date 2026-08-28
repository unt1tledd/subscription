package com.example.payment.workers;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.usecase.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class PendingPaymentWorker {
    private static final Duration PENDING_DELAY = Duration.ofMinutes(2);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PendingPaymentWorker(
            PaymentRepository paymentRepository,
            PaymentService paymentService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${payment.pending-check-delay-ms:30000}")
    public void checkPendingPayments() {
        Instant cutoff = Instant.now()
                .minus(PENDING_DELAY);

        List<Payment> payments = paymentRepository
                        .findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                                PaymentStatus.PENDING,
                                cutoff
                        );

        for (Payment payment : payments) {
            paymentService.refreshStatus(payment.getId());
        }
    }
}