package com.apidesign.mapper;

import com.apidesign.dto.OrderDTO;
import com.apidesign.dto.OrderItemDTO;
import com.apidesign.entity.Order;
import com.apidesign.entity.OrderItem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T09:56:15+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class OrderMapperImpl implements OrderMapper {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;

    @Override
    public OrderDTO toDTO(Order order) {
        if ( order == null ) {
            return null;
        }

        OrderDTO.OrderDTOBuilder orderDTO = OrderDTO.builder();

        orderDTO.user( userMapper.toDTO( order.getUser() ) );
        orderDTO.orderItems( orderItemListToOrderItemDTOList( order.getOrderItems() ) );
        orderDTO.createdAt( order.getCreatedAt() );
        orderDTO.estimatedDelivery( order.getEstimatedDelivery() );
        orderDTO.id( order.getId() );
        orderDTO.notes( order.getNotes() );
        orderDTO.orderNumber( order.getOrderNumber() );
        orderDTO.orderStatus( order.getOrderStatus() );
        orderDTO.shippingAddress( order.getShippingAddress() );
        orderDTO.totalAmount( order.getTotalAmount() );
        orderDTO.updatedAt( order.getUpdatedAt() );

        return orderDTO.build();
    }

    protected List<OrderItemDTO> orderItemListToOrderItemDTOList(List<OrderItem> list) {
        if ( list == null ) {
            return null;
        }

        List<OrderItemDTO> list1 = new ArrayList<OrderItemDTO>( list.size() );
        for ( OrderItem orderItem : list ) {
            list1.add( orderItemMapper.toDTO( orderItem ) );
        }

        return list1;
    }
}
