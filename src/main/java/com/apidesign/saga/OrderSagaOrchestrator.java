package com.apidesign.saga;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.OrderSaga;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.OrderSagaRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.service.PaymentService;
import com.apidesign.service.ShippingService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrated saga for {@code createOrder}. Replaces the previous inline implementation
 * in {@code OrderService} with four named, independently-committed steps:
 *
 * <ol>
 *   <li>{@link SagaStep#RESERVE_STOCK} — atomic stock decrement per item.</li>
 *   <li>{@link SagaStep#CHARGE_PAYMENT} — Resilience4j-guarded gateway call.</li>
 *   <li>{@link SagaStep#SCHEDULE_SHIPPING} — stub carrier integration.</li>
 *   <li>{@link SagaStep#COMPLETE_ORDER} — flip the order to CONFIRMED + publish event.</li>
 * </ol>
 *
 * <p><b>Why orchestrated?</b> The control-plane (this class) calls workers and persists
 * state. The alternative (choreographed sagas) wires steps via events — harder to debug
 * because there's no single point of truth. We pick orchestration for teachability.
 *
 * <p><b>Transactional shape.</b> Each step + its state update runs in
 * {@code REQUIRES_NEW}, so a downstream failure cannot roll back earlier commits. Without
 * that, the entire saga would behave like one big transaction and the whole point of
 * sagas (compensations vs. ACID) would be lost. The public {@link #start} method itself
 * runs with {@code NOT_SUPPORTED} to keep step-local transactions independent of any
 * caller-provided transaction.
 *
 * <p><b>Crash visibility</b> (not full recovery): every transition writes a row to
 * {@code OrderSaga}, and the per-step compensation log records what we did so post-mortem
 * traces are tractable. Re-driving in-flight sagas after a crash is a stretch goal.
 */
@Slf4j
@Service
public class OrderSagaOrchestrator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderSagaRepository sagaRepository;
    private final PaymentService paymentService;
    private final ShippingService shippingService;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher eventPublisher;
    private final OrderSagaOrchestrator self;

    @Autowired
    public OrderSagaOrchestrator(
        UserRepository userRepository,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        OrderSagaRepository sagaRepository,
        PaymentService paymentService,
        ShippingService shippingService,
        OrderMapper orderMapper,
        OrderEventPublisher eventPublisher,
        @Lazy OrderSagaOrchestrator self) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.sagaRepository = sagaRepository;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        // Self-reference required so step methods go through the Spring proxy and honour
        // their REQUIRES_NEW propagation. Direct `this.` calls would skip the AOP layer.
        this.self = self;
    }

    /**
     * Public entry point. Creates the PENDING order + saga row, then walks the steps. On
     * any failure, compensates in reverse and throws the original cause to the caller.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderDTO start(CreateOrderRequest request) {
        validateRequest(request);

        String sagaId = UUID.randomUUID().toString();
        Order order = self.createPendingOrder(request, sagaId);

        SagaContext ctx = new SagaContext(sagaId, order.getId());
        try {
            self.reserveStockStep(ctx, request);
            self.chargePaymentStep(ctx, order.getTotalAmount());
            self.scheduleShippingStep(ctx, order.getShippingAddress());
            self.completeOrderStep(ctx);
            log.info("Saga {} for order {} COMPLETED", sagaId, order.getId());
        } catch (RuntimeException ex) {
            log.warn(
                "Saga {} for order {} failed at step {} — compensating ({})",
                sagaId,
                order.getId(),
                ctx.lastAttemptedStep,
                ex.getMessage());
            self.compensate(ctx, ex);
            throw ex;
        }

        Order completed =
            orderRepository.findById(order.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "Order disappeared mid-saga: " + order.getId()));
        return orderMapper.toDTO(completed);
    }

    // --- Step 0: bootstrap -------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createPendingOrder(CreateOrderRequest request, String sagaId) {
        User user =
            userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User not found with ID: " + request.userId(), ErrorCodes.USER_NOT_FOUND));

        Order order =
            Order.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .orderStatus(OrderStatus.PENDING)
                .shippingAddress(
                    request.shippingAddress() != null
                        ? request.shippingAddress()
                        : user.getAddress())
                .notes(request.notes())
                .totalAmount(BigDecimal.ZERO)
                .build();

        // Build line items + running total. Stock is NOT decremented here; that lives in
        // the RESERVE_STOCK step so compensation can restore exactly what we reserved.
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.orderItems()) {
            Product product =
                productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with ID: " + itemRequest.productId(),
                        ErrorCodes.ORDER_ITEM_PRODUCT_NOT_FOUND));
            OrderItem orderItem =
                OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .unitPrice(product.getPrice())
                    .quantity(itemRequest.quantity())
                    .notes(itemRequest.notes())
                    .build();
            order.addOrderItem(orderItem);
            total =
                total.add(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }
        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        OrderSaga saga =
            OrderSaga.builder()
                .sagaId(sagaId)
                .orderId(saved.getId())
                .currentState(SagaState.STARTED.name())
                .currentStep(null)
                .compensationLog("[]")
                .build();
        sagaRepository.save(saga);
        log.info("Saga {} started for order {}", sagaId, saved.getId());
        return saved;
    }

    // --- Step 1: reserve stock --------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveStockStep(SagaContext ctx, CreateOrderRequest request) {
        ctx.lastAttemptedStep = SagaStep.RESERVE_STOCK;
        markStep(ctx, SagaStep.RESERVE_STOCK);
        for (CreateOrderRequest.OrderItemRequest item : request.orderItems()) {
            int updated = productRepository.decrementStock(item.productId(), item.quantity());
            if (updated == 0) {
                throw new BusinessLogicException(
                    "Insufficient stock for product: " + item.productId(),
                    ErrorCodes.ORDER_INSUFFICIENT_STOCK);
            }
            ctx.reservedStock.put(item.productId(), item.quantity());
        }
        transition(ctx, SagaState.STOCK_RESERVED, null);
        appendLog(ctx, SagaStep.RESERVE_STOCK, "OK");
    }

    // --- Step 2: charge payment -------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void chargePaymentStep(SagaContext ctx, BigDecimal amount) {
        ctx.lastAttemptedStep = SagaStep.CHARGE_PAYMENT;
        markStep(ctx, SagaStep.CHARGE_PAYMENT);
        String ref = paymentService.chargeOrder(ctx.orderId, amount);
        ctx.paymentReference = ref;
        // Resilience4j's fallback returns "DEGRADED:order-..." rather than throwing — treat
        // that as a logical failure so the saga compensates instead of silently confirming
        // an order that wasn't actually paid for.
        if (ref == null || ref.startsWith("DEGRADED:")) {
            throw new BusinessLogicException(
                "Payment gateway degraded (ref=" + ref + ")", ErrorCodes.INTERNAL_ERROR);
        }
        transition(ctx, SagaState.PAYMENT_CHARGED, null);
        appendLog(ctx, SagaStep.CHARGE_PAYMENT, "OK ref=" + ref);
    }

    // --- Step 3: schedule shipping ----------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleShippingStep(SagaContext ctx, String address) {
        ctx.lastAttemptedStep = SagaStep.SCHEDULE_SHIPPING;
        markStep(ctx, SagaStep.SCHEDULE_SHIPPING);
        String tracking = shippingService.scheduleShipment(ctx.orderId, address);
        ctx.trackingNumber = tracking;
        transition(ctx, SagaState.SHIPPING_SCHEDULED, null);
        appendLog(ctx, SagaStep.SCHEDULE_SHIPPING, "OK tracking=" + tracking);
    }

    // --- Step 4: complete order -------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeOrderStep(SagaContext ctx) {
        ctx.lastAttemptedStep = SagaStep.COMPLETE_ORDER;
        markStep(ctx, SagaStep.COMPLETE_ORDER);
        Order order =
            orderRepository.findById(ctx.orderId)
                .orElseThrow(() -> new IllegalStateException(
                    "Order disappeared at COMPLETE_ORDER: " + ctx.orderId));
        order.setOrderStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        // Event publication previously lived inline in OrderService.createOrder; the
        // notification fans out (AFTER_COMMIT) so it only fires when the saga has actually
        // committed COMPLETE_ORDER successfully.
        eventPublisher.publishCreated(order.getId(), order.getUser().getId(), order.getTotalAmount());
        transition(ctx, SagaState.COMPLETED, null);
        appendLog(ctx, SagaStep.COMPLETE_ORDER, "OK");
    }

    // --- Compensation -----------------------------------------------------

    /**
     * Walks completed steps in reverse and undoes them. Each compensation runs in its own
     * {@code REQUIRES_NEW} so a single failed undo doesn't cascade-roll-back the rest.
     */
    public void compensate(SagaContext ctx, RuntimeException originalFailure) {
        try {
            if (ctx.trackingNumber != null) {
                self.compensateShipping(ctx);
            }
            if (ctx.paymentReference != null) {
                self.compensatePayment(ctx);
            }
            if (!ctx.reservedStock.isEmpty()) {
                self.compensateStock(ctx);
            }
            self.markCompensated(ctx, originalFailure);
        } catch (RuntimeException compensationFailure) {
            log.error(
                "Saga {} compensation itself failed — marking FAILED for ops review",
                ctx.sagaId,
                compensationFailure);
            self.markFailed(ctx, compensationFailure);
        }

        // Best-effort: also flip the order to CANCELLED so customers see it (independent
        // of the saga row's bookkeeping). Errors here are swallowed because the saga row
        // already records the failure.
        try {
            self.cancelOrder(ctx.orderId);
        } catch (RuntimeException ex) {
            log.warn("Order {} cancellation flip failed during compensation: {}", ctx.orderId, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateShipping(SagaContext ctx) {
        transition(ctx, SagaState.COMPENSATING_SHIPPING, null);
        shippingService.cancelShipment(ctx.orderId, ctx.trackingNumber);
        appendLog(ctx, SagaStep.SCHEDULE_SHIPPING, "COMPENSATED tracking=" + ctx.trackingNumber);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensatePayment(SagaContext ctx) {
        transition(ctx, SagaState.COMPENSATING_PAYMENT, null);
        paymentService.refundCharge(ctx.orderId, ctx.paymentReference);
        appendLog(ctx, SagaStep.CHARGE_PAYMENT, "COMPENSATED ref=" + ctx.paymentReference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateStock(SagaContext ctx) {
        transition(ctx, SagaState.COMPENSATING_STOCK, null);
        for (Map.Entry<Long, Long> e : ctx.reservedStock.entrySet()) {
            productRepository.restoreStock(e.getKey(), e.getValue());
            appendLog(
                ctx,
                SagaStep.RESERVE_STOCK,
                "COMPENSATED productId=" + e.getKey() + " qty=" + e.getValue());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensated(SagaContext ctx, RuntimeException cause) {
        transition(ctx, SagaState.COMPENSATED, cause.getMessage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(SagaContext ctx, RuntimeException cause) {
        transition(ctx, SagaState.FAILED, cause.getMessage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        if (order.getOrderStatus() == OrderStatus.PENDING
            || order.getOrderStatus() == OrderStatus.CONFIRMED) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
    }

    // --- Persistence helpers ----------------------------------------------

    private void markStep(SagaContext ctx, SagaStep step) {
        OrderSaga saga = loadSaga(ctx.sagaId);
        saga.setCurrentStep(step.name());
        sagaRepository.save(saga);
    }

    private void transition(SagaContext ctx, SagaState newState, String errorMessage) {
        OrderSaga saga = loadSaga(ctx.sagaId);
        saga.setCurrentState(newState.name());
        if (newState == SagaState.COMPLETED
            || newState == SagaState.COMPENSATED
            || newState == SagaState.FAILED) {
            saga.setCurrentStep(null);
        }
        if (errorMessage != null) {
            saga.setErrorMessage(truncate(errorMessage, 2000));
        }
        sagaRepository.save(saga);
    }

    private void appendLog(SagaContext ctx, SagaStep step, String status) {
        OrderSaga saga = loadSaga(ctx.sagaId);
        // Append-only JSON-ish line per entry; not a full JSON array parser because the
        // log is for human/operator reading, not machine consumption.
        String existing = saga.getCompensationLog() == null ? "[]" : saga.getCompensationLog();
        // Strip trailing ']' so we can append.
        String trimmed = existing.endsWith("]") ? existing.substring(0, existing.length() - 1) : existing;
        String separator = trimmed.endsWith("[") ? "" : ",";
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", step.name());
        entry.put("status", status);
        entry.put("timestamp", Instant.now().toString());
        saga.setCompensationLog(trimmed + separator + toJson(entry) + "]");
        sagaRepository.save(saga);
    }

    private OrderSaga loadSaga(String sagaId) {
        return sagaRepository
            .findBySagaId(sagaId)
            .orElseThrow(() -> new IllegalStateException("Saga row vanished: " + sagaId));
    }

    private static String toJson(Map<String, Object> entry) {
        // Avoid pulling in Jackson here for one map → handwrite. Keys are alphanumeric, no escaping needed.
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : entry.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(e.getKey()).append("\":\"").append(String.valueOf(e.getValue()).replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // --- Request validation -----------------------------------------------

    private static void validateRequest(CreateOrderRequest request) {
        if (request.orderItems() == null || request.orderItems().isEmpty()) {
            throw new BusinessLogicException(
                "Order must contain at least one item", ErrorCodes.ORDER_EMPTY_ITEMS);
        }
        Set<Long> seen = new HashSet<>();
        for (CreateOrderRequest.OrderItemRequest item : request.orderItems()) {
            if (!seen.add(item.productId())) {
                throw new BusinessLogicException(
                    "Order contains duplicate product: " + item.productId(),
                    ErrorCodes.ORDER_DUPLICATE_ITEMS);
            }
        }
    }

    private static String generateOrderNumber() {
        long ts = Instant.now().toEpochMilli();
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return "ORD-" + ts + "-" + HexFormat.of().formatHex(bytes).toUpperCase();
    }

    /**
     * Mutable scratch-pad carried through the saga. Holds the saga ID, the order under
     * construction, and per-step bookkeeping (reservation map / payment ref / tracking
     * number) needed for compensations to undo exactly what was done.
     */
    public static final class SagaContext {
        public final String sagaId;
        public final Long orderId;
        public final Map<Long, Long> reservedStock = new HashMap<>();
        public String paymentReference;
        public String trackingNumber;
        public SagaStep lastAttemptedStep;

        public SagaContext(String sagaId, Long orderId) {
            this.sagaId = sagaId;
            this.orderId = orderId;
        }

        /** Visible for tests — quick way to assert which step actually fired last. */
        public List<Long> getReservedProductIds() {
            return new ArrayList<>(reservedStock.keySet());
        }
    }
}
