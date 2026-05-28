package com.apidesign.mapper;

import com.apidesign.dto.UserDTO;
import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.entity.User;
import org.mapstruct.*;

/**
 * MapStruct mapper for User entity and DTOs.
 *
 * Benefits of MapStruct over manual mapping:
 * - Type-safe mapping with compile-time code generation
 * - Zero-runtime reflection overhead
 * - Automatically handles null checks
 * - Supports partial updates via @MappingTarget
 * - Better performance than BeanUtils.copyProperties
 *
 * Configuration:
 * - componentModel: "spring" injects mapper as Spring bean
 * - nullValuePropertyMappingStrategy: Property not set to null if source is null
 */
@Mapper(componentModel = "spring",unmappedTargetPolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    /**
     * Maps User entity to UserDTO.
     *
     * @param user the source entity
     * @return mapped DTO
     */
    UserDTO toDTO(User user);

    /**
     * Maps CreateUserRequest to User entity.
     *
     * @param request the create request
     * @return mapped entity
     */
    User toEntity(CreateUserRequest request);

    /**
     * Updates existing User entity from UpdateUserRequest (partial update).
     * Properties that are null in the request are not mapped (due to IGNORE strategy).
     *
     * @param request the update request
     * @param user the target entity to update
     */
    void updateEntityFromRequest(UpdateUserRequest request, @MappingTarget User user);
}

