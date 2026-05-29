package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderItemRepository;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.response.PagedResponse;
import com.apidesign.saga.OrderSagaOrchestrator;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher eventPublisher;
    private final OrderSagaOrchestrator sagaOrchestrator;

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        OrderMapper orderMapper,
        OrderEventPublisher eventPublisher,
        OrderSagaOrchestrator sagaOrchestrator) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    /**
     * Order creation now delegates to {@link OrderSagaOrchestrator#start} — see that class
     * for the full step sequence + compensation rules.
     *
     * <p>Timeout 30 s: a tight upper bound for the four-step happy path (stock decrement +
     * payment gateway + shipping stub + complete). The saga's per-step transactions are
     * {@code REQUIRES_NEW}, so this outer timeout protects against orchestrator-side
     * loops/hangs rather than gating any single DB transaction.
     */
    @Transactional(timeout = 30)
    public OrderDTO createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.userId());
        return sagaOrchestrator.start(request);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long orderId) {
        return orderMapper.toDTO(loadOrder(orderId));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Order loadOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId, ErrorCodes.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        return orderMapper.toDTO(loadOrderByNumber(orderNumber));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Order loadOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with number: " + orderNumber, ErrorCodes.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByUser(Long userId, Pageable pageable) {
        Page<Order> orders = findOrdersByUser(userId, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Order> findOrdersByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND);
        }
        return orderRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findOrdersByStatus(status, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public Page<Order> findOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findOrdersByStatus(status, pageable);
    }

    public OrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Updating order {} -> {}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId, ErrorCodes.ORDER_NOT_FOUND));

        if (!OrderStatus.isValidTransition(order.getOrderStatus(), newStatus)) {
            throw new BusinessLogicException(
                "Invalid status transition from " + order.getOrderStatus() + " to " + newStatus,
                ErrorCodes.ORDER_INVALID_STATUS_TRANSITION);
        }

        order.setOrderStatus(newStatus);
        if (newStatus == OrderStatus.SHIPPED) {
            order.setEstimatedDelivery(LocalDateTime.now().plusDays(5));
        }
        Order saved = orderRepository.save(order);
        if (newStatus == OrderStatus.SHIPPED) {
            eventPublisher.publishShipped(saved.getId(), saved.getUser().getId());
        } else if (newStatus == OrderStatus.CANCELLED) {
            eventPublisher.publishCancelled(saved.getId(), saved.getUser().getId(), "Admin cancelled");
        }
        return orderMapper.toDTO(saved);
    }

    /**
     * Timeout 20 s: cancel walks N order items (stock restore) plus one order save and an
     * event publish — well under typical request budgets. A run that overshoots indicates
     * lock contention on PRODUCTS rows; aborting cleanly here beats hanging the request.
     */
    @Transactional(timeout = 20)
    public OrderDTO cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId, ErrorCodes.ORDER_NOT_FOUND));

        if (order.getOrderStatus() != OrderStatus.PENDING
            && order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessLogicException(
                "Cannot cancel order with status: " + order.getOrderStatus(),
                ErrorCodes.ORDER_INVALID_STATUS_TRANSITION);
        }

        for (OrderItem item : order.getOrderItems()) {
            productRepository.restoreStock(item.getProduct().getId(), item.getQuantity());
        }
        order.setOrderStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        eventPublisher.publishCancelled(saved.getId(), saved.getUser().getId(), "User cancelled");
        return orderMapper.toDTO(saved);
    }
}
