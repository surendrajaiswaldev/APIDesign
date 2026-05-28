package com.apidesign.controller;

import com.apidesign.constants.ApiEndpoints;
import com.apidesign.dto.UpdateUserRequest;
import com.apidesign.dto.UserDTO;
import com.apidesign.response.ApiResponse;
import com.apidesign.response.PagedResponse;
import com.apidesign.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(
        @PathVariable @Positive(message = "User ID must be positive") Long userId) {
        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
    }

    @GetMapping(ApiEndpoints.USER_BY_EMAIL)
    public ResponseEntity<ApiResponse<UserDTO>> getUserByEmail(@PathVariable String email) {
        UserDTO user = userService.getUserByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserDTO>>> getAllUsers(
        @PageableDefault(size = 20) Pageable pageable) {
        PagedResponse<UserDTO> pagedUsers = userService.getActiveUsers(pageable);
        return ResponseEntity.ok(
            ApiResponse.success(pagedUsers,
                String.format("Retrieved %d users", pagedUsers.content().size())));
    }

    @PutMapping(ApiEndpoints.USER_BY_ID)
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId,
        @Valid @RequestBody UpdateUserRequest request) {
        UserDTO updatedUser = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "User updated successfully"));
    }

    @DeleteMapping(ApiEndpoints.USER_BY_ID)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
        @PathVariable @Positive(message = "User ID must be positive") Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserDTO>> deactivateUser(
        @PathVariable("id") @Positive(message = "User ID must be positive") Long userId) {
        UserDTO deactivated = userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(deactivated, "User deactivated successfully"));
    }
}
