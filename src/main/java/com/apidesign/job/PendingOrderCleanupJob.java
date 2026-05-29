package com.apidesign.job;

import com.apidesign.constants.OrderStatus;
import com.apidesign.entity.Order;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly sweep that auto-cancels PENDING orders older than 24h.
 *
 * <p>{@link SchedulerLock} keeps this safe across multiple application instances —
 * exactly one node runs the job per tick. {@code lockAtLeastFor} prevents a fast-finishing
 * instance from immediately re-running and the {@code lockAtMostFor} cap releases the
 * lock if this instance crashes mid-sweep.
 *
 * <p>Cron {@code 0 0 * * * *} = top of every hour. Each cancellation publishes an
 * {@link com.apidesign.event.OrderCancelledEvent} so notification listeners fire the
 * same way they would for a user-driven cancel.
 */
@Slf4j
@Component
public class PendingOrderCleanupJob {

    private static final String LOCK_NAME = "pendingOrderCleanup";
    private static final String CANCEL_REASON = "Auto-cancelled: pending > 24h";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public PendingOrderCleanupJob(
        OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    // Tx timeout intentionally < ShedLock lockAtMostFor:
    //   lockAtMostFor = PT5M (300s)  ──── if this instance crashes mid-sweep
    //   timeout       = 240s         ──── DB aborts the long-running tx first
    // so the work fails cleanly here BEFORE the distributed lock could expire
    // and let a second node re-fire the same job. Keep this invariant intact
    // whenever you tune either value.
    @Transactional(timeout = 240)
    public void cancelStalePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<Order> stale =
            orderRepository.findByOrderStatusAndCreatedAtBefore(OrderStatus.PENDING, threshold);

        if (stale.isEmpty()) {
            log.debug("Pending-order sweep: nothing older than {}", threshold);
            return;
        }

        for (Order order : stale) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            eventPublisher.publishCancelled(order.getId(), order.getUser().getId(), CANCEL_REASON);
        }
        log.info("Pending-order sweep cancelled {} order(s) older than {}", stale.size(), threshold);
    }
}
