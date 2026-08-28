package com.example.payment.controller;

import com.example.payment.entity.Payment;
import com.example.payment.errors.IdempotencyConflictException;
import com.example.payment.errors.PaymentNotFoundException;
import com.example.payment.usecase.PaymentService;
import com.example.payment.v1.*;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;


@Component
public class PaymentController extends PaymentServiceGrpc.PaymentServiceImplBase {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void createPayment(CreatePaymentRequest req, StreamObserver<CreatePaymentResponse> responseObserver) {
        try {
            Payment payment = paymentService.create(
                    req.getUserId(),
                    req.getAmount(),
                    req.getCurrency(),
                    req.getPaymentMethodId(),
                    req.getIdempotencyKey()
            );

            CreatePaymentResponse response = CreatePaymentResponse
                    .newBuilder().setPayment(toProto(payment)).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("invalid payment id")
                            .asRuntimeException()
            );
        } catch (PaymentNotFoundException e) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (IdempotencyConflictException exception) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(exception.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Could not create payment")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void getPayment(GetPaymentRequest req, StreamObserver<GetPaymentResponse> responseObserver) {
        try {
            Payment payment = paymentService.get(UUID.fromString(req.getPaymentId()));

            GetPaymentResponse response = GetPaymentResponse.newBuilder().setPayment(toProto(payment)).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("invalid payment id")
                            .asRuntimeException()
            );
        } catch (PaymentNotFoundException e) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Could not get payment with id=" + req.getPaymentId())
                            .asRuntimeException()
            );
        }
    }

    private com.example.payment.v1.Payment toProto(
            Payment payment
    ) {
        return com.example.payment.v1.Payment.newBuilder()
                .setId(payment.getId().toString())
                .setUserId(payment.getUserId())
                .setAmount(payment.getAmount())
                .setCurrency(payment.getCurrency())
                .setStatus(toProtoStatus(payment.getStatus()))
                .setFailureCode(
                        payment.getFailureCode() == null
                                ? ""
                                : payment.getFailureCode()
                )
                .setCreatedAt(toTimestamp(payment.getCreatedAt()))
                .setUpdatedAt(toTimestamp(payment.getUpdatedAt()))
                .build();
    }

    private PaymentStatus toProtoStatus(
            com.example.payment.entity.PaymentStatus status
    ) {
        return switch (status) {
            case PENDING -> PaymentStatus.PAYMENT_STATUS_PENDING;
            case SUCCEEDED -> PaymentStatus.PAYMENT_STATUS_SUCCEEDED;
            case FAILED -> PaymentStatus.PAYMENT_STATUS_FAILED;
            default -> PaymentStatus.PAYMENT_STATUS_UNSPECIFIED;
        };
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
