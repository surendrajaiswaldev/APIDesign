package com.apidesign.config;

import lombok.extern.slf4j.Slf4j;

/**
 * Lombok Configuration Guide and Best Practices.
 *
 * This file documents Lombok usage patterns for the project.
 * Lombok reduces boilerplate by auto-generating getters, setters, equals, hashCode, toString.
 *
 * ================================================================================
 * LOMBOK ANNOTATIONS USED IN THIS PROJECT:
 * ================================================================================
 *
 * 1. @Getter
 *    Use for: Generating getters for all fields
 *    Fields with @Getter: All instance variables
 *    Exception: Sensitive fields (passwords) - create manually if needed
 *    AccessLevel: Default is public
 *
 *    @Getter
 *    public class User {
 *        private Long id;    // Generates getId()
 *        private String name; // Generates getName()
 *    }
 *
 * ================================================================================
 * 2. @Setter
 *    Use for: Generating setters for all fields
 *    Limitation: Don't use on immutable objects
 *    AccessLevel: Can restrict (e.g., @Setter(AccessLevel.PROTECTED))
 *    Security: Consider marking sensitive fields with @Setter(AccessLevel.PRIVATE)
 *
 *    @Setter
 *    public class User { ... }
 *
 * ================================================================================
 * 3. @NoArgsConstructor
 *    Use for: Generating no-argument constructor
 *    Why needed: JPA requires no-arg constructor for entity instantiation
 *    Access level: Default is package-private (fine for JPA)
 *    Caution: Don't use on immutable objects
 *
 *    @NoArgsConstructor  // Required for JPA entities
 *    public class User extends BaseEntity { ... }
 *
 * ================================================================================
 * 4. @AllArgsConstructor
 *    Use for: Generating constructor with all fields as parameters
 *    Order: Arguments follow field declaration order
 *    Usefulness: Useful for builders and testing
 *    Problem: Creates constructors with many parameters (Builder pattern better)
 *
 *    @AllArgsConstructor
 *    public class User extends BaseEntity { ... }
 *    // Generates: public User(Long id, String name, String email, ...)
 *
 * ================================================================================
 * 5. @RequiredArgsConstructor
 *    Use for: Generating constructor only for final/non-null fields
 *    Best for: Dependency injection in services and controllers
 *    Benefits: Only required dependencies in constructor, very clean
 *    Usage: Spring automatically uses this for constructor injection
 *
 *    @Service
 *    @RequiredArgsConstructor  // Perfect for DI
 *    public class UserService {
 *        private final UserRepository userRepository;  // Required in constructor
 *        private final UserMapper userMapper;         // Required in constructor
 *
 *        // Generates: public UserService(UserRepository repo, UserMapper mapper) { ... }
 *    }
 *
 * ================================================================================
 * 6. @Builder
 *    Use for: Fluent builder pattern for object construction
 *    Benefits: Readable, flexible object creation without many constructors
 *    Null handling: Builders allow null values (unlike constructors)
 *    Chaining: Methods return 'this' for fluent API
 *
 *    @Builder
 *    public class User extends BaseEntity { ... }
 *
 *    // Usage:
 *    User user = User.builder()
 *        .firstName("John")
 *        .lastName("Doe")
 *        .email("john@example.com")
 *        .isActive(true)
 *        .build();
 *
 * ================================================================================
 * 7. @Data
 *    WARNING: AVOID ON ENTITIES!
 *    What it does: Combines @Getter, @Setter, @ToString, @EqualsAndHashCode
 *    Problem with entities:
 *    - @EqualsAndHashCode on lazy-loaded collections causes issues
 *    - @ToString can trigger LazyInitializationException
 *    - Changes to entity can break lazy loading
 *    Safe to use: DTOs, request objects, simple POJOs
 *
 *    GOOD:  @Data public class UserDTO { ... }
 *    BAD:   @Data public class User extends BaseEntity { ... }
 *
 * ================================================================================
 * 8. @Slf4j
 *    Use for: Adding SLF4J logger field automatically
 *    Equivalent: private static final Logger log = LoggerFactory.getLogger(ClassName.class);
 *    Benefits: Less boilerplate, consistent logging across project
 *
 *    @Slf4j
 *    public class UserService {
 *        public void createUser(CreateUserRequest request) {
 *            log.info("Creating user with email: {}", request.getEmail());
 *        }
 *    }
 *
 * ================================================================================
 * USAGE PATTERNS IN THIS PROJECT:
 * ================================================================================
 *
 * ENTITIES (com.apidesign.entity):
 * @Getter
 * @Setter
 * @NoArgsConstructor      // Required for JPA
 * @AllArgsConstructor      // Optional but useful
 * @Builder                // Good for testing
 * public class User extends BaseEntity { ... }
 *
 * DTOs (com.apidesign.dto):
 * @Getter
 * @Setter
 * @NoArgsConstructor
 * @AllArgsConstructor
 * @Builder
 * public class UserDTO { ... }
 *
 * SERVICES (com.apidesign.service):
 * @Slf4j
 * @Service
 * @RequiredArgsConstructor  // Perfect for constructor injection
 * public class UserService {
 *     private final UserRepository userRepository;
 * }
 *
 * CONTROLLERS (com.apidesign.controller):
 * @RestController
 * public class UserController {
 *     private final UserService userService;
 *
 *     // Manually written constructor for clarity, but @RequiredArgsConstructor works too
 *     public UserController(UserService userService) {
 *         this.userService = userService;
 *     }
 * }
 *
 * ================================================================================
 * ANTI-PATTERNS - AVOID THESE:
 * ================================================================================
 *
 * 1. @Data on JPA entities:
 *    Problem: EqualsAndHashCode and ToString trigger lazy loading
 *    Solution: Use @Getter, @Setter instead
 *
 * 2. @Data on entities with @OneToMany:
 *    Problem: toString() includes all related entities, infinite loops possible
 *    Solution: Exclude lazy collections from generated methods
 *
 * 3. Using @AllArgsConstructor with many fields:
 *    Problem: Constructor has too many parameters
 *    Solution: Use @Builder instead
 *
 * 4. Mixing @AllArgsConstructor with inheritance:
 *    Problem: Constructor parameter ordering becomes confusing
 *    Solution: Use @Builder pattern
 *
 * 5. Forgetting @NoArgsConstructor on JPA entities:
 *    Problem: Runtime error during entity instantiation
 *    Solution: Always include for @Entity classes
 *
 * ================================================================================
 * CONFIGURATION FILE:
 * ================================================================================
 *
 * File: lombok.config (in project root)
 *
 * # Global Lombok configuration for this project
 * config.stopBubbling = true
 * lombok.accessors.chain = false
 * lombok.accessors.fluent = false
 * lombok.allArgsConstructor.suppressConstructorProperties = false
 * lombok.toString.includeFieldNames = true
 * lombok.equalsAndHashCode.callSuper = call
 * lombok.copyableAnnotations = org.springframework.beans.factory.annotation.Value
 *
 * Key settings:
 * - callSuper = call: For inheritance, calls super.equals/hashCode
 * - stopBubbling = true: Don't search parent directories for config
 *
 * ================================================================================
 * MAVEN CONFIGURATION (pom.xml):
 * ================================================================================
 *
 * Versions used:
 * - Lombok: 1.18.30 (latest stable)
 * - Java: 21 (supported by current Lombok)
 *
 * Annotation processor path in compiler plugin:
 * <path>
 *     <groupId>org.projectlombok</groupId>
 *     <artifactId>lombok</artifactId>
 *     <version>${lombok.version}</version>
 * </path>
 *
 * This must be in <annotationProcessorPaths> for compiler plugin.
 *
 * ================================================================================
 * PERFORMANCE CONSIDERATIONS:
 * ================================================================================
 *
 * 1. Compilation Time: Lombok adds minor overhead during compilation
 *    - IntelliJ indexing: Takes a bit longer
 *    - Build time: Negligible impact
 *    - Solution: Minimal - Lombok is optimized
 *
 * 2. Runtime Performance: Zero overhead
 *    - Generated code is same as hand-written
 *    - No reflection or annotation processing at runtime
 *    - Getter/setter call cost: Identical to manual implementation
 *
 * 3. Generated Method Count: Increases class file size slightly
 *    - Acceptable tradeoff for less boilerplate code
 *    - Modern JVMs handle efficiently
 *    - Spring Boot applications handle this well
 *
 * ================================================================================
 * IDE SUPPORT:
 * ================================================================================
 *
 * IntelliJ IDEA: Supports natively (Lombok plugin may be needed in older versions)
 * Eclipse: Requires Lombok plugin installation
 * VS Code: Language Server support available
 * Build: Works with Maven and Gradle
 *
 * ================================================================================
 * TESTING WITH LOMBOK:
 * ================================================================================
 *
 * Building test objects:
 *
 * User user = User.builder()
 *     .id(1L)
 *     .firstName("John")
 *     .email("john@example.com")
 *     .isActive(true)
 *     .build();
 *
 * Very convenient for test data setup!
 *
 * ================================================================================
 */
@Slf4j
public final class LombokConfigurationGuide {
    // This is just a configuration guide file - no code needed
    // See class javadoc for comprehensive Lombok documentation
}
