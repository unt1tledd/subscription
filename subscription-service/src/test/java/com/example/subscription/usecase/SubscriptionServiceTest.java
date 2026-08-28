package com.example.subscription.usecase;

import com.example.subscription.adapter.PaymentGrpcClient;
import com.example.subscription.dto.subscription.CheckoutResult;
import com.example.subscription.dto.subscription.PaymentResult;
import com.example.subscription.entity.Plan;
import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;
import com.example.subscription.errors.subscription.SubscriptionNotFoundException;
import com.example.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentGrpcClient paymentClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private Subscription subscription;

    @Mock
    private Plan plan;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                paymentClient,
                transactionTemplate
        );
    }

    private void mockTransactions() {
        doAnswer(invocation -> {
            TransactionCallback<?> callback =
                    invocation.getArgument(0);

            TransactionStatus status =
                    mock(TransactionStatus.class);

            return callback.doInTransaction(status);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    void getSub_returnsSubscription_whenSubscriptionExists() {
        UUID id = UUID.randomUUID();

        when(subscriptionRepository.findById(id))
                .thenReturn(Optional.of(subscription));

        Subscription result = subscriptionService.getSub(id);

        assertSame(subscription, result);

        verify(subscriptionRepository).findById(id);
    }

    @Test
    void getSub_throwsException_whenSubscriptionDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(subscriptionRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                SubscriptionNotFoundException.class,
                () -> subscriptionService.getSub(id)
        );

        verify(subscriptionRepository).findById(id);
    }

    @Test
    void getUserSubs_returnsAllUserSubscriptions_whenStatusIsNull() {
        long userId = 42L;

        Page<Subscription> expectedPage =
                new PageImpl<>(List.of(subscription));

        when(subscriptionRepository.findAllByUserId(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(expectedPage);

        Page<Subscription> result =
                subscriptionService.getUserSubs(
                        userId,
                        null,
                        0,
                        10
                );

        assertSame(expectedPage, result);

        verify(subscriptionRepository).findAllByUserId(
                eq(userId),
                argThat(pageable ->
                        pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 10
                                && pageable.getSort()
                                .getOrderFor("createdAt") != null
                )
        );

        verify(subscriptionRepository, never())
                .findAllByUserIdAndStatus(
                        anyLong(),
                        any(),
                        any()
                );
    }

    @Test
    void getUserSubs_filtersSubscriptionsByStatus() {
        long userId = 42L;
        SubscriptionStatus status = SubscriptionStatus.ACTIVE;

        Page<Subscription> expectedPage =
                new PageImpl<>(List.of(subscription));

        when(subscriptionRepository.findAllByUserIdAndStatus(
                eq(userId),
                eq(status),
                any(Pageable.class)
        )).thenReturn(expectedPage);

        Page<Subscription> result =
                subscriptionService.getUserSubs(
                        userId,
                        status,
                        0,
                        20
                );

        assertSame(expectedPage, result);

        verify(subscriptionRepository)
                .findAllByUserIdAndStatus(
                        eq(userId),
                        eq(status),
                        any(Pageable.class)
                );

        verify(subscriptionRepository, never())
                .findAllByUserId(
                        anyLong(),
                        any()
                );
    }

    @Test
    void getUserSubs_throwsException_whenPageIsNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getUserSubs(
                        42L,
                        null,
                        -1,
                        10
                )
        );

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void getUserSubs_throwsException_whenSizeIsZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getUserSubs(
                        42L,
                        null,
                        0,
                        0
                )
        );

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void getUserSubs_throwsException_whenSizeExceedsMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getUserSubs(
                        42L,
                        null,
                        0,
                        101
                )
        );

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void create_savesSubscription_whenDataIsValid() {
        String idempotencyKey =
                "create-subscription-123";

        when(subscription.getIdempotencyKey())
                .thenReturn(idempotencyKey);

        when(subscriptionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(subscription.getUserId()).thenReturn(42L);
        when(subscription.getPlan()).thenReturn(plan);
        when(plan.isActive()).thenReturn(true);

        when(subscriptionRepository.save(subscription))
                .thenReturn(subscription);

        Subscription result =
                subscriptionService.create(subscription);

        assertSame(subscription, result);

        verify(subscriptionRepository)
                .findByIdempotencyKey(idempotencyKey);

        verify(subscriptionRepository)
                .save(subscription);
    }

    @Test
    void create_throwsException_whenPlanIsInactive() {
        String idempotencyKey = "inactive-plan-key";

        when(subscription.getIdempotencyKey())
                .thenReturn(idempotencyKey);

        when(subscriptionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(subscription.getUserId()).thenReturn(42L);
        when(subscription.getPlan()).thenReturn(plan);
        when(plan.isActive()).thenReturn(false);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subscriptionService.create(
                                subscription
                        )
                );

        assertEquals(
                "Cannot create subscription for inactive plan",
                exception.getMessage()
        );

        verify(subscriptionRepository, never())
                .save(any());
    }

    @Test
    void create_returnsExistingSubscription_whenIdempotencyKeyAlreadyExists() {
        String idempotencyKey = "duplicate-key";

        Subscription existingSubscription =
                mock(Subscription.class);

        when(subscription.getIdempotencyKey())
                .thenReturn(idempotencyKey);

        when(subscriptionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(
                Optional.of(existingSubscription)
        );

        Subscription result =
                subscriptionService.create(subscription);

        assertSame(existingSubscription, result);

        verify(subscriptionRepository)
                .findByIdempotencyKey(idempotencyKey);

        verify(subscriptionRepository, never())
                .save(any());
    }

    @Test
    void cancelAutoRenew_disablesAutoRenew() {
        UUID id = UUID.randomUUID();

        when(subscriptionRepository.findById(id))
                .thenReturn(Optional.of(subscription));

        Subscription result =
                subscriptionService.cancelAutoRenew(id);

        assertSame(subscription, result);

        verify(subscription).cancelAutoRenew();
    }

    @Test
    void addAutoRenew_enablesAutoRenew() {
        UUID id = UUID.randomUUID();

        when(subscriptionRepository.findById(id))
                .thenReturn(Optional.of(subscription));

        Subscription result =
                subscriptionService.addAutoRenew(id);

        assertSame(subscription, result);

        verify(subscription).addAutoRenew();
    }

    @Test
    void checkout_activatesSubscription_whenPaymentSucceeds() {
        mockTransactions();

        UUID subscriptionId = UUID.randomUUID();
        String paymentMethodId = "test-card";
        String idempotencyKey = "checkout-123";

        prepareSubscriptionForCheckout(subscriptionId);

        PaymentResult payment = new PaymentResult(
                "payment-123",
                PaymentResult.Status.SUCCEEDED,
                49_900L,
                "RUB",
                Instant.now()
        );

        when(paymentClient.createPayment(
                subscriptionId,
                42L,
                49_900L,
                "RUB",
                paymentMethodId,
                idempotencyKey
        )).thenReturn(payment);

        CheckoutResult result = subscriptionService.checkout(
                subscriptionId,
                paymentMethodId,
                idempotencyKey
        );

        assertSame(subscription, result.subscription());
        assertSame(payment, result.payment());

        verify(subscription).startPayment();
        verify(subscription).activate();
        verify(subscription, never()).markPaymentFailed();

        verify(paymentClient).createPayment(
                subscriptionId,
                42L,
                49_900L,
                "RUB",
                paymentMethodId,
                idempotencyKey
        );
    }

    @Test
    void checkout_marksPaymentFailed_whenPaymentFails() {
        mockTransactions();

        UUID subscriptionId = UUID.randomUUID();

        prepareSubscriptionForCheckout(subscriptionId);

        PaymentResult payment = new PaymentResult(
                "payment-123",
                PaymentResult.Status.FAILED,
                49_900L,
                "RUB",
                Instant.now()
        );

        when(paymentClient.createPayment(
                subscriptionId,
                42L,
                49_900L,
                "RUB",
                "test-card",
                "checkout-123"
        )).thenReturn(payment);

        CheckoutResult result = subscriptionService.checkout(
                subscriptionId,
                "test-card",
                "checkout-123"
        );

        assertSame(payment, result.payment());

        verify(subscription).startPayment();
        verify(subscription).markPaymentFailed();
        verify(subscription, never()).activate();
    }

    @Test
    void checkout_leavesSubscriptionPending_whenPaymentIsPending() {
        mockTransactions();

        UUID subscriptionId = UUID.randomUUID();

        prepareSubscriptionForCheckout(subscriptionId);

        PaymentResult payment = new PaymentResult(
                "payment-123",
                PaymentResult.Status.PENDING,
                49_900L,
                "RUB",
                Instant.now()
        );

        when(paymentClient.createPayment(
                subscriptionId,
                42L,
                49_900L,
                "RUB",
                "test-card",
                "checkout-123"
        )).thenReturn(payment);

        CheckoutResult result = subscriptionService.checkout(
                subscriptionId,
                "test-card",
                "checkout-123"
        );

        assertSame(payment, result.payment());

        verify(subscription).startPayment();
        verify(subscription, never()).activate();
        verify(subscription, never()).markPaymentFailed();
    }

    @Test
    void checkout_leavesSubscriptionPending_whenGrpcCallFails() {
        mockTransactions();

        UUID subscriptionId = UUID.randomUUID();

        prepareSubscriptionForCheckout(subscriptionId);

        RuntimeException grpcException =
                new RuntimeException("Payment service unavailable");

        when(paymentClient.createPayment(
                subscriptionId,
                42L,
                49_900L,
                "RUB",
                "test-card",
                "checkout-123"
        )).thenThrow(grpcException);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> subscriptionService.checkout(
                        subscriptionId,
                        "test-card",
                        "checkout-123"
                )
        );

        assertSame(grpcException, thrown);

        verify(subscription).startPayment();
        verify(subscription, never()).activate();
        verify(subscription, never()).markPaymentFailed();

        verify(transactionTemplate, times(1))
                .execute(any());
    }

    private void prepareSubscriptionForCheckout(
            UUID subscriptionId
    ) {
        when(subscriptionRepository.findById(subscriptionId))
                .thenReturn(Optional.of(subscription));

        when(subscription.getId()).thenReturn(subscriptionId);
        when(subscription.getUserId()).thenReturn(42L);
        when(subscription.getPlan()).thenReturn(plan);

        when(plan.getPrice()).thenReturn(49_900L);
        when(plan.getCurrency()).thenReturn("RUB");
    }
}