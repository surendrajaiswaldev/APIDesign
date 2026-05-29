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
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessLogicException(
                "User with email " + request.email() + " already exists",
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS);
        }
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (user.getIsActive() == null) {
            user.setIsActive(true);
        }
        if (user.getUserType() == null) {
            user.setUserType("CUSTOMER");
        }
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            Set<String> roles = new HashSet<>();
            roles.add("USER");
            user.setRoles(roles);
        }
        return userMapper.toDTO(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long userId) {
        return userMapper.toDTO(loadUser(userId));
    }

    /** Entity-returning variant for HAL assemblers — DTO mapping happens in the assembler. */
    @Transactional(readOnly = true)
    public User loadUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        return userMapper.toDTO(loadUserByEmail(email));
    }

    /** Entity-returning variant for HAL assemblers. */
    @Transactional(readOnly = true)
    public User loadUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with email: " + email, ErrorCodes.USER_NOT_FOUND));
    }

    public UserDTO updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND));

        if (request.email() != null
            && !request.email().equals(user.getEmail())
            && userRepository.existsByEmail(request.email())) {
            throw new BusinessLogicException(
                "Email " + request.email() + " is already in use",
                ErrorCodes.USER_EMAIL_ALREADY_EXISTS);
        }
        userMapper.updateEntityFromRequest(request, user);
        return userMapper.toDTO(userRepository.save(user));
    }

    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND);
        }
        userRepository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserDTO> getActiveUsers(Pageable pageable) {
        Page<User> users = userRepository.findActiveUsers(true, pageable);
        return PagedResponse.from(users.map(userMapper::toDTO));
    }

    /** Entity-returning variant for HAL assemblers — pagedAssembler converts to PagedModel. */
    @Transactional(readOnly = true)
    public Page<User> findActiveUsers(Pageable pageable) {
        return userRepository.findActiveUsers(true, pageable);
    }

    public UserDTO deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "User not found with ID: " + userId, ErrorCodes.USER_NOT_FOUND));
        user.setIsActive(false);
        return userMapper.toDTO(userRepository.save(user));
    }
}
