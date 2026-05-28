package com.apidesign.mapper;

import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for Order entity and DTOs.
 *
 * Note on relationships:
 * - user: Injected UserMapper handles nested mapping
 * - orderItems: Injected OrderItemMapper handles collection mapping
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {UserMapper.class, OrderItemMapper.class})
public interface OrderMapper {

    /**
     * Maps Order entity to OrderDTO.
     * Automatically maps nested user and orderItems using injected mappers.
     *
     * @param order the source entity
     * @return mapped DTO with full details
     */
    @Mapping(source = "user", target = "user")
    @Mapping(source = "orderItems", target = "orderItems")
    OrderDTO toDTO(Order order);
}

