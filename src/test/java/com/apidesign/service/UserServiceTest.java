package com.apidesign.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.UserMapper;
import com.apidesign.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("UserService Tests")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    private CreateUserRequest createUserRequest;
    private User mockUser;
    private UserDTO mockUserDTO;

    @BeforeEach
    void setUp() {
        createUserRequest = new CreateUserRequest(
            "John", "Doe", "john@example.com", "password1", "1234567890",
            null, null, null, null);

        mockUser = User.builder()
            .id(1L)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .passwordHash("hashed")
            .phoneNumber("1234567890")
            .isActive(true)
            .roles(Set.of("USER"))
            .build();
        mockUser.setCreatedAt(LocalDateTime.now());
        mockUser.setUpdatedAt(LocalDateTime.now());

        mockUserDTO = new UserDTO(
            1L, "John", "Doe", "john@example.com", "1234567890",
            null, null, null, null, true, "CUSTOMER", Set.of("USER"), null, null);
    }

    @Test
    @DisplayName("Should create user successfully when email is unique")
    void testCreateUserSuccess() {
        when(userRepository.existsByEmail(createUserRequest.email())).thenReturn(false);
        when(userMapper.toEntity(createUserRequest)).thenReturn(mockUser);
        when(passwordEncoder.encode(createUserRequest.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userMapper.toDTO(mockUser)).thenReturn(mockUserDTO);

        UserDTO result = userService.createUser(createUserRequest);

        assertNotNull(result);
        assertEquals("john@example.com", result.email());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BusinessLogicException when email already exists")
    void testCreateUserFailsWhenEmailExists() {
        when(userRepository.existsByEmail(createUserRequest.email())).thenReturn(true);

        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class, () -> userService.createUser(createUserRequest));

        assertEquals(ErrorCodes.USER_EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve user by ID successfully")
    void testGetUserByIdSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userMapper.toDTO(mockUser)).thenReturn(mockUserDTO);

        UserDTO result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void testGetUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class, () -> userService.getUserById(999L));

        assertEquals(ErrorCodes.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should deactivate user successfully")
    void testDeactivateUserSuccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userMapper.toDTO(mockUser)).thenReturn(mockUserDTO);

        UserDTO result = userService.deactivateUser(1L);

        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when deactivating non-existent user")
    void testDeactivateUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.deactivateUser(999L));
    }

    @Test
    @DisplayName("Should delete user successfully when user exists")
    void testDeleteUserSuccess() {
        when(userRepository.existsById(1L)).thenReturn(true);
        userService.deleteUser(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUserNotFound() {
        when(userRepository.existsById(999L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(999L));
        verify(userRepository, never()).deleteById(any());
    }
}
