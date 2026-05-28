package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiEndpoints.ORDER_BASE_PATH)
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {
        OrderDTO created = orderService.createOrder(request);
        return new ResponseEntity<>(
            ApiResponse.created(created, "Order created successfully"), HttpStatus.CREATED);
    }

    @GetMapping(ApiEndpoints.ORDER_BY_ID)
    public ResponseEntity<ApiResponse<OrderDTO>> getOrderById(
        @PathVariable @Positive(message = "Order ID must be positive") Long id) {
        OrderDTO order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order, "Order retrieved successfully"));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrderByNumber(
        @PathVariable String orderNumber) {
        OrderDTO order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(order, "Order retrieved successfully"));
    }

    @GetMapping(ApiEndpoints.ORDER_BY_USER)
    public ResponseEntity<ApiResponse<PagedResponse<OrderDTO>>> getOrdersByUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId,
        @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<OrderDTO> paged = orderService.getOrdersByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(paged, "Orders retrieved successfully"));
    }

    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<OrderDTO>>> getOrdersByStatus(
        @PathVariable OrderStatus status, @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<OrderDTO> paged = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(paged, "Orders retrieved successfully"));
    }

    /**
     * Only admins can drive arbitrary status transitions. Customers should use
     * {@code POST /{id}/cancel} instead.
     */
    @PutMapping(ApiEndpoints.ORDER_STATUS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDTO>> updateOrderStatus(
        @PathVariable @Positive(message = "Order ID must be positive") Long id,
        @Valid @RequestBody UpdateStatusRequest request) {
        OrderDTO updated = orderService.updateOrderStatus(id, request.newStatus());
        return ResponseEntity.ok(ApiResponse.success(updated, "Order status updated successfully"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDTO>> cancelOrder(
        @PathVariable @Positive(message = "Order ID must be positive") Long id) {
        OrderDTO cancelled = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Order cancelled successfully"));
    }

    public record UpdateStatusRequest(
        @NotNull(message = "New status is required") OrderStatus newStatus) {}
}
