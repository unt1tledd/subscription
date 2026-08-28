package com.example.payment.processor;

import org.springframework.stereotype.Component;

@Component
public class FakePaymentProcessor implements PaymentProcessor {

    @Override
    public ProcessingResult process(
            String paymentMethodId,
            long amountMinor,
            String currency,
            String idempotencyKey
    ) {
        if (paymentMethodId.equals("test-failed")) {
            return ProcessingResult.failed(
                    "PAYMENT_DECLINED"
            );
        }

        return ProcessingResult.succeeded();
    }

    @Override
    public ProcessingResult getStatus(
            String idempotencyKey
    ) {
         // имитируем, что зависший платёж
         // при следующей проверке успешно завершился
        return ProcessingResult.succeeded();
    }
}