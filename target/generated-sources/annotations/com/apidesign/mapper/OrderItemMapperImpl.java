package com.apidesign.mapper;

import com.apidesign.dto.OrderItemDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import com.apidesign.entity.Product;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T09:56:15+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class OrderItemMapperImpl implements OrderItemMapper {

    @Override
    public OrderItemDTO toDTO(OrderItem orderItem) {
        if ( orderItem == null ) {
            return null;
        }

        OrderItemDTO.OrderItemDTOBuilder orderItemDTO = OrderItemDTO.builder();

        orderItemDTO.orderId( orderItemOrderId( orderItem ) );
        orderItemDTO.productId( orderItemProductId( orderItem ) );
        orderItemDTO.createdAt( orderItem.getCreatedAt() );
        orderItemDTO.discount( orderItem.getDiscount() );
        orderItemDTO.id( orderItem.getId() );
        orderItemDTO.notes( orderItem.getNotes() );
        orderItemDTO.productName( orderItem.getProductName() );
        orderItemDTO.productSku( orderItem.getProductSku() );
        orderItemDTO.quantity( orderItem.getQuantity() );
        orderItemDTO.unitPrice( orderItem.getUnitPrice() );

        return orderItemDTO.build();
    }

    private Long orderItemOrderId(OrderItem orderItem) {
        if ( orderItem == null ) {
            return null;
        }
        Order order = orderItem.getOrder();
        if ( order == null ) {
            return null;
        }
        Long id = order.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private Long orderItemProductId(OrderItem orderItem) {
        if ( orderItem == null ) {
            return null;
        }
        Product product = orderItem.getProduct();
        if ( product == null ) {
            return null;
        }
        Long id = product.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
