package com.example.subscription.controller;

import com.example.subscription.dto.subscription.CheckoutSubscriptionRequest;
import com.example.subscription.dto.subscription.CheckoutSubscriptionResponse;
import com.example.subscription.dto.subscription.CreateSubscriptionRequest;
import com.example.subscription.dto.subscription.SubscriptionResponse;
import com.example.subscription.dto.subscription.CheckoutResult;
import com.example.subscription.entity.Plan;
import com.example.subscription.entity.Subscription;
import com.example.subscription.entity.SubscriptionStatus;
import com.example.subscription.usecase.PlanService;
import com.example.subscription.usecase.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final PlanService planService;

    public SubscriptionController(SubscriptionService subscriptionService, PlanService planService) {
        this.subscriptionService = subscriptionService;
        this.planService = planService;
    }

    @GetMapping
    public Page<SubscriptionResponse> getUserSubs(
            @Positive
            @RequestParam long userId,

            @RequestParam(required = false)
            SubscriptionStatus status,

            @PositiveOrZero
            @RequestParam(defaultValue = "0")
            int page,

            @Positive
            @Max(100)
            @RequestParam(defaultValue = "10")
            int size
    ) {
        return subscriptionService.getUserSubs(userId, status, page, size).map(SubscriptionResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(
            @Valid
            @RequestBody
            CreateSubscriptionRequest request,

            @NotBlank
            @Size(max = 128)
            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    ) {
        Plan plan = planService.get(request.planCode());

        return SubscriptionResponse.from(subscriptionService.create(
                new Subscription(
                        request.userId(),
                        plan,
                        request.autoRenew(),
                        idempotencyKey)
        ));
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getSub(@PathVariable UUID id) {
        return SubscriptionResponse.from(subscriptionService.getSub(id));
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancelAutoRenew(@PathVariable UUID id) {
        return SubscriptionResponse.from(subscriptionService.cancelAutoRenew(id));
    }

    @PostMapping("{id}/auto")
    public SubscriptionResponse addAutoRenew(@PathVariable UUID id) {
        return SubscriptionResponse.from(subscriptionService.addAutoRenew(id));
    }

    @PostMapping("/{id}/checkout")
    public CheckoutSubscriptionResponse checkout(
            @PathVariable UUID id,

            @Valid
            @RequestBody
            CheckoutSubscriptionRequest request,

            @NotBlank
            @Size(max = 128)
            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    ) {
        CheckoutResult result = subscriptionService.checkout(
                id,
                request.paymentMethodId(),
                idempotencyKey
        );

        return CheckoutSubscriptionResponse.from(
                result.subscription(),
                result.payment()
        );
    }
}
