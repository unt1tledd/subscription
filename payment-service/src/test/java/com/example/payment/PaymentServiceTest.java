package com.example.payment;

import com.example.payment.entity.OutboxStatus;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.errors.IdempotencyConflictException;
import com.example.payment.errors.PaymentNotFoundException;
import com.example.payment.processor.PaymentProcessor;
import com.example.payment.processor.ProcessingResult;
import com.example.payment.repository.OutboxRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.usecase.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long USER_ID = 42L;
    private static final long AMOUNT = 10_000L;
    private static final String CURRENCY = "RUB";
    private static final String KEY = "payment-key";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProcessor paymentProcessor;

    @Mock
    private Payment payment;

    @Mock
    private OutboxRepository outboxRepository;

    private RecordingTransactionTemplate transactionTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        transactionTemplate = new RecordingTransactionTemplate();
        paymentService = new PaymentService(
                paymentRepository,
                paymentProcessor,
                transactionTemplate,
                outboxRepository
        );
    }

    @Test
    void get_returnsPayment_whenPaymentExists() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.get(paymentId);

        assertSame(payment, result);
        verify(paymentRepository).findById(paymentId);
    }

    @Test
    void get_throwsException_whenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.get(paymentId)
        );
    }

    @Test
    void create_returnsExistingPayment_forRepeatedRequest() {
        UUID paymentId = UUID.randomUUID();
        executeValueTransactions();

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(payment));
        when(payment.hasSameParameters(USER_ID, AMOUNT, CURRENCY))
                .thenReturn(true);
        when(payment.getId()).thenReturn(paymentId);
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-success",
                KEY
        );

        assertSame(payment, result);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(paymentProcessor);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void create_throwsConflict_whenExistingKeyHasDifferentParameters() {
        executeValueTransactions();

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(payment));
        when(payment.hasSameParameters(USER_ID, AMOUNT, CURRENCY))
                .thenReturn(false);

        assertThrows(
                IdempotencyConflictException.class,
                () -> paymentService.create(
                        USER_ID,
                        AMOUNT,
                        CURRENCY,
                        "test-success",
                        KEY
                )
        );

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verifyNoInteractions(paymentProcessor);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void create_marksPaymentSucceeded_whenProcessorSucceeds() {
        UUID paymentId = UUID.randomUUID();
        AtomicReference<Payment> savedPayment = prepareNewPayment(paymentId, true);
        executeValueTransactions();
        executeVoidTransactions();

        when(paymentProcessor.process(
                "test-success",
                AMOUNT,
                CURRENCY,
                KEY
        )).thenReturn(ProcessingResult.succeeded());

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-success",
                KEY
        );

        assertSame(savedPayment.get(), result);
        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(paymentProcessor).process(
                "test-success",
                AMOUNT,
                CURRENCY,
                KEY
        );
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.SUCCEEDED
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void create_marksPaymentFailed_whenProcessorDeclinesPayment() {
        UUID paymentId = UUID.randomUUID();
        AtomicReference<Payment> savedPayment = prepareNewPayment(paymentId, true);
        executeValueTransactions();
        executeVoidTransactions();

        when(paymentProcessor.process(
                "test-failed",
                AMOUNT,
                CURRENCY,
                KEY
        )).thenReturn(ProcessingResult.failed("PAYMENT_DECLINED"));

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-failed",
                KEY
        );

        assertSame(savedPayment.get(), result);
        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertEquals("PAYMENT_DECLINED", result.getFailureCode());
        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.FAILED
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void create_leavesPaymentPending_whenProcessorReturnsPending() {
        UUID paymentId = UUID.randomUUID();
        AtomicReference<Payment> savedPayment = prepareNewPayment(paymentId, true);
        executeValueTransactions();
        executeVoidTransactions();

        when(paymentProcessor.process(
                "test-pending",
                AMOUNT,
                CURRENCY,
                KEY
        )).thenReturn(ProcessingResult.pending());

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-pending",
                KEY
        );

        assertSame(savedPayment.get(), result);
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void create_leavesPaymentPending_whenProcessorThrowsException() {
        UUID paymentId = UUID.randomUUID();
        AtomicReference<Payment> savedPayment = prepareNewPayment(paymentId, false);
        executeValueTransactions();

        when(paymentProcessor.process(
                "test-error",
                AMOUNT,
                CURRENCY,
                KEY
        )).thenThrow(new RuntimeException("Provider unavailable"));

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-error",
                KEY
        );

        assertSame(savedPayment.get(), result);
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertEquals(0, transactionTemplate.executeWithoutResultCalls());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void create_returnsRacedPayment_whenConcurrentInsertUsesSameParameters() {
        UUID paymentId = UUID.randomUUID();
        executeValueTransactions();

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(payment.hasSameParameters(USER_ID, AMOUNT, CURRENCY))
                .thenReturn(true);
        when(payment.getId()).thenReturn(paymentId);
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        Payment result = paymentService.create(
                USER_ID,
                AMOUNT,
                CURRENCY,
                "test-success",
                KEY
        );

        assertSame(payment, result);
        assertEquals(2, transactionTemplate.executeCalls());
        verifyNoInteractions(paymentProcessor);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void create_throwsConflict_whenConcurrentInsertUsesDifferentParameters() {
        executeValueTransactions();

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(payment.hasSameParameters(USER_ID, AMOUNT, CURRENCY))
                .thenReturn(false);

        assertThrows(
                IdempotencyConflictException.class,
                () -> paymentService.create(
                        USER_ID,
                        AMOUNT,
                        CURRENCY,
                        "test-success",
                        KEY
                )
        );

        verifyNoInteractions(paymentProcessor);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void refreshStatus_marksPendingPaymentSucceeded() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenReturn(ProcessingResult.succeeded());

        paymentService.refreshStatus(paymentId);

        verify(paymentProcessor).getStatus(KEY);
        assertEquals(PaymentStatus.SUCCEEDED, pendingPayment.getStatus());
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.SUCCEEDED
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void refreshStatus_marksPendingPaymentFailed_whenProviderDeclinesIt() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenReturn(ProcessingResult.failed("PAYMENT_DECLINED"));

        paymentService.refreshStatus(paymentId);

        assertEquals(PaymentStatus.FAILED, pendingPayment.getStatus());
        assertEquals("PAYMENT_DECLINED", pendingPayment.getFailureCode());
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.FAILED
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void refreshStatus_doesNothing_whenPaymentIsAlreadyFinal() {
        UUID paymentId = UUID.randomUUID();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(payment.getStatus())
                .thenReturn(PaymentStatus.SUCCEEDED);

        paymentService.refreshStatus(paymentId);

        verifyNoInteractions(paymentProcessor);
        assertEquals(0, transactionTemplate.totalCalls());
        verifyNoInteractions(outboxRepository);
        verify(paymentRepository, never()).findByIdForUpdate(paymentId);
    }

    @Test
    void refreshStatus_incrementsAttempts_whenProviderStillPending() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenReturn(ProcessingResult.pending());

        paymentService.refreshStatus(paymentId);

        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        assertEquals(1, pendingPayment.getStatusCheckAttempts());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void refreshStatus_marksUnknown_afterMaximumAttempts() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        setStatusCheckAttempts(pendingPayment, 4);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenReturn(ProcessingResult.pending());

        paymentService.refreshStatus(paymentId);

        assertEquals(PaymentStatus.UNKNOWN, pendingPayment.getStatus());
        assertEquals(5, pendingPayment.getStatusCheckAttempts());
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.UNKNOWN
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void refreshStatus_recordsAttempt_whenProviderThrowsException() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenThrow(new RuntimeException("Provider unavailable"));

        paymentService.refreshStatus(paymentId);

        assertEquals(PaymentStatus.PENDING, pendingPayment.getStatus());
        assertEquals(1, pendingPayment.getStatusCheckAttempts());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void refreshStatus_marksUnknown_whenProviderErrorsExhaustAttempts() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = newPayment(paymentId);
        setStatusCheckAttempts(pendingPayment, 4);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentProcessor.getStatus(KEY))
                .thenThrow(new RuntimeException("Provider unavailable"));

        paymentService.refreshStatus(paymentId);

        assertEquals(PaymentStatus.UNKNOWN, pendingPayment.getStatus());
        assertEquals(5, pendingPayment.getStatusCheckAttempts());
        verify(outboxRepository).save(
                argThat(event ->
                        event.getPaymentId().equals(paymentId)
                                && event.getPaymentStatus()
                                == PaymentStatus.UNKNOWN
                                && event.getStatus()
                                == OutboxStatus.NEW
                )
        );
    }

    @Test
    void refreshStatus_doesNotOverwriteStatusChangedByAnotherThread() {
        UUID paymentId = UUID.randomUUID();
        Payment current = mock(Payment.class);
        executeVoidTransactions();

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(payment.getIdempotencyKey()).thenReturn(KEY);
        when(paymentProcessor.getStatus(KEY))
                .thenReturn(ProcessingResult.failed("PAYMENT_DECLINED"));

        when(paymentRepository.findByIdForUpdate(paymentId))
                .thenReturn(Optional.of(current));
        when(current.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);

        paymentService.refreshStatus(paymentId);

        verify(current, never()).markFailed(anyString());
        verify(current, never()).markSucceeded();
        verify(current, never()).incrementStatusCheckAttempts();
        verifyNoInteractions(outboxRepository);
    }

    private AtomicReference<Payment> prepareNewPayment(
            UUID paymentId,
            boolean expectStatusUpdate
    ) {
        AtomicReference<Payment> savedPayment = new AtomicReference<>();

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment value = invocation.getArgument(0);
                    ReflectionTestUtils.setField(value, "id", paymentId);
                    savedPayment.set(value);
                    return value;
                });
        if (expectStatusUpdate) {
            when(paymentRepository.findByIdForUpdate(paymentId))
                    .thenAnswer(invocation -> Optional.of(savedPayment.get()));
        }
        when(paymentRepository.findById(paymentId))
                .thenAnswer(invocation -> Optional.of(savedPayment.get()));

        return savedPayment;
    }

    private Payment newPayment(UUID paymentId) {
        Payment value = new Payment(
                USER_ID,
                AMOUNT,
                CURRENCY,
                KEY
        );

        ReflectionTestUtils.setField(
                value,
                "id",
                paymentId
        );

        return value;
    }

    private void setStatusCheckAttempts(
            Payment payment,
            int attempts
    ) {
        ReflectionTestUtils.setField(
                payment,
                "statusCheckAttempts",
                attempts
        );
    }

    private void executeValueTransactions() {
        // Transactions execute immediately in RecordingTransactionTemplate.
    }

    private void executeVoidTransactions() {
        // Transactions execute immediately in RecordingTransactionTemplate.
    }

    private static final class RecordingTransactionTemplate extends TransactionTemplate {
        private int executeCalls;
        private int executeWithoutResultCalls;

        @Override
        public <T> T execute(TransactionCallback<T> action)
                throws TransactionException {
            executeCalls++;
            return action.doInTransaction(mock(TransactionStatus.class));
        }

        @Override
        public void executeWithoutResult(
                Consumer<TransactionStatus> action
        ) throws TransactionException {
            executeWithoutResultCalls++;
            action.accept(mock(TransactionStatus.class));
        }

        int executeCalls() {
            return executeCalls;
        }

        int executeWithoutResultCalls() {
            return executeWithoutResultCalls;
        }

        int totalCalls() {
            return executeCalls + executeWithoutResultCalls;
        }
    }
}
