package com.apidesign.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.apidesign.constants.OrderStatus;
import com.apidesign.controller.OrderController;
import com.apidesign.controller.UserController;
import com.apidesign.dto.OrderDTO;
import com.apidesign.entity.Order;
import com.apidesign.mapper.OrderMapper;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Builds HAL representations of {@link Order} entities.
 *
 * <p>Links added:
 *
 * <ul>
 *   <li>{@code self} — {@code GET /orders/{id}}</li>
 *   <li>{@code user} — {@code GET /users/{userId}} (the customer who placed the order)</li>
 *   <li>{@code cancel} — {@code POST /orders/{id}/cancel} (conditional: only when the order
 *       is in PENDING or CONFIRMED status, mirroring the service-side guard)</li>
 * </ul>
 *
 * <p>The "order-items" sub-resource is intentionally embedded in the DTO itself rather than
 * given a separate URL — there's no dedicated endpoint for the collection today.
 */
@Component
public class OrderModelAssembler implements RepresentationModelAssembler<Order, EntityModel<OrderDTO>> {

    private final OrderMapper orderMapper;

    public OrderModelAssembler(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public EntityModel<OrderDTO> toModel(Order order) {
        OrderDTO dto = orderMapper.toDTO(order);
        EntityModel<OrderDTO> model =
            EntityModel.of(
                dto,
                linkTo(methodOn(OrderController.class).getOrderById(order.getId())).withSelfRel(),
                linkTo(methodOn(UserController.class).getUserById(order.getUser().getId()))
                    .withRel("user"));
        if (isCancellable(order.getOrderStatus())) {
            model.add(
                linkTo(methodOn(OrderController.class).cancelOrder(order.getId()))
                    .withRel("cancel"));
        }
        return model;
    }

    private static boolean isCancellable(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }
}
