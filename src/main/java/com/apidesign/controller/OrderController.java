package com.apidesign.controller;

import com.apidesign.assembler.OrderModelAssembler;
import com.apidesign.constants.ApiEndpoints;
import com.apidesign.constants.OrderStatus;
import com.apidesign.dto.CreateOrderRequest;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.response.ApiResponse;
import com.apidesign.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
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
@Tag(
    name = "Orders",
    description =
        "Order placement, retrieval, and status transitions. Creation runs the four-step "
            + "orchestrated saga (RESERVE_STOCK → CHARGE_PAYMENT → SCHEDULE_SHIPPING → COMPLETE_ORDER).")
public class OrderController {

    private final OrderService orderService;
    private final OrderModelAssembler orderAssembler;
    private final PagedResourcesAssembler<Order> pagedAssembler;

    public OrderController(
        OrderService orderService,
        OrderModelAssembler orderAssembler,
        PagedResourcesAssembler<Order> pagedAssembler) {
        this.orderService = orderService;
        this.orderAssembler = orderAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @Operation(
        summary = "Create an order via the saga orchestrator",
        description =
            "Walks RESERVE_STOCK → CHARGE_PAYMENT → SCHEDULE_SHIPPING → COMPLETE_ORDER. "
                + "On any step failure, completed steps are compensated in reverse and the order "
                + "is moved to CANCELLED. Supports the Idempotency-Key header for safe retries.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
        })
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {
        OrderDTO created = orderService.createOrder(request);
        return new ResponseEntity<>(
            ApiResponse.created(created, "Order created successfully"), HttpStatus.CREATED);
    }

    @Operation(
        summary = "Get order by ID",
        description = "Returns a HAL representation with self, user, and conditional cancel links.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @GetMapping(value = ApiEndpoints.ORDER_BY_ID, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<OrderDTO>> getOrderById(
        @Parameter(description = "Order identifier", example = "100")
        @PathVariable @Positive(message = "Order ID must be positive") Long id) {
        Order order = orderService.loadOrder(id);
        return ResponseEntity.ok(orderAssembler.toModel(order));
    }

    @Operation(
        summary = "Get order by order number",
        description = "Looks up an order by its human-readable order number (ORD-...).")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @GetMapping(value = "/number/{orderNumber}", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<OrderDTO>> getOrderByNumber(
        @Parameter(description = "Public order number", example = "ORD-1234567890-ABCDEF12")
        @PathVariable String orderNumber) {
        Order order = orderService.loadOrderByNumber(orderNumber);
        return ResponseEntity.ok(orderAssembler.toModel(order));
    }

    @Operation(
        summary = "List orders for a user",
        description = "Paginated orders belonging to a single user. Returns HAL PagedModel.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @GetMapping(value = ApiEndpoints.ORDER_BY_USER, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<PagedModel<EntityModel<OrderDTO>>> getOrdersByUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId,
        @PageableDefault(size = 20) Pageable pageable) {
        Page<Order> page = orderService.findOrdersByUser(userId, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, orderAssembler));
    }

    @Operation(
        summary = "List orders by status (ADMIN only)",
        description = "Paginated orders filtered by status — e.g. all PENDING orders.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
        })
    @GetMapping(value = "/by-status/{status}", produces = MediaTypes.HAL_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedModel<EntityModel<OrderDTO>>> getOrdersByStatus(
        @PathVariable OrderStatus status, @PageableDefault(size = 20) Pageable pageable) {
        Page<Order> page = orderService.findOrdersByStatus(status, pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, orderAssembler));
    }

    /**
     * Only admins can drive arbitrary status transitions. Customers should use
     * {@code POST /{id}/cancel} instead.
     */
    @Operation(
        summary = "Update order status (ADMIN only)",
        description = "Direct transition. Validates against OrderStatus.isValidTransition.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
        })
    @PutMapping(ApiEndpoints.ORDER_STATUS)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDTO>> updateOrderStatus(
        @PathVariable @Positive(message = "Order ID must be positive") Long id,
        @Valid @RequestBody UpdateStatusRequest request) {
        OrderDTO updated = orderService.updateOrderStatus(id, request.newStatus());
        return ResponseEntity.ok(ApiResponse.success(updated, "Order status updated successfully"));
    }

    @Operation(
        summary = "Cancel an order",
        description = "Customer-driven cancellation. Restores stock and publishes a domain event.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDTO>> cancelOrder(
        @PathVariable @Positive(message = "Order ID must be positive") Long id) {
        OrderDTO cancelled = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Order cancelled successfully"));
    }

    public record UpdateStatusRequest(
        @NotNull(message = "New status is required") OrderStatus newStatus) {}
}
