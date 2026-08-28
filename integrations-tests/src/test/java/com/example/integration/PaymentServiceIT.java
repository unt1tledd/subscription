package com.example.integration;

import com.example.payment.PaymentApplication;
import com.example.payment.entity.OutboxEvent;
import com.example.payment.entity.OutboxStatus;
import com.example.payment.repository.OutboxRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.v1.CreatePaymentRequest;
import com.example.payment.v1.PaymentServiceGrpc;
import com.example.payment.v1.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = PaymentApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.flyway.locations=classpath:db/migrations/payment",
                "payment.outbox.topic=payment-status-changed-it",
                "payment.outbox.poll-delay-ms=100",
                "payment.outbox.recovery-delay-ms=600000",
                "payment.pending-check-delay-ms=600000"
        }
)
class PaymentServiceIT {

    private static final String TOPIC =
            "payment-status-changed-it";

    private static final int GRPC_PORT = findFreePort();

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            )
                    .withDatabaseName("payment_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka-native:3.8.0")
            );

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
        registry.add(
                "spring.kafka.bootstrap-servers",
                KAFKA::getBootstrapServers
        );
        registry.add(
                "grpc.server.port",
                () -> GRPC_PORT
        );
    }

    @Test
    void createsPaymentThroughGrpcAndPublishesKafkaEvent()
            throws Exception {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", GRPC_PORT)
                .usePlaintext()
                .build();

        try {
            String idempotencyKey =
                    "payment-" + UUID.randomUUID();

            PaymentServiceGrpc.PaymentServiceBlockingStub stub =
                    PaymentServiceGrpc.newBlockingStub(channel);

            var response = stub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .createPayment(
                            CreatePaymentRequest.newBuilder()
                                    .setSubscriptionId(
                                            UUID.randomUUID().toString()
                                    )
                                    .setUserId(42)
                                    .setAmount(19_900)
                                    .setCurrency("RUB")
                                    .setPaymentMethodId("test-success")
                                    .setIdempotencyKey(idempotencyKey)
                                    .build()
                    );

            assertThat(response.hasPayment()).isTrue();
            assertThat(response.getPayment().getStatus())
                    .isEqualTo(
                            PaymentStatus
                                    .PAYMENT_STATUS_SUCCEEDED
                    );

            UUID paymentId = UUID.fromString(
                    response.getPayment().getId()
            );

            assertThat(paymentRepository.findById(paymentId))
                    .isPresent();

            ConsumerRecord<String, String> record =
                    readSucceededPaymentEvent(paymentId);

            assertThat(record.key())
                    .isEqualTo(paymentId.toString());

            JsonNode event = objectMapper.readTree(record.value());

            assertThat(event.path("paymentId").asText())
                    .isEqualTo(paymentId.toString());
            assertThat(event.path("paymentStatus").asText())
                    .isEqualTo("SUCCEEDED");
            assertThat(
                    event.path("payload")
                            .path("userId")
                            .asLong()
            ).isEqualTo(42);

            await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> {
                        List<OutboxEvent> paymentEvents =
                                outboxRepository.findAll()
                                        .stream()
                                        .filter(value ->
                                                value.getPaymentId()
                                                        .equals(paymentId)
                                        )
                                        .toList();

                        assertThat(paymentEvents)
                                .isNotEmpty()
                                .allMatch(value ->
                                        value.getStatus()
                                                == OutboxStatus.PUBLISHED
                                );
                    });
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void repeatedRequestWithSameIdempotencyKeyReturnsSamePayment()
            throws Exception {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", GRPC_PORT)
                .usePlaintext()
                .build();

        try {
            PaymentServiceGrpc.PaymentServiceBlockingStub stub =
                    PaymentServiceGrpc.newBlockingStub(channel);

            String idempotencyKey =
                    "payment-" + UUID.randomUUID();

            CreatePaymentRequest request =
                    CreatePaymentRequest.newBuilder()
                            .setSubscriptionId(
                                    UUID.randomUUID().toString()
                            )
                            .setUserId(42)
                            .setAmount(19_900)
                            .setCurrency("RUB")
                            .setPaymentMethodId("test-success")
                            .setIdempotencyKey(idempotencyKey)
                            .build();

            var firstResponse = stub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .createPayment(request);

            var secondResponse = stub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .createPayment(request);

            assertThat(firstResponse.hasPayment()).isTrue();
            assertThat(secondResponse.hasPayment()).isTrue();

            assertThat(secondResponse.getPayment().getId())
                    .isEqualTo(
                            firstResponse.getPayment().getId()
                    );

            assertThat(secondResponse.getPayment().getStatus())
                    .isEqualTo(
                            firstResponse.getPayment().getStatus()
                    );

            UUID paymentId = UUID.fromString(
                    firstResponse.getPayment().getId()
            );

            await()
                    .atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> {
                        assertThat(
                                paymentRepository
                                        .countByIdempotencyKey(
                                                idempotencyKey
                                        )
                        ).isEqualTo(1);

                        List<OutboxEvent> events =
                                outboxRepository
                                        .findAllByPaymentId(paymentId);

                        assertThat(events).hasSize(1);

                        assertThat(events.get(0).getStatus())
                                .isEqualTo(
                                        OutboxStatus.PUBLISHED
                                );
                    });
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            );
        }
    }

    private ConsumerRecord<String, String>
    readSucceededPaymentEvent(UUID paymentId) {
        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(
                        KAFKA.getBootstrapServers(),
                        "payment-service-it-" + UUID.randomUUID(),
                        "false"
                );

        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        try (var consumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer()) {
            consumer.subscribe(List.of(TOPIC));

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(20).toNanos();

            while (System.nanoTime() < deadline) {
                var records = consumer.poll(
                        Duration.ofMillis(500)
                );

                for (ConsumerRecord<String, String> record : records) {
                    JsonNode event = readEvent(record.value());

                    boolean expectedPayment =
                            paymentId.toString().equals(
                                    event.path("paymentId").asText()
                            );

                    boolean succeeded =
                            "SUCCEEDED".equals(
                                    event.path("paymentStatus").asText()
                            );

                    if (expectedPayment && succeeded) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError(
                "SUCCEEDED event was not published for payment "
                        + paymentId
        );
    }

    private JsonNode readEvent(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Payment service published invalid JSON",
                    exception
            );
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not find a free TCP port",
                    exception
            );
        }
    }
}
