package com.apidesign.event.listener;

import com.apidesign.event.OrderCancelledEvent;
import com.apidesign.event.OrderCreatedEvent;
import com.apidesign.event.OrderShippedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pretends to dispatch email / SMS notifications. Logs only — replace with a real
 * sender (Spring Mail, Twilio, etc.) without touching the publisher or the service
 * layer.
 *
 * <p>{@link TransactionalEventListener} with phase = AFTER_COMMIT guarantees the row is
 * durably committed before we attempt to "notify" — no risk of telling a customer the
 * order shipped if the transaction then rolls back. The {@code @Async} hop frees the
 * request thread; failures here cannot affect the originating HTTP response.
 *
 * <p><b>Timeout strategy (T3):</b> we deliberately keep listener methods {@code void}
 * because {@code @TransactionalEventListener(AFTER_COMMIT)} fires after the publishing
 * transaction has already committed — the {@link java.util.concurrent.CompletableFuture}
 * {@code .orTimeout(...)} pattern would only kill the task scheduled on the executor,
 * not propagate a meaningful failure back to the caller (there is none). Instead, we
 * <i>observe</i> latency by stamping start/end millis on MDC; metrics + log scrapers can
 * surface slow listeners without changing the public method shape. Back-pressure is
 * handled in {@code AsyncConfig.eventTaskExecutor}: bounded queue + CallerRunsPolicy
 * means a saturated pool spills onto the publishing thread, never silently drops.
 */
@Slf4j
@Component
public class NotificationListener {

    private static final String MDC_START_MS = "listener.startMs";
    private static final String MDC_DURATION_MS = "listener.durationMs";

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        long start = System.currentTimeMillis();
        MDC.put(MDC_START_MS, Long.toString(start));
        try {
            log.info(
                "Notification: order {} created (user={}, total={})",
                event.orderId(),
                event.userId(),
                event.totalAmount());
        } finally {
            MDC.put(MDC_DURATION_MS, Long.toString(System.currentTimeMillis() - start));
            MDC.remove(MDC_START_MS);
            MDC.remove(MDC_DURATION_MS);
        }
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(OrderShippedEvent event) {
        long start = System.currentTimeMillis();
        MDC.put(MDC_START_MS, Long.toString(start));
        try {
            log.info(
                "Notification: order {} shipped (user={})", event.orderId(), event.userId());
        } finally {
            MDC.put(MDC_DURATION_MS, Long.toString(System.currentTimeMillis() - start));
            MDC.remove(MDC_START_MS);
            MDC.remove(MDC_DURATION_MS);
        }
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        long start = System.currentTimeMillis();
        MDC.put(MDC_START_MS, Long.toString(start));
        try {
            log.info(
                "Notification: order {} cancelled (user={}, reason={})",
                event.orderId(),
                event.userId(),
                event.reason());
        } finally {
            MDC.put(MDC_DURATION_MS, Long.toString(System.currentTimeMillis() - start));
            MDC.remove(MDC_START_MS);
            MDC.remove(MDC_DURATION_MS);
        }
    }
}
