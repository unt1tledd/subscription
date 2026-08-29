package com.example.subscription.usecase;

import com.example.subscription.adapter.PaymentGrpcClient;
import com.example.subscription.dto.subscription.CheckoutResult;
import com.example.subscription.dto.subscription.PaymentResult;
import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;
import com.example.subscription.errors.subscription.SubscriptionNotFoundException;
import com.example.subscription.repository.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class SubscriptionService {
    private final static int MAX_PAGE_SIZE = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentGrpcClient paymentClient;
    private final TransactionTemplate transactionTemplate;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            PaymentGrpcClient paymentClient,
            TransactionTemplate transactionTemplate
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentClient = paymentClient;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public Page<Subscription> getUserSubs(long userId, SubscriptionStatus status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative"
            );
        }

        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Subscription> subs;

        if (status == null) {
            subs = subscriptionRepository.findAllByUserId(userId, pageable);
        } else {
            subs = subscriptionRepository.findAllByUserIdAndStatus(userId, status, pageable);
        }

        return subs;
    }

    public Subscription getSub(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));
    }

    @Transactional
    public Subscription cancelAutoRenew(UUID id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));

        sub.cancelAutoRenew();

        return sub;
    }

    @Transactional
    public Subscription addAutoRenew(UUID id) {
        Subscription sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));

        sub.addAutoRenew();

        return sub;
    }

    @Transactional
    public Subscription create(Subscription sub) {
        String idempotencyKey = sub.getIdempotencyKey();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }

        var existingSubscription = subscriptionRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existingSubscription.isPresent()) {
            return existingSubscription.get();
        }

        if (sub.getUserId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        if (sub.getPlan() == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        if (!sub.getPlan().isActive()) {
            throw new IllegalArgumentException(
                    "Cannot create subscription for inactive plan"
            );
        }

        return subscriptionRepository.save(sub);
    }

    private record CheckoutData(
            UUID subscriptionId,
            long userId,
            long amount,
            String currency
    ) {
    }

    public CheckoutResult checkout(
            UUID id,
            String paymentMethodId,
            String idempotencyKey
    ) {
        CheckoutData data = transactionTemplate.execute(status -> {
            Subscription sub = subscriptionRepository.findById(id)
                    .orElseThrow(() -> new SubscriptionNotFoundException(id));

            sub.startPayment();

            return new CheckoutData(
                    sub.getId(),
                    sub.getUserId(),
                    sub.getPlan().getPrice(),
                    sub.getPlan().getCurrency()
            );
        });

        if (data == null) {
            throw new IllegalStateException("Could not prepare subscription checkout");
        }

        PaymentResult payment = paymentClient.createPayment(
                data.subscriptionId(),
                data.userId(),
                data.amount(),
                data.currency(),
                paymentMethodId,
                idempotencyKey
        );

        Subscription subscription =
                transactionTemplate.execute(status -> {
                    Subscription current = subscriptionRepository.findById(id)
                            .orElseThrow(() -> new SubscriptionNotFoundException(id));

                    switch (payment.status()) {
                        case SUCCEEDED -> current.activate();

                        case FAILED -> current.markPaymentFailed();

                        case PENDING -> {}
                    }

                    return current;
                });

        if (subscription == null) {
            throw new IllegalStateException("Could not update subscription after payment");
        }

        return new CheckoutResult(subscription, payment);
    }
}
