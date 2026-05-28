package com.apidesign.dto;

import com.apidesign.constants.OrderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDTO(
    Long id,
    String orderNumber,
    UserDTO user,
    OrderStatus orderStatus,
    BigDecimal totalAmount,
    String shippingAddress,
    String notes,
    LocalDateTime estimatedDelivery,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<OrderItemDTO> orderItems) {}
