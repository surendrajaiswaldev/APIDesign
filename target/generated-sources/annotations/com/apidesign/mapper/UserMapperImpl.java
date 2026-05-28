package com.apidesign.mapper;

import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-28T09:56:15+0530",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserDTO toDTO(User user) {
        if ( user == null ) {
            return null;
        }

        UserDTO.UserDTOBuilder userDTO = UserDTO.builder();

        userDTO.address( user.getAddress() );
        userDTO.city( user.getCity() );
        userDTO.createdAt( user.getCreatedAt() );
        userDTO.email( user.getEmail() );
        userDTO.firstName( user.getFirstName() );
        userDTO.id( user.getId() );
        userDTO.isActive( user.getIsActive() );
        userDTO.lastName( user.getLastName() );
        userDTO.phoneNumber( user.getPhoneNumber() );
        userDTO.state( user.getState() );
        userDTO.updatedAt( user.getUpdatedAt() );
        userDTO.userType( user.getUserType() );
        userDTO.zipcode( user.getZipcode() );

        return userDTO.build();
    }

    @Override
    public User toEntity(CreateUserRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder<?, ?> user = User.builder();

        user.address( request.getAddress() );
        user.city( request.getCity() );
        user.email( request.getEmail() );
        user.firstName( request.getFirstName() );
        user.lastName( request.getLastName() );
        user.phoneNumber( request.getPhoneNumber() );
        user.state( request.getState() );
        user.zipcode( request.getZipcode() );

        return user.build();
    }

    @Override
    public void updateEntityFromRequest(UpdateUserRequest request, User user) {
        if ( request == null ) {
            return;
        }

        if ( request.getAddress() != null ) {
            user.setAddress( request.getAddress() );
        }
        if ( request.getCity() != null ) {
            user.setCity( request.getCity() );
        }
        if ( request.getEmail() != null ) {
            user.setEmail( request.getEmail() );
        }
        if ( request.getFirstName() != null ) {
            user.setFirstName( request.getFirstName() );
        }
        if ( request.getIsActive() != null ) {
            user.setIsActive( request.getIsActive() );
        }
        if ( request.getLastName() != null ) {
            user.setLastName( request.getLastName() );
        }
        if ( request.getPhoneNumber() != null ) {
            user.setPhoneNumber( request.getPhoneNumber() );
        }
        if ( request.getState() != null ) {
            user.setState( request.getState() );
        }
        if ( request.getZipcode() != null ) {
            user.setZipcode( request.getZipcode() );
        }
    }
}
