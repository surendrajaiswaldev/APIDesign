package com.apidesign.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pretend payment gateway. Demonstrates the four Resilience4j primitives the way they
 * stack in production code — annotation order is roughly:
 *
 * <ol>
 *   <li>{@link Bulkhead} — caps concurrency (so a downstream stall cannot exhaust threads)</li>
 *   <li>{@link CircuitBreaker} — opens when failure rate crosses threshold (fail fast)</li>
 *   <li>{@link Retry} — transparently retries transient errors (after the breaker decides)</li>
 * </ol>
 *
 * <p>The intentional 30% random failure + 100-2000ms latency makes it easy to verify the
 * fallback kicks in once the circuit opens — call {@link #chargeOrder} in a loop and
 * watch responses flip from real payment refs to {@code DEGRADED:order-*}.
 */
@Slf4j
@Service
public class PaymentService {

    @CircuitBreaker(name = "payment", fallbackMethod = "chargeFallback")
    @Retry(name = "payment")
    @Bulkhead(name = "payment")
    public String chargeOrder(Long orderId, BigDecimal amount) {
        long sleepMillis = ThreadLocalRandom.current().nextLong(100, 2001);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while charging order " + orderId, e);
        }
        if (ThreadLocalRandom.current().nextInt(100) < 30) {
            log.warn("Simulated payment failure for order {} (amount={})", orderId, amount);
            throw new RuntimeException("Simulated payment failure");
        }
        String ref = "PAY-" + orderId + "-" + System.currentTimeMillis();
        log.info("Charged order {} amount={} ref={}", orderId, amount, ref);
        return ref;
    }

    /**
     * Resilience4j hands the original throwable to fallbacks; the method must mirror
     * the protected one's signature with a final {@code Throwable}.
     */
    @SuppressWarnings("unused")
    private String chargeFallback(Long orderId, BigDecimal amount, Throwable ex) {
        log.warn(
            "Payment for order {} fell back (amount={}, cause={})",
            orderId,
            amount,
            ex.toString());
        return "DEGRADED:order-" + orderId;
    }

    /**
     * Saga compensation hook: reverse a previously-successful charge. Stubbed — logs only.
     * Production would re-call the gateway's refund endpoint with the original reference.
     */
    public void refundCharge(Long orderId, String chargeRef) {
        log.info("Refunding charge ref={} for order {}", chargeRef, orderId);
    }
}
