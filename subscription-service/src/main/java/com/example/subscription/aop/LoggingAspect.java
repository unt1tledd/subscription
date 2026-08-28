package com.example.subscription.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(public * " + "com.example.subscription.controller..*(..))")
    public void controllerMethods() {
    }

    @Around("controllerMethods()")
    public Object logControllerMethod(
            ProceedingJoinPoint joinPoint
    ) throws Throwable {
        String controller = joinPoint
                .getSignature()
                .getDeclaringType()
                .getSimpleName();

        String method = joinPoint
                .getSignature()
                .getName();

        long startedAt = System.nanoTime();

        log.info(
                "Request started: {}.{}",
                controller,
                method
        );

        try {
            Object result = joinPoint.proceed();

            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.info(
                    "Request completed: {}.{} in {} ms",
                    controller,
                    method,
                    durationMs
            );

            return result;
        } catch (Throwable exception) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            log.error(
                    "Request failed: {}.{} after {} ms: {}",
                    controller,
                    method,
                    durationMs,
                    exception.getMessage()
            );

            throw exception;
        }
    }


}