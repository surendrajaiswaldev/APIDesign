package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.Product;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.OrderMapper;
import com.apidesign.repository.OrderItemRepository;
import com.apidesign.repository.OrderRepository;
import com.apidesign.repository.ProductRepository;
import com.apidesign.repository.UserRepository;
import com.apidesign.response.PagedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Order Service - Complex business logic for order management.
 *
 * Key Responsibilities:
 * 1. Order lifecycle management (create, status transitions, shipping)
 * 2. Stock management (check availability, reduce stock)
 * 3. Price calculation and total computation
 * 4. Order validation (duplicate items, inventory, user)
 * 5. Order retrieval with various filters
 *
 * Why @Transactional at class level?
 * - All methods are transactional by default
 * - Ensures data consistency across multiple DB operations
 * - Rollback if any operation fails (atomicity)
 * - Override with readOnly=true for query methods
 *
 * Transaction Boundaries:
 * - Start: First DB operation in method
 * - End: Method returns Successfully or throws exception
 * - On exception: All changes rolled back
 * - Prevents partial orders in DB
 */
@Slf4j
@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository,
                       OrderMapper orderMapper,
                       ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.productService = productService;
    }

    /**
     * Create a new order with complex business logic.
     *
     * Business Steps:
     * 1. Validate user exists
     * 2. Validate order has items
     * 3. Validate no duplicate products in same order
     * 4. Validate each product exists and has sufficient stock
     * 5. Reserve stock (reduce product stock)
     * 6. Calculate total amount
     * 7. Save order with items
     * 8. Generate order number
     *
     * Transactional Consistency:
     * - If any step fails, entire order creation rolls back
     * - No partial orders in database
     * - Stock changes rollback if validation fails later
     *
     * @param request the create order request
     * @return created order DTO
     * @throws ResourceNotFoundException if user/product not found
     * @throws BusinessLogicException if validation fails
     */
    public OrderDTO createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());

        // Step 1: Validate user exists
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + request.getUserId(),
                ErrorCodes.USER_NOT_FOUND
            ));

        // Step 2: Validate order items
        if (request.getOrderItems() == null || request.getOrderItems().isEmpty()) {
            throw new BusinessLogicException(
                "Order must contain at least one item",
                ErrorCodes.ORDER_EMPTY_ITEMS
            );
        }

        // Step 3: Validate no duplicate products
        Set<Long> productIds = new HashSet<>();
        for (CreateOrderRequest.OrderItemRequest item : request.getOrderItems()) {
            if (!productIds.add(item.getProductId())) {
                throw new BusinessLogicException(
                    "Order contains duplicate product: " + item.getProductId(),
                    ErrorCodes.ORDER_DUPLICATE_ITEMS
                );
            }
        }

        // Step 4 & 5: Validate products exist and reserve stock
        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = Order.builder()
            .orderNumber(generateOrderNumber())
            .user(user)
            .orderStatus(OrderStatus.PENDING)
            .shippingAddress(request.getShippingAddress() != null ?
                request.getShippingAddress() : user.getAddress())
            .notes(request.getNotes())
            .build();

        // Create order items and calculate total
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getOrderItems()) {
            // Validate product exists
            Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Product not found with ID: " + itemRequest.getProductId(),
                    ErrorCodes.ORDER_ITEM_PRODUCT_NOT_FOUND
                ));

            // Check stock availability
            if (!product.hasEnoughStock(itemRequest.getQuantity())) {
                log.warn("Insufficient stock for product: {} - Available: {}, Requested: {}",
                        product.getId(), product.getStockQuantity(), itemRequest.getQuantity());
                throw new BusinessLogicException(
                    "Insufficient stock for product: " + product.getName(),
                    ErrorCodes.ORDER_INSUFFICIENT_STOCK
                );
            }

            // Create order item (stores current product price snapshot)
            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .productSku(product.getSku())
                .unitPrice(product.getPrice())
                .quantity(itemRequest.getQuantity())
                .notes(itemRequest.getNotes())
                .build();

            order.addOrderItem(orderItem);

            // Add to total (unitPrice * quantity)
            BigDecimal itemTotal = product.getPrice()
                .multiply(new BigDecimal(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            // Reduce product stock (transaction handles rollback)
            productService.reduceStock(product.getId(), itemRequest.getQuantity());
        }

        // Set total amount
        order.setTotalAmount(totalAmount);

        // Save order (includes items due to CASCADE.PERSIST)
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully - Order ID: {}, Order Number: {}, Total: {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

        return orderMapper.toDTO(savedOrder);
    }

    /**
     * Get order by ID with details (user and items fetched).
     *
     * @param orderId the order ID
     * @return order DTO with full details
     * @throws ResourceNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long orderId) {
        log.debug("Fetching order with ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId,
                ErrorCodes.ORDER_NOT_FOUND
            ));

        return orderMapper.toDTO(order);
    }

    /**
     * Get order by order number (customer-facing identifier).
     *
     * @param orderNumber the order number
     * @return order DTO
     * @throws ResourceNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        log.debug("Fetching order with number: {}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with number: " + orderNumber,
                ErrorCodes.ORDER_NOT_FOUND
            ));

        return orderMapper.toDTO(order);
    }

    /**
     * Get all orders for a user with pagination.
     * Uses @EntityGraph to prevent N+1 problem.
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return paginated orders
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByUser(Long userId, Pageable pageable) {
        log.debug("Fetching orders for user: {} - Page: {}", userId, pageable.getPageNumber());

        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                "User not found with ID: " + userId,
                ErrorCodes.USER_NOT_FOUND
            );
        }

        Page<Order> orders = orderRepository.findByUserId(userId, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
    }

    /**
     * Get orders by status with pagination.
     *
     * @param status the order status
     * @param pageable pagination
     * @return paginated orders
     */
    @Transactional(readOnly = true)
    public PagedResponse<OrderDTO> getOrdersByStatus(String status, Pageable pageable) {
        log.debug("Fetching orders with status: {} - Page: {}", status, pageable.getPageNumber());

        Page<Order> orders = orderRepository.findOrdersByStatus(status, pageable);
        return PagedResponse.from(orders.map(orderMapper::toDTO));
    }

    /**
     * Update order status with validation.
     *
     * Business Logic:
     * - Validate current status allows transition to new status
     * - PENDING -> CONFIRMED, CANCELLED
     * - CONFIRMED -> SHIPPED, CANCELLED
     * - SHIPPED -> DELIVERED
     * - DELIVERED, CANCELLED -> no transitions (terminal states)
     *
     * @param orderId the order ID
     * @param newStatus the new status
     * @return updated order DTO
     * @throws ResourceNotFoundException if order not found
     * @throws BusinessLogicException if transition invalid
     */
    public OrderDTO updateOrderStatus(Long orderId, String newStatus) {
        log.info("Updating order status - Order ID: {}, New Status: {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId,
                ErrorCodes.ORDER_NOT_FOUND
            ));

        // Validate status transition
        if (!OrderStatus.isValidTransition(order.getOrderStatus(), newStatus)) {
            log.warn("Invalid status transition - From: {}, To: {}",
                    order.getOrderStatus(), newStatus);
            throw new BusinessLogicException(
                "Invalid status transition from " + order.getOrderStatus() + " to " + newStatus,
                ErrorCodes.ORDER_INVALID_STATUS_TRANSITION
            );
        }

        order.setOrderStatus(newStatus);

        // Update estimated delivery if transitioning to SHIPPED
        if (newStatus.equals(OrderStatus.SHIPPED)) {
            order.setEstimatedDelivery(LocalDateTime.now().plusDays(5));
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Order status updated successfully: {}", orderId);

        return orderMapper.toDTO(updatedOrder);
    }

    /**
     * Cancel an order.
     *
     * Business Logic:
     * - Can only cancel PENDING or CONFIRMED orders
     * - Return stock to inventory
     * - Set status to CANCELLED
     *
     * @param orderId the order ID
     * @return cancelled order DTO
     * @throws ResourceNotFoundException if order not found
     * @throws BusinessLogicException if order can't be cancelled
     */
    public OrderDTO cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found with ID: " + orderId,
                ErrorCodes.ORDER_NOT_FOUND
            ));

        // Validate order can be cancelled
        if (!order.getOrderStatus().equals(OrderStatus.PENDING) &&
            !order.getOrderStatus().equals(OrderStatus.CONFIRMED)) {
            throw new BusinessLogicException(
                "Cannot cancel order with status: " + order.getOrderStatus(),
                ErrorCodes.ORDER_INVALID_STATUS_TRANSITION
            );
        }

        // Return stock to inventory
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);

        log.info("Order cancelled successfully: {}", orderId);
        return orderMapper.toDTO(cancelledOrder);
    }

    /**
     * Generate unique order number.
     * Format: ORD-YYYYMMDD-XXXXX (timestamp + random)
     * Ensures uniqueness and readability.
     *
     * @return generated order number
     */
    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return "ORD-" + timestamp + "-" + random;
    }
}

