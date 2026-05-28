package com.apidesign.event.listener;

import com.apidesign.event.OrderCancelledEvent;
import com.apidesign.event.OrderCreatedEvent;
import com.apidesign.event.OrderShippedEvent;
import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Component
public class NotificationListener {

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info(
            "Notification: order {} created (user={}, total={})",
            event.orderId(),
            event.userId(),
            event.totalAmount());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderShipped(OrderShippedEvent event) {
        log.info(
            "Notification: order {} shipped (user={})", event.orderId(), event.userId());
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info(
            "Notification: order {} cancelled (user={}, reason={})",
            event.orderId(),
            event.userId(),
            event.reason());
    }
}
