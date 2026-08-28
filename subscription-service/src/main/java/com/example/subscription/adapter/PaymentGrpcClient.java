package com.example.subscription.adapter;

import com.example.payment.v1.CreatePaymentRequest;
import com.example.payment.v1.CreatePaymentResponse;
import com.example.payment.v1.Payment;
import com.example.subscription.dto.subscription.PaymentResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.example.payment.v1.PaymentServiceGrpc;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PaymentGrpcClient {
    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;
    private final Logger log = LoggerFactory.getLogger(PaymentGrpcClient.class);

    private final int maxAttempts;
    private final long initialBackoffMs;

    public PaymentGrpcClient(
            @Value("${payment.grpc.host}") String host,
                        @Value ("${payment.grpc.port}") int port) {
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PaymentServiceGrpc.newBlockingStub(channel);
        this.maxAttempts = Integer.parseInt(System.getProperty("payment.client.maxAttempts", "3"));
        this.initialBackoffMs = Long.parseLong(System.getProperty("payment.client.initialBackoffMs", "500"));
    }

    public PaymentResult createPayment(
            UUID subscriptionId,
            long userId,
            long amount,
            String currency,
            String paymentMethodId,
            String idempotencyKey
    ) {
        CreatePaymentRequest request = CreatePaymentRequest.newBuilder()
                .setSubscriptionId(subscriptionId.toString())
                .setUserId(userId)
                .setAmount(amount)
                .setCurrency(currency)
                .setPaymentMethodId(paymentMethodId)
                .setIdempotencyKey(idempotencyKey)
                .build();

        int attempt = 0;
        long backoff = initialBackoffMs;
        while (true) {
                attempt++;
            try {
                CreatePaymentResponse response = stub
                                .withDeadlineAfter(3, TimeUnit.SECONDS)
                                .createPayment(request);

                if (!response.hasPayment()) {
                        throw new IllegalStateException(
                                        "Payment service returned response without payment"
                        );
                }

                return mapPayment(response.getPayment());
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                        log.error("Payment create failed after {} attempts (subscription={}, user={})", attempt, subscriptionId, userId, e);
                        throw e;
                }

                log.warn("Payment create attempt {} failed, will retry after {}ms (subscription={}, user={})", attempt, backoff, subscriptionId, userId, e);

                try {
                        Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                }

                backoff = Math.min(backoff * 2, 5000L);
            }
        }
    }

    private PaymentResult mapPayment(Payment payment) {
        Instant createdAt = Instant.ofEpochSecond(
                payment.getCreatedAt().getSeconds(),
                payment.getCreatedAt().getNanos()
        );

        return new PaymentResult(
                payment.getId(),
                mapStatus(payment.getStatus()),
                payment.getAmount(),
                payment.getCurrency(),
                createdAt
        );
    }

    private PaymentResult.Status mapStatus(
            com.example.payment.v1.PaymentStatus status
    ) {
        return switch (status) {
            case PAYMENT_STATUS_PENDING ->
                    PaymentResult.Status.PENDING;

            case PAYMENT_STATUS_SUCCEEDED ->
                    PaymentResult.Status.SUCCEEDED;

            case PAYMENT_STATUS_FAILED ->
                    PaymentResult.Status.FAILED;

            case PAYMENT_STATUS_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalStateException(
                            "Unknown payment status: " + status
                    );
        };
    }


    @PreDestroy
    public void shutdown() {
        channel.shutdown();

        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
