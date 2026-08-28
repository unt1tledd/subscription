package com.example.subscription.controller;

import com.example.subscription.dto.plan.CreatePlanRequest;
import com.example.subscription.dto.plan.PlanResponse;
import com.example.subscription.entity.Plan;
import com.example.subscription.usecase.PlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {
    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public Page<PlanResponse> getAllPlans(
            @RequestParam(required = false) Boolean active,
            @PositiveOrZero @RequestParam(defaultValue = "0") int page,
            @Positive @RequestParam(defaultValue = "10") @Max(100) int size
    ) {
        return planService.getPlans(active, page, size).map(PlanResponse::from);

    }

    @GetMapping("/{code}")
    public PlanResponse get(@PathVariable String code) {
        return PlanResponse.from(planService.get(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(
            @Valid @RequestBody CreatePlanRequest request
    ) {
        return PlanResponse.from(planService.create(new Plan(
                request.code(),
                request.name(),
                request.price(),
                request.currency(),
                request.durationDays()
        )));
    }


    @PostMapping("/{code}/deactive")
    public PlanResponse deactivate(@PathVariable String code) {
        return PlanResponse.from(planService.deactivate(code));
    }

    @PostMapping("/{code}/activate")
    public PlanResponse activate(@PathVariable String code) {
        return PlanResponse.from(planService.activate(code));
    }
}
