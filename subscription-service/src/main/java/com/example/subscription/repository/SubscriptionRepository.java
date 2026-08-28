package com.example.subscription.repository;

import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = "plan")
    Optional<Subscription> findById(@NonNull UUID id);

    @EntityGraph(attributePaths = "plan")
    Page<Subscription> findAllByUserId(
            long userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "plan")
    Page<Subscription> findAllByUserIdAndStatus(
            long userId,
            SubscriptionStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "plan")
    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByIdempotencyKey(String idempotencyKey);
}
