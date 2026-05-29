package com.apidesign.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.apidesign.controller.OrderController;
import com.apidesign.controller.UserController;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import com.apidesign.mapper.UserMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * Builds HAL representations of {@link User} entities.
 *
 * <p>Links added:
 *
 * <ul>
 *   <li>{@code self} — {@code GET /users/{id}}</li>
 *   <li>{@code users} — {@code GET /users} (collection)</li>
 *   <li>{@code orders} — {@code GET /orders/user/{id}} (this user's order history)</li>
 * </ul>
 */
@Component
public class UserModelAssembler implements RepresentationModelAssembler<User, EntityModel<UserDTO>> {

    private final UserMapper userMapper;

    public UserModelAssembler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public EntityModel<UserDTO> toModel(User user) {
        UserDTO dto = userMapper.toDTO(user);
        return EntityModel.of(
            dto,
            linkTo(methodOn(UserController.class).getUserById(user.getId())).withSelfRel(),
            linkTo(methodOn(UserController.class).getAllUsers(Pageable.unpaged())).withRel("users"),
            linkTo(methodOn(OrderController.class).getOrdersByUser(user.getId(), Pageable.unpaged()))
                .withRel("orders"));
    }
}
