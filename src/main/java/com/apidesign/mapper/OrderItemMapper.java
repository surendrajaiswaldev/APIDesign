package com.apidesign.mapper;

import com.apidesign.dto.OrderItemDTO;
import com.apidesign.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for OrderItem entity and DTOs.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    /**
     * Maps OrderItem entity to OrderItemDTO.
     * Maps order.id to orderId and product.id to productId.
     *
     * @param orderItem the source entity
     * @return mapped DTO
     */
    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "product.id", target = "productId")
    OrderItemDTO toDTO(OrderItem orderItem);
}

