package com.apidesign.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.apidesign.controller.ProductController;
import com.apidesign.dto.ProductDTO;
import com.apidesign.entity.Product;
import com.apidesign.mapper.ProductMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Builds HAL representations of {@link Product} entities.
 *
 * <p>Links added:
 *
 * <ul>
 *   <li>{@code self} — {@code GET /products/{id}}</li>
 *   <li>{@code products} — {@code GET /products} (collection)</li>
 *   <li>{@code by-category} — {@code GET /products/category/{category}} (only when the
 *       product has a non-blank category)</li>
 * </ul>
 */
@Component
public class ProductModelAssembler
    implements RepresentationModelAssembler<Product, EntityModel<ProductDTO>> {

    private final ProductMapper productMapper;

    public ProductModelAssembler(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public EntityModel<ProductDTO> toModel(Product product) {
        ProductDTO dto = productMapper.toDTO(product);
        EntityModel<ProductDTO> model =
            EntityModel.of(
                dto,
                linkTo(methodOn(ProductController.class).getProductById(product.getId()))
                    .withSelfRel(),
                linkTo(methodOn(ProductController.class).getAllProducts(Pageable.unpaged()))
                    .withRel("products"));
        if (product.getCategory() != null && !product.getCategory().isBlank()) {
            model.add(
                linkTo(
                    methodOn(ProductController.class)
                        .getProductsByCategory(product.getCategory(), Pageable.unpaged()))
                    .withRel("by-category"));
        }
        return model;
    }
}
