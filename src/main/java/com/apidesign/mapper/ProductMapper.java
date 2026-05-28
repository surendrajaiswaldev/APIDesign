package com.apidesign.mapper;

import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for Product entity and DTOs.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    /**
     * Maps Product entity to ProductDTO.
     */
    ProductDTO toDTO(Product product);

    /**
     * Maps CreateProductRequest to Product entity.
     */
    Product toEntity(CreateProductRequest request);

    /**
     * Updates existing Product entity from UpdateProductRequest.
     */
    void updateEntityFromRequest(UpdateProductRequest request, @MappingTarget Product product);
}

