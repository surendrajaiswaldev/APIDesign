package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.UserMapper;
import com.apidesign.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 *
 * Testing Approach:
 * - Use @ExtendWith(MockitoExtension.class) for JUnit 5
 * - Mock external dependencies (repositories, mappers)
 * - Test service layer business logic in isolation
 * - Verify exception handling
 * - Use descriptive test method names (@DisplayName)
 *
 * Testing Patterns:
 * - Arrange-Act-Assert (AAA)
 * - One assertion per test when possible
 * - Test both happy path and error scenarios
 */
@DisplayName("UserService Tests")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private CreateUserRequest createUserRequest;
    private User mockUser;
    private UserDTO mockUserDTO;

    @BeforeEach
    void setUp() {
        // Arrange: Set up test data
        createUserRequest = CreateUserRequest.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .phoneNumber("1234567890")
            .build();

        mockUser = User.builder()
            .id(1L)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .phoneNumber("1234567890")
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        mockUserDTO = UserDTO.builder()
            .id(1L)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .phoneNumber("1234567890")
            .isActive(true)
            .build();
    }

    @Test
    @DisplayName("Should create user successfully when email is unique")
    void testCreateUserSuccess() {
        // Arrange
        when(userRepository.existsByEmail(createUserRequest.getEmail()))
            .thenReturn(false);
        when(userMapper.toEntity(createUserRequest))
            .thenReturn(mockUser);
        when(userRepository.save(any(User.class)))
            .thenReturn(mockUser);
        when(userMapper.toDTO(mockUser))
            .thenReturn(mockUserDTO);

        // Act
        UserDTO result = userService.createUser(createUserRequest);

        // Assert
        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        assertEquals("John", result.getFirstName());

        // Verify repository was called
        verify(userRepository, times(1)).existsByEmail(createUserRequest.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BusinessLogicException when email already exists")
    void testCreateUserFailsWhenEmailExists() {
        // Arrange
        when(userRepository.existsByEmail(createUserRequest.getEmail()))
            .thenReturn(true);

        // Act & Assert
        BusinessLogicException exception = assertThrows(
            BusinessLogicException.class,
            () -> userService.createUser(createUserRequest)
        );

        assertEquals(ErrorCodes.USER_EMAIL_ALREADY_EXISTS, exception.getErrorCode());

        // Verify save was never called
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve user by ID successfully")
    void testGetUserByIdSuccess() {
        // Arrange
        Long userId = 1L;
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(mockUser));
        when(userMapper.toDTO(mockUser))
            .thenReturn(mockUserDTO);

        // Act
        UserDTO result = userService.getUserById(userId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("John", result.getFirstName());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void testGetUserByIdNotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> userService.getUserById(userId)
        );

        assertEquals(ErrorCodes.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should deactivate user successfully")
    void testDeactivateUserSuccess() {
        // Arrange
        Long userId = 1L;
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class)))
            .thenReturn(mockUser);
        when(userMapper.toDTO(mockUser))
            .thenReturn(mockUserDTO);

        // Act
        UserDTO result = userService.deactivateUser(userId);

        // Assert
        assertNotNull(result);

        // Verify user was marked inactive
        verify(userRepository, times(1)).save(argThat(user ->
            user.getId().equals(userId) && !user.getIsActive()
        ));
    }

    @Test
    @DisplayName("Should throw exception when deactivating non-existent user")
    void testDeactivateUserNotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> userService.deactivateUser(userId));
    }

    @Test
    @DisplayName("Should delete user successfully when user exists")
    void testDeleteUserSuccess() {
        // Arrange
        Long userId = 1L;
        when(userRepository.existsById(userId))
            .thenReturn(true);

        // Act
        userService.deleteUser(userId);

        // Assert
        // Verify delete was called
        verify(userRepository, times(1)).deleteById(userId);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void testDeleteUserNotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.existsById(userId))
            .thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
            () -> userService.deleteUser(userId));

        // Verify delete was not called
        verify(userRepository, never()).deleteById(any());
    }
}

