package com.apidesign.controller;

import com.apidesign.assembler.UserModelAssembler;
import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.entity.User;
import com.apidesign.interceptor.DeprecatedEndpoint;
import com.apidesign.response.ApiResponse;
import com.apidesign.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiEndpoints.USER_BASE_PATH)
@Validated
@Tag(name = "Users", description = "Manage application users (customers and admins).")
public class UserController {

    private final UserService userService;
    private final UserModelAssembler userAssembler;
    private final PagedResourcesAssembler<User> pagedAssembler;

    public UserController(
        UserService userService,
        UserModelAssembler userAssembler,
        PagedResourcesAssembler<User> pagedAssembler) {
        this.userService = userService;
        this.userAssembler = userAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @Operation(
        summary = "Get user by ID",
        description = "Fetches a single user. Returns a HAL representation with self, users, and orders links.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
        })
    @GetMapping(value = ApiEndpoints.USER_BY_ID, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<UserDTO>> getUserById(
        @Parameter(name = "userId", description = "Unique user identifier", example = "1")
        @PathVariable @Positive(message = "User ID must be positive") Long userId) {
        User user = userService.loadUser(userId);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Operation(
        summary = "[DEPRECATED] Get user by email",
        description = "Use GET /users/{id} instead. Response carries Deprecation, Sunset, and Link headers.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @DeprecatedEndpoint(
        sunset = "2026-12-31",
        replacedBy = "/users/{id}",
        link = "https://docs.example.com/deprecations/user-by-email")
    @GetMapping(value = ApiEndpoints.USER_BY_EMAIL, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<UserDTO>> getUserByEmail(
        @Parameter(description = "Email address (case-insensitive)", example = "alice@example.com")
        @PathVariable String email) {
        User user = userService.loadUserByEmail(email);
        return ResponseEntity.ok(userAssembler.toModel(user));
    }

    @Operation(
        summary = "List active users",
        description = "Paginated active users. Returns a HAL PagedModel with first/prev/next/last links.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page returned (possibly empty)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
        })
    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<PagedModel<EntityModel<UserDTO>>> getAllUsers(
        @Parameter(name = "page", description = "Zero-based page index", example = "0")
        @PageableDefault(size = 20) Pageable pageable) {
        Page<User> page = userService.findActiveUsers(pageable);
        return ResponseEntity.ok(pagedAssembler.toModel(page, userAssembler));
    }

    @Operation(
        summary = "Update an existing user",
        description = "Partial-update on the user record. Email uniqueness is enforced.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
        })
    @PutMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId,
        @Valid @RequestBody UpdateUserRequest request) {
        UserDTO updatedUser = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "User updated successfully"));
    }

    @Operation(
        summary = "Delete a user (ADMIN only)",
        description = "Hard-deletes the user. Consider deactivate before delete.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @DeleteMapping(ApiEndpoints.USER_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Deactivate user",
        description = "Soft-disables the user without deleting the row.")
    @ApiResponses(
        value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
        })
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserDTO>> deactivateUser(
        @PathVariable("id") @Positive(message = "User ID must be positive") Long userId) {
        UserDTO deactivated = userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(deactivated, "User deactivated successfully"));
    }
}
