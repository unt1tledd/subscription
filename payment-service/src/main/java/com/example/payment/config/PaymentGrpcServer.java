package com.example.payment.config;

import com.example.payment.controller.PaymentController;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentGrpcServer {

    private final PaymentController controller;
    private final int port;

    private Server server;

    public PaymentGrpcServer(
            PaymentController controller,
            @Value("${grpc.server.port:9091}") int port
    ) {
        this.controller = controller;
        this.port = port;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder
                .forPort(port)
                .addService(controller)
                .build()
                .start();
    }

    @PreDestroy
    public void shutdown() {
        if (server == null) {
            return;
        }

        server.shutdown();

        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS
            )) {
                server.shutdownNow();
            }
        } catch (InterruptedException exception) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}