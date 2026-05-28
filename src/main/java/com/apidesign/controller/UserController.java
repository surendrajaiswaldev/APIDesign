package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.CreateUserRequest;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * User REST Controller for user management endpoints.
 *
 * Controller Responsibilities (Keep THIN):
 * 1. Map HTTP requests to service method calls
 * 2. Handle HTTP status codes and response codes
 * 3. Transform service responses to API responses
 * 4. Add HATEOAS links (optional, adds navigation)
 * 5. Validate request parameters
 *
 * DO NOT put business logic here - that's service layer's job!
 *
 * REST API Best Practices:
 * - Use proper HTTP methods (GET, POST, PUT, DELETE)
 * - Use appropriate HTTP status codes
 * - Resource-oriented URLs (nouns, not verbs)
 * - Use path parameters for identifiers
 * - Use query parameters for filters
 * - Use request body for complex data
 * - Version API (e.g., /api/v1)
 *
 * HATEOAS (Hypermedia As The Engine Of Application State):
 * - Adds links to related resources in response
 * - Clients discover available actions from response
 * - Example: When retrieving user, include link to their orders
 * - Not always needed (can be over-engineering for simple APIs)
 */
@Slf4j
@RestController
@RequestMapping(ApiEndpoints.USER_BASE_PATH)
@Validated
public class UserController {
    private final UserService userService;

    // Constructor injection
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Create a new user.
     *
     * HTTP: POST /api/v1/users
     * Status: 201 (Created)
     *
     * @param request the create user request with validation
     * @return created user with HATEOAS links
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("Creating user - Email: {}", request.getEmail());

        UserDTO createdUser = userService.createUser(request);

        ApiResponse<UserDTO> response = ApiResponse.success(
            createdUser,
            "User created successfully"
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get user by ID.
     *
     * HTTP: GET /api/v1/users/{id}
     * Status: 200 (OK)
     *
     * @param userId the user ID (validated to be positive)
     * @return user details with links
     */
    @GetMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<ApiResponse<EntityModel<UserDTO>>> getUserById(
            @PathVariable
            @Positive(message = "User ID must be positive")
            Long userId) {

        log.info("Fetching user: {}", userId);

        UserDTO user = userService.getUserById(userId);

        // Add HATEOAS links
        EntityModel<UserDTO> userModel = EntityModel.of(user,
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(UserController.class).getUserById(userId))
                .withSelfRel(),
            WebMvcLinkBuilder.linkTo(UserController.class)
                .withRel("all-users")
        );

        ApiResponse<EntityModel<UserDTO>> response = ApiResponse.success(
            userModel,
            "User retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get user by email.
     *
     * HTTP: GET /api/v1/users/email/{email}
     * Status: 200 (OK)
     *
     * @param email the user's email
     * @return user details
     */
    @GetMapping(ApiEndpoints.USER_BY_EMAIL)
    public ResponseEntity<ApiResponse<UserDTO>> getUserByEmail(
            @PathVariable String email) {

        log.info("Fetching user by email: {}", email);

        UserDTO user = userService.getUserByEmail(email);

        ApiResponse<UserDTO> response = ApiResponse.success(
            user,
            "User retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all active users with pagination.
     *
     * HTTP: GET /api/v1/users?page=0&size=10&sort=firstName
     * Status: 200 (OK)
     *
     * Query Parameters:
     * - page: 0-based page number (default: 0)
     * - size: page size (default: 20)
     * - sort: sort criteria, e.g., "firstName,asc" or "createdAt,desc"
     *
     * @param pageable pagination parameters (injected by Spring)
     * @return paginated active users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<CollectionModel<UserDTO>>> getAllUsers(
            @PageableDefault(size = 20, page = 0) Pageable pageable) {

        log.info("Fetching all active users - Page: {}, Size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        PagedResponse<UserDTO> pagedUsers = userService.getActiveUsers(pageable);

        // Build HATEOAS collection with pagination links
        CollectionModel<UserDTO> userCollection = CollectionModel.of(
            pagedUsers.getContent(),
            WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(UserController.class).getAllUsers(pageable))
                .withSelfRel()
        );

        ApiResponse<CollectionModel<UserDTO>> response = ApiResponse.success(
            userCollection,
            String.format("Retrieved %d users", pagedUsers.getContent().size())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Update an existing user (partial update).
     *
     * HTTP: PUT /api/v1/users/{id}
     * Status: 200 (OK)
     *
     * Note: PUT for full replacement, PATCH for partial updates
     * Here using PUT with optional fields in request for simplicity.
     *
     * @param userId the user ID to update
     * @param request the update request with optional fields
     * @return updated user
     */
    @PutMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable
            @Positive(message = "User ID must be positive")
            Long userId,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("Updating user: {}", userId);

        UserDTO updatedUser = userService.updateUser(userId, request);

        ApiResponse<UserDTO> response = ApiResponse.success(
            updatedUser,
            "User updated successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a user.
     *
     * HTTP: DELETE /api/v1/users/{id}
     * Status: 204 (No Content) - per REST best practices
     *
     * @param userId the user ID to delete
     * @return empty response
     */
    @DeleteMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<Void> deleteUser(
            @PathVariable
            @Positive(message = "User ID must be positive")
            Long userId) {

        log.info("Deleting user: {}", userId);

        userService.deleteUser(userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate a user (soft delete alternative).
     *
     * HTTP: POST /api/v1/users/{id}/deactivate
     * Status: 200 (OK)
     *
     * @param userId the user ID to deactivate
     * @return deactivated user
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserDTO>> deactivateUser(
            @PathVariable("id")
            @Positive(message = "User ID must be positive")
            Long userId) {

        log.info("Deactivating user: {}", userId);

        UserDTO deactivatedUser = userService.deactivateUser(userId);

        ApiResponse<UserDTO> response = ApiResponse.success(
            deactivatedUser,
            "User deactivated successfully"
        );

        return ResponseEntity.ok(response);
    }
}

