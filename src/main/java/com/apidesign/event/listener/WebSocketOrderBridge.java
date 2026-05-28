package com.apidesign.event.listener;

import com.apidesign.event.OrderCancelledEvent;
import com.apidesign.event.OrderCreatedEvent;
import com.apidesign.event.OrderShippedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards order events into the STOMP broker so subscribed clients see live updates.
 *
 * <p>Plain {@link EventListener} (no {@code @Async}, no {@code @TransactionalEventListener})
 * — the broker dispatches send asynchronously already, and pushing to subscribers slightly
 * before the transaction commits is acceptable here: clients will refetch on their next
 * REST call anyway. If "telegraphs before commit" ever becomes a problem, switch to
 * {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p>Topic shape: {@code /topic/orders/{userId}}. Clients subscribe to their own user id.
 */
@Slf4j
@Component
public class WebSocketOrderBridge {

    private static final String TOPIC_PREFIX = "/topic/orders/";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketOrderBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        push(event.userId(), new OrderUpdate("CREATED", event.orderId()));
    }

    @EventListener
    public void onOrderShipped(OrderShippedEvent event) {
        push(event.userId(), new OrderUpdate("SHIPPED", event.orderId()));
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        push(event.userId(), new OrderUpdate("CANCELLED", event.orderId()));
    }

    private void push(Long userId, OrderUpdate payload) {
        String destination = TOPIC_PREFIX + userId;
        log.debug("WS push -> {} : {}", destination, payload);
        messagingTemplate.convertAndSend(destination, payload);
    }

    /** Message payload sent over the WS topic. */
    public record OrderUpdate(String type, Long orderId) {}
}
