package com.apidesign.event;

import java.math.BigDecimal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over {@link ApplicationEventPublisher} so callers depend on a typed
 * domain API rather than the framework abstraction. Each publish method captures the
 * minimum data downstream listeners need; full entity look-ups stay in the listener
 * to avoid stale state.
 */
@Component
public class OrderEventPublisher {

    private final ApplicationEventPublisher publisher;

    public OrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishCreated(Long orderId, Long userId, BigDecimal totalAmount) {
        publisher.publishEvent(new OrderCreatedEvent(orderId, userId, totalAmount));
    }

    public void publishShipped(Long orderId, Long userId) {
        publisher.publishEvent(new OrderShippedEvent(orderId, userId));
    }

    public void publishCancelled(Long orderId, Long userId, String reason) {
        publisher.publishEvent(new OrderCancelledEvent(orderId, userId, reason));
    }
}
