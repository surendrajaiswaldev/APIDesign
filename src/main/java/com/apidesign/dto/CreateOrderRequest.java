package com.apidesign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
    @NotNull(message = "User ID is required") @Positive(message = "User ID must be positive")
        Long userId,
    @NotEmpty(message = "Order must contain at least one item")
        @Size(min = 1, max = 100, message = "Order must contain between 1 and 100 items")
        @Valid
        List<OrderItemRequest> orderItems,
    String shippingAddress,
    @Size(max = 500, message = "Notes must not exceed 500 characters") String notes) {

    public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
            @Positive(message = "Product ID must be positive")
            Long productId,
        @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be positive")
            Long quantity,
        String notes) {}
}
