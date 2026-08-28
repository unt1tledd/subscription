package com.example.subscription.repository;

import com.example.subscription.entity.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    boolean existsByCode(String code);

    Optional<Plan> findByCode(String code);

    Page<Plan> findAllByActive(boolean active, Pageable pageable);
}
