package com.apidesign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDTO(
    Long id,
    String sku,
    String name,
    String description,
    BigDecimal price,
    Long stockQuantity,
    Long minStockLevel,
    String category,
    Boolean isAvailable,
    String supplier,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
