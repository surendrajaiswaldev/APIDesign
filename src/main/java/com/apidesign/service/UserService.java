package com.apidesign.service;

import com.apidesign.constants.ErrorCodes;
import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import com.apidesign.exception.BusinessLogicException;
import com.apidesign.exception.ResourceNotFoundException;
import com.apidesign.mapper.UserMapper;
import com.apidesign.repository.UserRepository;
import com.apidesign.response.PagedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Service - Business logic layer for user management.
 *
 * Service Layer Responsibilities:
 * 1. Implement business logic (validation, calculations, workflows)
 * 2. Interact with repositories for data access
 * 3. Handle transactions (@Transactional)
 * 4. Throw appropriate exceptions for error cases
 * 5. Use DTOs for API boundaries (not entities)
 *
 * Why not expose entities directly?
 * - Entities are persistence objects, not API contracts
 * - Entity changes shouldn't break API
 * - DTOs separate internal model from external contracts
 * - Controllers stay thin (no business logic)
 * - Services are reusable across different APIs
 *
 * @Service annotation:
 * - Makes this a Spring Bean
 * - Enables @Transactional support
 * - Enables AOP proxying for logging/monitoring
 */
@Slf4j
@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // Constructor injection - preferred over field injection
    // Benefits: Testable (can mock dependencies), explicit dependencies, immutable
    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Create a new user.
     *
     * Business Logic:
     * 1. Validate email doesn't already exist
     * 2. Convert request DTO to entity using mapper
     * 3. Save entity to database
     * 4. Return created user as DTO
     *
     * @param request the create user request
     * @return created user DTO
     * @throws BusinessLogicException if email already exists
     */
    public UserDTO createUser(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        // Business validation: Ensure unique email
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("User creation failed - email already exists: {}", request.getEmail());
            throw new BusinessLogicException(
                "User with email " + request.getEmail() + " already exists",
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS
            );
        }

        // Map request to entity and save
        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);

        log.info("User created successfully with ID: {}", savedUser.getId());
        return userMapper.toDTO(savedUser);
    }

    /**
     * Get user by ID.
     *
     * @param userId the user ID
     * @return user DTO
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long userId) {
        log.debug("Fetching user with ID: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId,
                ErrorCodes.USER_NOT_FOUND
            ));

        return userMapper.toDTO(user);
    }

    /**
     * Get user by email.
     *
     * @param email the user's email
     * @return user DTO
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        log.debug("Fetching user with email: {}", email);

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with email: " + email,
                ErrorCodes.USER_NOT_FOUND
            ));

        return userMapper.toDTO(user);
    }

    /**
     * Update an existing user (partial update).
     *
     * Benefits of partial update:
     * - Clients don't need to provide all fields
     * - Backwards compatible if new fields are added
     * - MapStruct's IGNORE strategy: null values aren't mapped
     *
     * @param userId the user ID
     * @param request the update request with optional fields
     * @return updated user DTO
     * @throws ResourceNotFoundException if user not found
     */
    public UserDTO updateUser(Long userId, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId,
                ErrorCodes.USER_NOT_FOUND
            ));

        // Check if email changed and is unique
        if (request.getEmail() != null &&
            !request.getEmail().equals(user.getEmail()) &&
            userRepository.existsByEmail(request.getEmail())) {
            log.warn("User update failed - new email already exists: {}", request.getEmail());
            throw new BusinessLogicException(
                "Email " + request.getEmail() + " is already in use",
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS
            );
        }

        // Apply updates using mapper (only non-null fields)
        userMapper.updateEntityFromRequest(request, user);
        User updatedUser = userRepository.save(user);

        log.info("User updated successfully: {}", userId);
        return userMapper.toDTO(updatedUser);
    }

    /**
     * Delete a user.
     * Note: This implements hard delete. For audit trail, implement soft delete
     * with an "isDeleted" flag instead.
     *
     * @param userId the user ID to delete
     * @throws ResourceNotFoundException if user not found
     */
    public void deleteUser(Long userId) {
        log.info("Deleting user with ID: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                "User not found with ID: " + userId,
                ErrorCodes.USER_NOT_FOUND
            );
        }

        userRepository.deleteById(userId);
        log.info("User deleted successfully: {}", userId);
    }

    /**
     * Get paginated list of active users.
     *
     * Why pagination?
     * - Prevents loading entire dataset into memory
     * - Improves API response time
     * - Better UX (handles large data)
     * - Database query is efficient (LIMIT/OFFSET)
     *
     * @param pageable pagination parameters (page, size, sort)
     * @return paginated active users
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserDTO> getActiveUsers(Pageable pageable) {
        log.debug("Fetching active users - Page: {}, Size: {}",
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<User> users = userRepository.findActiveUsers(true, pageable);
        return PagedResponse.from(users.map(userMapper::toDTO));
    }

    /**
     * Deactivate a user (softer alternative to delete).
     * Useful for maintaining audit trail while preventing login.
     *
     * @param userId the user ID
     * @throws ResourceNotFoundException if user not found
     */
    public UserDTO deactivateUser(Long userId) {
        log.info("Deactivating user with ID: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId,
                ErrorCodes.USER_NOT_FOUND
            ));

        user.setIsActive(false);
        User updatedUser = userRepository.save(user);

        log.info("User deactivated: {}", userId);
        return userMapper.toDTO(updatedUser);
    }
}

