package com.apidesign.mapper;

import com.apidesign.dto.CreateProductRequest;
import com.apidesign.dto.ProductDTO;
import com.apidesign.dto.UpdateProductRequest;
import com.apidesign.entity.Product;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T09:56:15+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductDTO toDTO(Product product) {
        if ( product == null ) {
            return null;
        }

        ProductDTO.ProductDTOBuilder productDTO = ProductDTO.builder();

        productDTO.category( product.getCategory() );
        productDTO.createdAt( product.getCreatedAt() );
        productDTO.description( product.getDescription() );
        productDTO.id( product.getId() );
        productDTO.isAvailable( product.getIsAvailable() );
        productDTO.minStockLevel( product.getMinStockLevel() );
        productDTO.name( product.getName() );
        productDTO.price( product.getPrice() );
        productDTO.sku( product.getSku() );
        productDTO.stockQuantity( product.getStockQuantity() );
        productDTO.supplier( product.getSupplier() );
        productDTO.updatedAt( product.getUpdatedAt() );

        return productDTO.build();
    }

    @Override
    public Product toEntity(CreateProductRequest request) {
        if ( request == null ) {
            return null;
        }

        Product.ProductBuilder<?, ?> product = Product.builder();

        product.category( request.getCategory() );
        product.description( request.getDescription() );
        product.minStockLevel( request.getMinStockLevel() );
        product.name( request.getName() );
        product.price( request.getPrice() );
        product.sku( request.getSku() );
        product.stockQuantity( request.getStockQuantity() );
        product.supplier( request.getSupplier() );

        return product.build();
    }

    @Override
    public void updateEntityFromRequest(UpdateProductRequest request, Product product) {
        if ( request == null ) {
            return;
        }

        if ( request.getCategory() != null ) {
            product.setCategory( request.getCategory() );
        }
        if ( request.getDescription() != null ) {
            product.setDescription( request.getDescription() );
        }
        if ( request.getIsAvailable() != null ) {
            product.setIsAvailable( request.getIsAvailable() );
        }
        if ( request.getMinStockLevel() != null ) {
            product.setMinStockLevel( request.getMinStockLevel() );
        }
        if ( request.getName() != null ) {
            product.setName( request.getName() );
        }
        if ( request.getPrice() != null ) {
            product.setPrice( request.getPrice() );
        }
        if ( request.getStockQuantity() != null ) {
            product.setStockQuantity( request.getStockQuantity() );
        }
        if ( request.getSupplier() != null ) {
            product.setSupplier( request.getSupplier() );
        }
    }
}
