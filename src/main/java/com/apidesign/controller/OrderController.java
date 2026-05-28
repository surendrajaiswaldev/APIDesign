package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Order REST Controller for order management endpoints.
 *
 * Complex Business Logic Endpoints:
 * - Create order with inventory validation
 * - Status transitions with state machine validation
 * - Order cancellation with stock return
 * - Order retrieval with various filters
 *
 * HATEOAS is particularly useful here to guide status transitions:
 * Client can see what transitions are available from response links.
 */
@Slf4j
@RestController
@RequestMapping(ApiEndpoints.ORDER_BASE_PATH)
@Validated
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order with complex business validation.
     *
     * HTTP: POST /api/v1/orders
     * Status: 201 (Created)
     *
     * Business Logic:
     * - Validate user exists
     * - Validate products exist and have stock
     * - Reserve inventory
     * - Calculate total
     * - Create order with items
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("Creating order for user: {}", request.getUserId());
        OrderDTO createdOrder = orderService.createOrder(request);

        ApiResponse<OrderDTO> response = ApiResponse.success(
            createdOrder,
            "Order created successfully"
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get order by ID with full details (user and items).
     *
     * HTTP: GET /api/v1/orders/{id}
     */
    @GetMapping(ApiEndpoints.ORDER_BY_ID)
    public ResponseEntity<ApiResponse<EntityModel<OrderDTO>>> getOrderById(
            @PathVariable
            @Positive(message = "Order ID must be positive")
            Long id) {

        log.info("Fetching order: {}", id);
        OrderDTO order = orderService.getOrderById(id);

        // Add HATEOAS links for available actions
        EntityModel<OrderDTO> orderModel = EntityModel.of(order,
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(OrderController.class).getOrderById(id))
                .withSelfRel(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(OrderController.class)
                    .getOrdersByUser(order.getUser().getId(),
                        org.springframework.data.domain.PageRequest.of(0, 20)))
                .withRel("user-orders"),
            WebMvcLinkBuilder.linkTo(OrderController.class)
                .withRel("all-orders")
        );

        ApiResponse<EntityModel<OrderDTO>> response = ApiResponse.success(
            orderModel,
            "Order retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get order by order number (customer-facing identifier).
     *
     * HTTP: GET /api/v1/orders/number/{orderNumber}
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrderByNumber(
            @PathVariable String orderNumber) {

        log.info("Fetching order by number: {}", orderNumber);
        OrderDTO order = orderService.getOrderByNumber(orderNumber);

        ApiResponse<OrderDTO> response = ApiResponse.success(
            order,
            "Order retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all orders for a specific user with pagination.
     *
     * HTTP: GET /api/v1/orders/user/{userId}?page=0&size=20
     */
    @GetMapping(ApiEndpoints.ORDER_BY_USER)
    public ResponseEntity<ApiResponse<CollectionModel<OrderDTO>>> getOrdersByUser(
            @PathVariable
            @Positive(message = "User ID must be positive")
            Long userId,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching orders for user: {}", userId);
        PagedResponse<OrderDTO> pagedOrders = orderService.getOrdersByUser(userId, pageable);

        CollectionModel<OrderDTO> orderCollection = CollectionModel.of(
            pagedOrders.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(OrderController.class)
                    .getOrdersByUser(userId, pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<OrderDTO>> response = ApiResponse.success(
            orderCollection,
            "Orders retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get orders by status with pagination.
     *
     * HTTP: GET /api/v1/orders/status/{status}?page=0&size=20
     *
     * Status Values: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<ApiResponse<CollectionModel<OrderDTO>>> getOrdersByStatus(
            @PathVariable
            @NotBlank(message = "Status is required")
            String status,
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching orders by status: {}", status);
        PagedResponse<OrderDTO> pagedOrders = orderService.getOrdersByStatus(status, pageable);

        CollectionModel<OrderDTO> orderCollection = CollectionModel.of(
            pagedOrders.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(OrderController.class)
                    .getOrdersByStatus(status, pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<OrderDTO>> response = ApiResponse.success(
            orderCollection,
            "Orders retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Update order status with state machine validation.
     *
     * HTTP: PUT /api/v1/orders/{id}/status
     * Body: { "newStatus": "CONFIRMED" }
     *
     * Valid Transitions:
     * - PENDING -> CONFIRMED or CANCELLED
     * - CONFIRMED -> SHIPPED or CANCELLED
     * - SHIPPED -> DELIVERED
     * - DELIVERED, CANCELLED -> Terminal (no transitions)
     */
    @PutMapping(ApiEndpoints.ORDER_STATUS)
    public ResponseEntity<ApiResponse<OrderDTO>> updateOrderStatus(
            @PathVariable
            @Positive(message = "Order ID must be positive")
            Long id,
            @RequestBody UpdateStatusRequest request) {

        log.info("Updating order status - Order ID: {}, New Status: {}", id, request.getNewStatus());
        OrderDTO updatedOrder = orderService.updateOrderStatus(id, request.getNewStatus());

        ApiResponse<OrderDTO> response = ApiResponse.success(
            updatedOrder,
            "Order status updated successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel an order.
     *
     * HTTP: POST /api/v1/orders/{id}/cancel
     *
     * Business Logic:
     * - Can only cancel PENDING or CONFIRMED orders
     * - Returns inventory to stock
     * - Sets status to CANCELLED
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDTO>> cancelOrder(
            @PathVariable
            @Positive(message = "Order ID must be positive")
            Long id) {

        log.info("Cancelling order: {}", id);
        OrderDTO cancelledOrder = orderService.cancelOrder(id);

        ApiResponse<OrderDTO> response = ApiResponse.success(
            cancelledOrder,
            "Order cancelled successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for order status updates.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotBlank(message = "New status is required")
        private String newStatus;
    }
}

