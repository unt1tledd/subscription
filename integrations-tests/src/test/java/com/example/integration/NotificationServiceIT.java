package com.example.integration;

import com.example.notification.NotificationApplication;
import com.example.notification.entity.NotificationStatus;
import com.example.notification.entity.PaymentEventStatus;
import com.example.notification.repository.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = NotificationApplication.class,
        properties = {
                "spring.main.web-application-type=none",

                "spring.flyway.locations="
                        + "classpath:db/migrations/notification",

                "spring.kafka.consumer.group-id="
                        + "notification-service-it",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.key-deserializer="
                        + "org.apache.kafka.common.serialization."
                        + "StringDeserializer",
                "spring.kafka.consumer.value-deserializer="
                        + "org.apache.kafka.common.serialization."
                        + "StringDeserializer",
                "spring.kafka.producer.key-serializer="
                        + "org.apache.kafka.common.serialization."
                        + "StringSerializer",
                "spring.kafka.producer.value-serializer="
                        + "org.apache.kafka.common.serialization."
                        + "StringSerializer",

                "notification.kafka.payment-topic="
                        + "payment-status-changed-it",
                "notification.kafka.group-id="
                        + "notification-service-it",
                "notification.kafka.dlt-suffix=.DLT",
                "notification.kafka.retry-delay-ms=50",
                "notification.kafka.retry-attempts=0",
                "notification.kafka.partitions=1"
        }
)
class NotificationServiceIT {

    private static final String TOPIC = "payment-status-changed-it";

    private static final String DLT_TOPIC = TOPIC + ".DLT";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("notification")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

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
    }

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
    }

    @Test
    void consumesPaymentEventAndStoresNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        String message = createPaymentEvent(
                eventId,
                paymentId,
                "SUCCEEDED"
        );

        sendMessage(paymentId, message);

        await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() ->
                        assertThat(
                                notificationRepository
                                        .existsByEventId(eventId)
                        ).isTrue()
                );

        var notification = notificationRepository.findByEventId(eventId).orElseThrow();

        assertThat(notification.getEventId()).isEqualTo(eventId);

        assertThat(notification.getPaymentId()).isEqualTo(paymentId);

        assertThat(notification.getUserId()).isEqualTo(42L);

        assertThat(notification.getPaymentStatus()).isEqualTo(PaymentEventStatus.SUCCEEDED);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.NEW);
    }

    @Test
    void doesNotCreateDuplicateNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        String message = createPaymentEvent(
                eventId,
                paymentId,
                "SUCCEEDED"
        );

        sendMessage(paymentId, message);
        sendMessage(paymentId, message);

        await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() ->
                        assertThat(
                                notificationRepository
                                        .countByEventId(eventId)
                        ).isEqualTo(1)
                );

        await()
                .during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(
                                notificationRepository
                                        .countByEventId(eventId)
                        ).isEqualTo(1)
                );
    }

    @Test
    void sendsInvalidMessageToDlt() throws Exception {
        long notificationsBefore = notificationRepository.count();

        String invalidMessage = """
                {
                  "eventId": "not-a-uuid",
                  "paymentStatus": "SUCCEEDED"
                }
                """;

        kafkaTemplate.send(
                TOPIC,
                UUID.randomUUID().toString(),
                invalidMessage
        ).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = waitForDltRecord(invalidMessage);

        assertThat(dltRecord.topic()).isEqualTo(DLT_TOPIC);

        assertThat(dltRecord.value()).isEqualTo(invalidMessage);

        assertThat(notificationRepository.count()).isEqualTo(notificationsBefore);
    }

    private void sendMessage(
            UUID paymentId,
            String message
    ) throws Exception {
        kafkaTemplate.send(
                TOPIC,
                paymentId.toString(),
                message
        ).get(10, TimeUnit.SECONDS);
    }

    private String createPaymentEvent(
            UUID eventId,
            UUID paymentId,
            String paymentStatus
    ) {
        return """
                {
                  "eventId": "%s",
                  "paymentId": "%s",
                  "paymentStatus": "%s",
                  "payload": {
                    "userId": 42,
                    "amount": 19900,
                    "currency": "RUB"
                  },
                  "occurredAt": "%s"
                }
                """.formatted(
                eventId,
                paymentId,
                paymentStatus,
                Instant.now()
        );
    }

    private ConsumerRecord<String, String> waitForDltRecord(
            String expectedMessage
    ) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "notification-dlt-test-" + UUID.randomUUID()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DLT_TOPIC));

            var deadline = Instant.now().plusSeconds(20);

            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    if (expectedMessage.equals(record.value())) {
                        return record;
                    }
                }
            }
        }

        throw new AssertionError("Message was not received from DLT topic: " + DLT_TOPIC);
    }
}