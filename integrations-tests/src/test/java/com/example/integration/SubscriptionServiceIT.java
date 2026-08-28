package com.example.integration;

import com.example.subscription.SubscriptionApplication;
import com.example.subscription.entity.Subscription;
import com.example.subscription.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = SubscriptionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.grpc.host=localhost",
                "payment.grpc.port=1"
        }
)
class SubscriptionServiceIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("subscription_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

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
    }

    @Test
    void createsPlanAndSubscriptionThroughHttpApi() {
        String planCode = "pro-" + UUID.randomUUID();

        ResponseEntity<JsonNode> planResponse = createPlan(planCode);

        assertThat(planResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode plan = planResponse.getBody();

        assertThat(plan).isNotNull();
        assertThat(plan.path("code").asText()).isEqualTo(planCode);

        String idempotencyKey = "subscription-" + UUID.randomUUID();

        ResponseEntity<JsonNode> subscriptionResponse =
                createSubscription(
                        42,
                        planCode,
                        idempotencyKey
                );

        assertThat(subscriptionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode subscription = subscriptionResponse.getBody();

        assertThat(subscription).isNotNull();
        assertThat(subscription.path("planCode").asText()).isEqualTo(planCode);
        assertThat(subscription.path("status").asText()).isEqualTo("NEW");
        assertThat(subscription.path("userId").asLong()).isEqualTo(42);
        assertThat(subscription.path("autoRenew").asBoolean()).isTrue();

        UUID subscriptionId = UUID.fromString(subscription.path("id").asText());

        Subscription savedSubscription = subscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow();

        assertThat(savedSubscription.getIdempotencyKey()).isEqualTo(idempotencyKey);

        ResponseEntity<JsonNode> userSubscriptions =
                restTemplate.getForEntity(
                        "/api/v1/subscriptions?userId=42",
                        JsonNode.class
                );

        assertThat(userSubscriptions.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode page = userSubscriptions.getBody();

        assertThat(page).isNotNull();
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);

        assertThat(page.path("content").isArray()).isTrue();

        assertThat(page.path("content").get(0).path("id").asText()).isEqualTo(subscriptionId.toString());

        assertThat(page.path("content").get(0).path("planCode").asText()).isEqualTo(planCode);
    }

    @Test
    void repeatedRequestWithSameIdempotencyKeyReturnsSameSubscription() {
        String planCode = "idempotent-" + UUID.randomUUID();

        ResponseEntity<JsonNode> planResponse = createPlan(planCode);

        assertThat(planResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String idempotencyKey = "subscription-" + UUID.randomUUID();

        ResponseEntity<JsonNode> firstResponse =
                createSubscription(
                        43,
                        planCode,
                        idempotencyKey
                );

        ResponseEntity<JsonNode> secondResponse =
                createSubscription(
                        43,
                        planCode,
                        idempotencyKey
                );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(secondResponse.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode firstSubscription = firstResponse.getBody();

        JsonNode secondSubscription = secondResponse.getBody();

        assertThat(firstSubscription).isNotNull();
        assertThat(secondSubscription).isNotNull();

        assertThat(secondSubscription.path("id").asText()
            ).isEqualTo(firstSubscription.path("id").asText());

        assertThat(secondSubscription.path("status").asText()
            ).isEqualTo(firstSubscription.path("status").asText());

        assertThat(subscriptionRepository.countByIdempotencyKey(idempotencyKey)
            ).isEqualTo(1);
    }

    @Test
    void doesNotCreateSubscriptionForUnknownPlan() {
        String idempotencyKey = "subscription-" + UUID.randomUUID();

        String unknownPlanCode = "unknown-" + UUID.randomUUID();

        ResponseEntity<JsonNode> response =
                createSubscription(
                        44,
                        unknownPlanCode,
                        idempotencyKey
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(subscriptionRepository.countByIdempotencyKey(idempotencyKey)).isZero();
    }

    private ResponseEntity<JsonNode> createPlan(String planCode) {
        return restTemplate.postForEntity(
                "/api/v1/plans",
                Map.of(
                        "code", planCode,
                        "name", "Pro monthly",
                        "price", 19_900,
                        "currency", "RUB",
                        "durationDays", 30
                ),
                JsonNode.class
        );
    }

    private ResponseEntity<JsonNode> createSubscription(
            long userId,
            String planCode,
            String idempotencyKey
    ) {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);

        headers.set(
                "Idempotency-Key",
                idempotencyKey
        );

        return restTemplate.exchange(
                "/api/v1/subscriptions",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "userId", userId,
                                "planCode", planCode,
                                "autoRenew", true
                        ),
                        headers
                ),
                JsonNode.class
        );
    }
}