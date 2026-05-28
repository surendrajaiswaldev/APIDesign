# E-Commerce Order Management System

Production-grade Spring Boot backend application for managing e-commerce orders, users, and products with enterprise-level coding standards, clean architecture, and production-ready practices.

## 📋 Table of Contents
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Database Setup](#database-setup)
- [API Endpoints](#api-endpoints)
- [Key Features](#key-features)
- [Architectural Decisions](#architectural-decisions)
- [Coding Standards](#coding-standards)

## 🏗️ Architecture

This project follows **Clean Layered Architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│         REST Controllers (HTTP Layer)            │  Request Handling
├─────────────────────────────────────────────────┤
│      Global Exception Handler & Advice           │  Cross-cutting Concerns
├─────────────────────────────────────────────────┤
│ Service Layer (Business Logic & Transactions)    │  Business Rules
├─────────────────────────────────────────────────┤
│   Repository Layer (Data Access & Queries)       │  Database Operations
├─────────────────────────────────────────────────┤
│        Entity Layer (Domain Models)              │  Data Representation
├─────────────────────────────────────────────────┤
│    Configuration & Cross-Cutting Concerns        │  AOP, Interceptors
└─────────────────────────────────────────────────┘
```

### Layer Responsibilities

**Controller Layer** (`controller/`)
- Map HTTP requests to service methods
- Validate path/query parameters
- Return formatted HTTP responses
- Add HATEOAS links (optional REST enrichment)
- **MUST remain thin** - no business logic

**Service Layer** (`service/`)
- Implement business logic
- Validate business rules
- Handle transactions (@Transactional)
- Coordinate between repositories
- Throw appropriate exceptions

**Repository Layer** (`repository/`)
- Database query execution only
- Spring Data JPA derived queries
- JPQL @Query methods
- Native SQL for complex queries
- No business logic

**Entity Layer** (`entity/`)
- JPA domain models
- ORM annotations
- Relationships (One-to-Many, Many-to-One)
- Audit fields (createdAt, updatedAt)
- Helper methods only

**Cross-Cutting Concerns**
- **Interceptors**: HTTP-level request/response handling (correlation IDs, request timing)
- **AOP Aspects**: Method-level concerns (service logging, execution time, exception handling)
- **Exception Handling**: Global @RestControllerAdvice for standardized error responses
- **Response Formatting**: Standardized API response structures

## 🚀 Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.0 |
| ORM | Spring Data JPA/Hibernate | Latest |
| Database | Oracle (FREEPDB1) | 21c+ |
| Connection Pool | HikariCP | 5.x |
| Mapping | MapStruct | 1.5.5 |
| Boilerplate Reduction | Lombok | 1.18.30 |
| REST Enhancement | Spring HATEOAS | 2.1.1 |
| AOP | Spring AOP | 6.0+ |
| Build Tool | Maven | 3.8+ |
| Testing | JUnit 5 + Mockito | Latest |

## 📁 Project Structure

```
src/main/java/com/apidesign/
├── ApiDesignApplication.java          # Main Spring Boot application
├── config/
│   ├── DatabaseConfig.java            # JPA/Hibernate configuration
│   ├── WebConfig.java                 # Interceptor registration & MVC config
│   └── ValidationConfig.java          # Bean validation setup
├── controller/                         # HTTP request handlers
│   ├── UserController.java
│   ├── ProductController.java
│   └── OrderController.java
├── service/                            # Business logic & transactions
│   ├── UserService.java
│   ├── ProductService.java
│   └── OrderService.java
├── repository/                         # Data access layer
│   ├── UserRepository.java
│   ├── ProductRepository.java
│   ├── OrderRepository.java
│   └── OrderItemRepository.java
├── entity/                             # JPA domain models
│   ├── BaseEntity.java                 # Common audit fields
│   ├── User.java
│   ├── Product.java
│   ├── Order.java
│   └── OrderItem.java
├── dto/                                # Data transfer objects
│   ├── UserDTO.java
│   ├── CreateUserRequest.java
│   ├── UpdateUserRequest.java
│   ├── ProductDTO.java
│   ├── CreateProductRequest.java
│   ├── UpdateProductRequest.java
│   ├── OrderDTO.java
│   ├── OrderItemDTO.java
│   └── CreateOrderRequest.java
├── mapper/                             # MapStruct DTO mappers
│   ├── UserMapper.java
│   ├── ProductMapper.java
│   ├── OrderMapper.java
│   └── OrderItemMapper.java
├── exception/                          # Custom exceptions
│   ├── BaseException.java
│   ├── ResourceNotFoundException.java
│   ├── BusinessLogicException.java
│   ├── ValidationException.java
│   └── DatabaseException.java
├── aspect/                             # AOP aspects
│   └── LoggingAspect.java             # Service layer logging & timing
├── interceptor/                        # HTTP interceptors
│   └── RequestInterceptor.java        # Correlation ID & request tracking
├── advice/                             # Exception handlers
│   └── GlobalExceptionHandler.java    # REST @ExceptionHandler
├── response/                           # API response models
│   ├── ApiResponse.java               # Generic response wrapper
│   ├── PagedResponse.java             # Pagination response
│   └── ValidationErrorResponse.java   # Validation error details
├── constants/                          # Application constants
│   ├── OrderStatus.java
│   ├── ApiEndpoints.java
│   └── ErrorCodes.java
└── util/                               # Utility classes
    ├── CorrelationIdUtil.java         # MDC correlation ID management
    └── (other utilities)

src/main/resources/
├── application.yml                     # Main configuration
├── application-local.yml               # Local development profile
├── application-dev.yml                 # Development profile
└── application-prod.yml                # Production profile
```

## 🔧 Getting Started

### Prerequisites
- Java 21 JDK installed
- Maven 3.8+
- Oracle FREEPDB1 running locally
- Git

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd APIDesign
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Set active profile to local**
   ```bash
   # The application will use application-local.yml by default
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   # OR
   java -jar target/order-management-system-1.0.0.jar --spring.profiles.active=local
   ```

5. **API Documentation**
   - Swagger/Springdoc OpenAPI (if integrated): `http://localhost:8080/api/v1/swagger-ui.html`
   - Application runs on: `http://localhost:8080/api/v1`

## 🗄️ Database Setup

### Oracle FREEPDB1 Local Configuration

**Connection Details**
```properties
jdbc.url=jdbc:oracle:thin:@localhost:1521/FREEPDB1
jdbc.username=system
jdbc.password=password
```

**HikariCP Connection Pool Configuration** (in application.yml)
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5           # Keep 5 connections ready
      maximum-pool-size: 20     # Max 20 connections
      connection-timeout: 30000 # Wait 30s for connection
      idle-timeout: 600000      # Close after 10min idle
      max-lifetime: 1800000     # Max 30min connection lifetime
```

### Why HikariCP?
- **Fastest connection pool** available for Java
- Lower latency compared to other pools
- Efficient resource usage
- Battle-tested in production

### Creating Database & Schema

```sql
-- Connect as system user
sqlplus system/password@localhost:1521/FREEPDB1

-- Create sequence for ID generation (used by @SequenceGenerator)
CREATE SEQUENCE ID_SEQ START WITH 1 INCREMENT BY 1 NOCYCLE;

-- Hibernate will auto-create/update tables based on @Entity annotations
-- when ddl-auto is set to 'update' in application-local.yml
```

### Table Audit Fields

All entities automatically include:
- `CREATED_AT`: Entity creation timestamp
- `UPDATED_AT`: Last modification timestamp
- `CREATED_BY`: User creating entity
- `UPDATED_BY`: User last modifying entity

## 📡 API Endpoints

### Base URL
```
http://localhost:8080/api/v1
```

### User Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/users` | Create new user |
| GET | `/users` | List all active users (paginated) |
| GET | `/users/{id}` | Get user by ID |
| GET | `/users/email/{email}` | Get user by email |
| PUT | `/users/{id}` | Update user |
| DELETE | `/users/{id}` | Delete user |
| POST | `/users/{id}/deactivate` | Deactivate user (soft delete) |

### Product Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/products` | Create new product |
| GET | `/products` | List all products (paginated) |
| GET | `/products/{id}` | Get product by ID |
| GET | `/products/category/{category}` | Get products by category |
| GET | `/products/search` | Search products by price/category |
| GET | `/products/lowstock` | Get low-stock products |
| PUT | `/products/{id}` | Update product |
| DELETE | `/products/{id}` | Delete product |

### Order Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/orders` | Create new order |
| GET | `/orders/{id}` | Get order by ID |
| GET | `/orders/number/{orderNumber}` | Get order by number |
| GET | `/orders/user/{userId}` | List orders for user |
| GET | `/orders/by-status/{status}` | List orders by status |
| PUT | `/orders/{id}/status` | Update order status |
| POST | `/orders/{id}/cancel` | Cancel order |

### Query Parameters

**Pagination & Sorting**
```
?page=0&size=20&sort=firstName,asc
```

- `page`: 0-based page number
- `size`: Items per page
- `sort`: Column name and direction (asc/desc)

### Response Format

All successful responses follow this structure:
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 200,
  "message": "Success message",
  "data": {
    "id": 1,
    "name": "John Doe",
    ...
  }
}
```

### Error Response Format

```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 404,
  "message": "User not found with ID: 999",
  "errorCode": "USER-001"
}
```

### Validation Error Response

```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 400,
  "message": "Validation failed",
  "path": "/users",
  "errors": [
    {
      "field": "email",
      "message": "Email should be valid",
      "rejectedValue": "invalid-email"
    }
  ]
}
```

## 🎯 Key Features

### 1. Complex Order Management
- Multi-item order creation with stock validation
- Automatic inventory reduction
- Order cancellation with stock return
- Status transitions with state machine validation
  - PENDING → CONFIRMED, CANCELLED
  - CONFIRMED → SHIPPED, CANCELLED
  - SHIPPED → DELIVERED (terminal)

### 2. Query Optimization (N+1 Prevention)
- **JOIN FETCH** in JPQL: `SELECT o FROM Order o JOIN FETCH o.user`
- **@EntityGraph**: Declarative relationship loading
- Examples in `OrderRepository.java`

### 3. Database Query Approaches

**Derived Queries** (Simple & Readable)
```java
Optional<User> findByEmail(String email);
Page<Product> findByCategory(String category, Pageable pageable);
```

**JPQL Queries** (Portable & Type-Safe)
```java
@Query("SELECT u FROM User u WHERE u.isActive = true")
Page<User> findActiveUsers(Pageable pageable);
```

**Native SQL** (Complex Aggregations)
```java
@Query(value = "SELECT * FROM ORDERS WHERE TOTAL_AMOUNT > :amount", 
       nativeQuery = true)
Page<Order> findHighValueOrders(Double amount, Pageable pageable);
```

### 4. Validation

**Standard Bean Validation**
- `@NotNull`, `@NotBlank`, `@Email`
- `@Positive`, `@Size`, `@Pattern`

**Custom Validators** (Examples in code)
- Email uniqueness
- Password strength
- Phone number format

### 5. Logging with MDC Correlation IDs

```
2026-05-09 10:30:45 [UUID-12345] INFO UserService - Creating user with email: john@example.com
2026-05-09 10:30:46 [UUID-12345] DEBUG OrderService - Order created: ORD-20260509-XYZ12
```

- Request correlation ID generated by `RequestInterceptor`
- Set in MDC for automatic inclusion in all logs
- Returned in response header for client tracking

### 6. Transaction Management

All data modifications are transactional:
```java
@Service
@Transactional  // All methods transactional
public class UserService {
    @Transactional(readOnly = true)  // Override for queries
    public UserDTO getUserById(Long id) { ... }
}
```

### 7. HATEOAS Support

Selected endpoints include navigation links:
```json
{
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "_links": {
    "self": {"href": "/users/1"},
    "all-users": {"href": "/users"},
    "user-orders": {"href": "/orders/user/1"}
  }
}
```

### 8. MapStruct DTO Mapping

Type-safe, compile-time code generation:
```java
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = IGNORE)
public interface UserMapper {
    UserDTO toDTO(User user);
    User toEntity(CreateUserRequest request);
    void updateEntityFromRequest(UpdateUserRequest request, @MappingTarget User user);
}
```

### 9. Spring AOP Cross-Cutting Concerns

**Service Layer Logging**
- Method entry/exit logging
- Automatic execution time tracking
- Exception logging with stack traces

```
LoggingAspect:
  - @Before: Log service method invocation
  - @Around: Track execution time
  - @AfterThrowing: Log exceptions
```

## 🏛️ Architectural Decisions

### SOLID Principles Implementation

**Single Responsibility Principle (SRP)**
- Each class has one reason to change
- Controllers: HTTP handling
- Services: Business logic
- Repositories: Data access

**Open/Closed Principle (OCP)**
- Base exception class for extension
- Service layer for business logic extension
- Mapper interfaces for customization

**Liskov Substitution Principle (LSP)**
- Exception hierarchy allows polymorphic handling
- All exceptions extend `BaseException`

**Interface Segregation Principle (ISP)**
- Minimal repository interfaces
- Only required methods exposed
- Client doesn't depend on unneeded methods

**Dependency Inversion Principle (DIP)**
- Constructor injection (depends on abstractions)
- Spring manages dependencies
- Services depend on Repository interfaces, not implementations

### Entity Design

**Why separate User, Product, Order, OrderItem?**
- **User**: Customer account management
- **Product**: Catalog management
- **Order**: Order lifecycle
- **OrderItem**: Line items with historical pricing

**OrderItem Importance**
- Stores product price snapshot (price at time of order)
- Prevents calculation errors if product price changes
- Maintains accurate historical data

### DTO vs Entity

**Why DTOs?**
- API contracts independent from entities
- Hide internal structure (security)
- Allow independent evolution of entities and API
- Prevent lazy loading issues within REST layer

### Lazy vs Eager Loading

**Default: LAZY**
- Reduces initial query time
- Prevents loading unnecessary data
- Risk: N+1 problem if not handled

**Solution**
- Use JOIN FETCH in repository queries
- Use @EntityGraph for declarative loading

### Transaction Boundaries

**Why at Service Layer?**
- Business logic consistency
- Multiple repository calls as atomic unit
- Automatic rollback on exception

**Example**
```java
// If any step fails, entire order creation rolled back
@Transactional
public OrderDTO createOrder(CreateOrderRequest request) {
    // 1. Validate user
    // 2. Validate products
    // 3. Reduce stock
    // 4. Create order
    // Either all succeed or all rollback
}
```

### Exception Handling Strategy

**Custom Exception Hierarchy**
```
BaseException
├── ResourceNotFoundException (404)
├── BusinessLogicException (400)
├── ValidationException (400)
└── DatabaseException (500)
```

**Global Exception Handler**
- Standardized error responses
- Proper HTTP status codes
- Security: Don't leak internal details

### Interceptor vs AOP

**Interceptor (RequestInterceptor)**
- HTTP-level concerns
- Request/response headers
- Correlation ID generation
- Execution timing

**AOP (LoggingAspect)**
- Method-level concerns
- Service layer monitoring
- Business logic cross-cutting
- Exception handling

## 📊 Coding Standards

### Naming Conventions

**Classes**
- Services: `*Service` (UserService)
- Controllers: `*Controller` (UserController)
- Repositories: `*Repository` (UserRepository)
- Mappers: `*Mapper` (UserMapper)
- Aspects: `*Aspect` (LoggingAspect)
- Exceptions: `*Exception` (ResourceNotFoundException)

**Methods**
- Getters: `get*()` or property name
- Setters: `set*()`
- Checkers: `is*()` or `has*()`
- Mappers: `to*()` or `from*()`

**Constants**
- All uppercase with underscores: `MAX_POOL_SIZE`, `ORDER_PENDING`

### Comments

**Public Classes & Methods**
- JavaDoc comments with parameter/return descriptions
- Explain "why" not "what"

**Complex Business Logic**
- Inline comments explain decision
- Reference business rules

**Avoid**
- Stating obvious (`// increment i`)
- Outdated comments
- Commented-out code

### Code Organization

**Method Order in Class**
1. Constants
2. Fields
3. Constructor(s)
4. Public methods
5. Protected methods
6. Private methods
7. Inner classes

**Line Length**
- Max 120 characters
- Long method calls: break into multiple lines

## 🧪 Testing Strategy

### Test File Locations
```
src/test/java/com/apidesign/
├── service/
│   ├── UserServiceTest.java
│   ├── ProductServiceTest.java
│   └── OrderServiceTest.java
├── repository/
│   └── OrderRepositoryTest.java
└── controller/
    ├── UserControllerTest.java
    └── OrderControllerTest.java
```

### Testing Approach

**Unit Tests** (Test business logic)
- Service layer tests with mocks
- Repository tests with @DataJpaTest

**Integration Tests** (Test full flow)
- @SpringBootTest with TestContainers
- Test multiple layers together

**Test Example**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void testCreateUserSuccess() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .phoneNumber("1234567890")
            .build();
            
        User expectedUser = User.builder()
            .id(1L)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .isActive(true)
            .build();
        
        when(userRepository.save(any(User.class))).thenReturn(expectedUser);
        
        // Act
        UserDTO result = userService.createUser(request);
        
        // Assert
        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }
}
```

## 📋 API Examples

### 1. Create User

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phoneNumber": "+1-234-567-8900",
    "address": "123 Main St",
    "city": "Springfield",
    "state": "IL",
    "zipcode": "62701"
  }'
```

**Response (201 Created):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 201,
  "message": "User created successfully",
  "data": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "phoneNumber": "+1-234-567-8900",
    "address": "123 Main St",
    "city": "Springfield",
    "state": "IL",
    "zipcode": "62701",
    "isActive": true
  }
}
```

### 2. Create Product with Custom Validators

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "PROD-2024-001",
    "name": "Laptop Computer",
    "description": "High-performance laptop",
    "price": 1299.99,
    "stockQuantity": 50,
    "minStockLevel": 10,
    "category": "Electronics",
    "supplier": "Tech Corp"
  }'
```

**Response (201 Created):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 201,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "sku": "PROD-2024-001",
    "name": "Laptop Computer",
    "description": "High-performance laptop",
    "price": 1299.99,
    "stockQuantity": 50,
    "minStockLevel": 10,
    "category": "Electronics",
    "supplier": "Tech Corp",
    "isAvailable": true
  }
}
```

### 3. Validation Error Example

**Request (Invalid SKU - lowercase):**
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "prod-2024-001",
    "name": "Test Product",
    "price": 99.99,
    "stockQuantity": 10
  }'
```

**Response (400 Bad Request):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/v1/products",
  "errors": [
    {
      "field": "sku",
      "message": "SKU must be alphanumeric with hyphens, 3-20 characters",
      "rejectedValue": "prod-2024-001"
    }
  ]
}
```

### 4. Create Order with Multiple Items

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "orderItems": [
      {
        "productId": 1,
        "quantity": 2,
        "notes": "Premium model"
      },
      {
        "productId": 5,
        "quantity": 1,
        "notes": "Extended warranty"
      }
    ],
    "shippingAddress": "456 Oak Ave, Chicago, IL 60601",
    "notes": "Priority shipping please"
  }'
```

**Response (201 Created):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 201,
  "message": "Order created successfully",
  "data": {
    "id": 1,
    "orderNumber": "ORD-20260509-ABC123",
    "userId": 1,
    "orderStatus": "PENDING",
    "totalAmount": 2699.97,
    "shippingAddress": "456 Oak Ave, Chicago, IL 60601",
    "notes": "Priority shipping please",
    "createdAt": "2026-05-09T10:30:00",
    "updatedAt": "2026-05-09T10:30:00",
    "orderItems": [
      {
        "id": 1,
        "productId": 1,
        "productName": "Laptop Computer",
        "quantity": 2,
        "unitPrice": 1299.99,
        "totalPrice": 2599.98
      },
      {
        "id": 2,
        "productId": 5,
        "productName": "Extended Warranty",
        "quantity": 1,
        "unitPrice": 99.99,
        "totalPrice": 99.99
      }
    ]
  }
}
```

### 5. List Products with Pagination

**Request:**
```bash
curl "http://localhost:8080/api/v1/products?page=0&size=10&sort=name,asc"
```

**Response (200 OK):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 200,
  "message": "Retrieved 10 products",
  "data": {
    "content": [
      {
        "id": 1,
        "sku": "PROD-2024-001",
        "name": "Extended Warranty",
        "price": 99.99,
        "stockQuantity": 500,
        "category": "Services"
      },
      {
        "id": 2,
        "sku": "PROD-2024-002",
        "name": "Laptop Computer",
        "price": 1299.99,
        "stockQuantity": 48,
        "category": "Electronics"
      }
    ],
    "currentPage": 0,
    "pageSize": 10,
    "totalElements": 25,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 6. HATEOAS Enabled - Get Single Product

**Request:**
```bash
curl "http://localhost:8080/api/v1/products/1"
```

**Response (200 OK with HATEOAS links):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 200,
  "message": "Product retrieved successfully",
  "data": {
    "id": 1,
    "sku": "PROD-2024-001",
    "name": "Laptop Computer",
    "price": 1299.99,
    "stockQuantity": 50,
    "_links": {
      "self": {
        "href": "http://localhost:8080/api/v1/products/1"
      },
      "all-products": {
        "href": "http://localhost:8080/api/v1/products"
      }
    }
  }
}
```

### 7. Update User

**Request:**
```bash
curl -X PUT http://localhost:8080/api/v1/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jonathan",
    "phoneNumber": "+1-234-567-8901"
  }'
```

**Response (200 OK):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 200,
  "message": "User updated successfully",
  "data": {
    "id": 1,
    "firstName": "Jonathan",
    "lastName": "Doe",
    "email": "john@example.com",
    "phoneNumber": "+1-234-567-8901",
    "isActive": true
  }
}
```

### 8. Error Response - Resource Not Found

**Request:**
```bash
curl "http://localhost:8080/api/v1/users/999"
```

**Response (404 Not Found):**
```json
{
  "timestamp": "2026-05-09T10:30:00",
  "status": 404,
  "message": "User not found with ID: 999",
  "errorCode": "USER-001"
}
```

### 9. Correlation ID Tracking in Response Headers

**Request:**
```bash
curl -v "http://localhost:8080/api/v1/products/1"
```

**Response Headers:**
```
HTTP/1.1 200 OK
X-Correlation-ID: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
```

This correlation ID can be used in logs to track the entire request flow across services.



## 🚢 Deployment

### Production Configuration (application-prod.yml)

**Database**
- Connection string from environment variables
- SSL connections enabled
- Larger connection pool

**Logging**
- WARN level default
- Log files to `/var/log/order-management-system/`
- File rotation

**Security**
- SSL/TLS enabled
- Sensitive credentials from environment

### Building for Production

```bash
# Build JAR
mvn clean package -DskipTests -P prod

# Run with production profile
java -jar target/order-management-system-1.0.0.jar \
  --spring.profiles.active=prod \
  -Dspring.datasource.url=jdbc:oracle:thin:@prod-db:1521/FREEPDB1 \
  -Dspring.datasource.username=${DB_USER} \
  -Dspring.datasource.password=${DB_PASSWORD}
```

## 📝 Future Improvements

1. **Authentication & Authorization**
   - JWT token support
   - Role-based access control (RBAC)
   - OAuth2 integration

2. **Caching Layer**
   - Redis for product catalog caching
   - Spring Cache abstraction

3. **API Documentation**
   - Springdoc OpenAPI / Swagger integration
   - API versioning strategies

4. **Async Processing**
   - Async order confirmation emails
   - Background job processing with Spring Task
   - Message queues (RabbitMQ/Kafka)

5. **Monitoring & Metrics**
   - Micrometer/Prometheus metrics
   - Spring Boot Actuator endpoints
   - Distributed tracing (Jaeger/Zipkin)

6. **Database Migrations**
   - Flyway/Liquibase for schema versioning
   - Version-controlled DDL changes

7. **Advanced Security**
   - Rate limiting
   - CORS configuration
   - Request sanitization

## 🤝 Contributing

Follow the coding standards and architectural patterns described above.

## 📄 License

Proprietary - All rights reserved

---

**Created**: May 2026  
**Java Version**: 21  
**Spring Boot Version**: 3.3.0

