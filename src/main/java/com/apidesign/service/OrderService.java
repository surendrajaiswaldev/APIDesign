package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.event.OrderEventPublisher;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderItemRepository;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.response.PagedResponse;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_CREATE_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final OrderEventPublisher eventPublisher;

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        OrderMapper orderMapper,
        OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    public OrderDTO createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.userId());

        DataIntegrityViolationException last = null;
        for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return createOrderInternal(request);
            } catch (DataIntegrityViolationException ex) {
                last = ex;
                log.warn(
                    "Order create attempt {} failed with integrity violation: {}",
                    attempt,
                    ex.getMostSpecificCause().getMessage());
            }
        }
        throw last;
    }

    private OrderDTO createOrderInternal(CreateOrderRequest request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + request.userId(), ErrorCodes.USER_NOT_FOUND));

        if (request.orderItems() == null || request.orderItems().isEmpty()) {
            throw new BusinessLogicException(
                "Order must contain at least one item", ErrorCodes.ORDER_EMPTY_ITEMS);
        }

        Set<Long> productIds = new HashSet<>();
        for (CreateOrderRequest.OrderItemRequest item : request.orderItems()) {
            if (!productIds.add(item.productId())) {
                throw new BusinessLogicException(
                    "Order contains duplicate product: " + item.productId(),
                    ErrorCodes.ORDER_DUPLICATE_ITEMS);
            }
        }

        Order order = Order.builder()
            .orderNumber(generateOrderNumber())
            .user(user)
            .orderStatus(OrderStatus.PENDING)
            .shippingAddress(
                request.shippingAddress() != null ? request.shippingAddress() : user.getAddress())
            .notes(request.notes())
            .totalAmount(BigDecimal.ZERO)
            .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.orderItems()) {
            Product product = productRepository.findById(itemRequest.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Product not found with ID: " + itemRequest.productId(),
                    ErrorCodes.ORDER_ITEM_PRODUCT_NOT_FOUND));

            // Atomic decrement; throws if the row update affected 0 rows (no stock).
            int updated = productRepository.decrementStock(product.getId(), itemRequest.quantity());
            if (updated == 0) {
                log.warn(
                    "Insufficient stock for product id={} requested={}",
                    product.getId(),
                    itemRequest.quantity());
                throw new BusinessLogicException(
                    "Insufficient stock for product: " + product.getName(),
                    ErrorCodes.ORDER_INSUFFICIENT_STOCK);
            }

            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .productSku(product.getSku())
                .unitPrice(product.getPrice())
                .quantity(itemRequest.quantity())
                .notes(itemRequest.notes())
                .build();
            order.addOrderItem(orderItem);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        log.info("Order created id={} number={} total={}", saved.getId(), saved.getOrderNumber(),
            saved.getTotalAmount());
        // Published before commit; @TransactionalEventListener(AFTER_COMMIT) defers delivery
        // until the transaction actually durably succeeds.
        eventPublisher.publishCreated(saved.getId(), saved.getUser().getId(), saved.getTotalAmount());
        return orderMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId, ErrorCodes.ORDER_NOT_FOUND));
        return orderMapper.toDTO(order);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with number: " + orderNumber, ErrorCodes.ORDER_NOT_FOUND));
        return orderMapper.toDTO(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND);
        }
        Page<Order> orders = orderRepository.findByUserId(userId, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findOrdersByStatus(status, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
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

    /**
     * {@code ORD-<epochMillis>-<8 hex chars from SecureRandom>}.
     * Collision probability is dominated by the random suffix; retried on
     * {@code DataIntegrityViolationException} at the controller boundary.
     */
    private static String generateOrderNumber() {
        long ts = Instant.now().toEpochMilli();
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return "ORD-" + ts + "-" + HexFormat.of().formatHex(bytes).toUpperCase();
    }
}
