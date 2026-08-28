package com.example.subscription.usecase;

import com.example.subscription.entity.Plan;
import com.example.subscription.errors.plan.PlanAlreadyExistsException;
import com.example.subscription.errors.plan.PlanNotFoundException;
import com.example.subscription.repository.PlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
public class PlanService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public Plan create(Plan plan) {
        if (planRepository.existsByCode(plan.getCode())) {
            throw new PlanAlreadyExistsException(plan.getCode());
        }

        return planRepository.save(plan);
    }

    public Plan get(String code) {
        return planRepository.findByCode(code).orElseThrow(() -> new PlanNotFoundException(code));
    }

    @Transactional(readOnly = true)
    public Page<Plan> getPlans(Boolean active, int page, int size) {
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

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Plan> plans;

        if (active == null) {
            plans = planRepository.findAll(pageable);
        } else {
            plans = planRepository.findAllByActive(active, pageable);
        }

        return plans;

    }

    @Transactional
    public Plan activate(String code) {
        Plan plan = planRepository.findByCode(code).orElseThrow(() -> new PlanNotFoundException(code));

        plan.activate();

        return plan;
    }

    @Transactional
    public Plan deactivate(String code) {
        Plan plan = planRepository.findByCode(code).orElseThrow(() -> new PlanNotFoundException(code));

        plan.deactivate();

        return plan;
    }

}
