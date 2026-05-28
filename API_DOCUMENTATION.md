# API Documentation - E-Commerce Order Management System

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
Currently, this API does not require authentication. Future versions will include JWT token support.

## Response Format

### Success Response (200, 201)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Success message",
  "data": {
    // ... resource data
  }
}
```

### Error Response (400, 404, 500)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 404,
  "message": "Error description",
  "errorCode": "ERROR-001"
}
```

### Validation Error Response (400)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/v1/users",
  "errors": [
    {
      "field": "email",
      "message": "Email should be valid",
      "rejectedValue": "invalid-email"
    }
  ]
}
```

## Pagination

Query parameters for paginated endpoints:
```
?page=0&size=20&sort=firstName,asc
```

Paginated response includes:
```json
{
  "content": [...],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 100,
  "totalPages": 5,
  "isFirst": true,
  "isLast": false,
  "hasNext": true,
  "hasPrevious": false
}
```

---

## User Management Endpoints

### 1. Create User
**POST** `/users`

**Request**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phoneNumber": "+1-555-0123",
  "address": "123 Main Street",
  "city": "New York",
  "state": "NY",
  "zipcode": "10001"
}
```

**Response** (201 Created)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 201,
  "message": "User created successfully",
  "data": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1-555-0123",
    "address": "123 Main Street",
    "city": "New York",
    "state": "NY",
    "zipcode": "10001",
    "isActive": true,
    "userType": "CUSTOMER",
    "createdAt": "2026-05-09T10:30:45",
    "updatedAt": "2026-05-09T10:30:45"
  }
}
```

**Error** (400 - Email already exists)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 400,
  "message": "User with email john.doe@example.com already exists",
  "errorCode": "USER-002"
}
```

---

### 2. Get User by ID
**GET** `/users/{id}`

**Example** `GET /users/1`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "User retrieved successfully",
  "data": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    ...
    "_links": {
      "self": {"href": "/users/1"},
      "all-users": {"href": "/users"}
    }
  }
}
```

---

### 3. Get User by Email
**GET** `/users/email/{email}`

**Example** `GET /users/email/john.doe@example.com`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "User retrieved successfully",
  "data": { ... }
}
```

---

### 4. List All Users (Paginated)
**GET** `/users?page=0&size=10&sort=firstName,asc`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Retrieved 10 users",
  "data": {
    "content": [
      {
        "id": 1,
        "firstName": "John",
        ...
      },
      {
        "id": 2,
        ...
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 25,
    "totalPages": 3,
    "isFirst": true,
    "isLast": false,
    "hasNext": true,
    "hasPrevious": false,
    "_links": {
      "self": {"href": "/users?page=0&size=10"}
    }
  }
}
```

---

### 5. Update User
**PUT** `/users/{id}`

**Request** (All fields optional)
```json
{
  "firstName": "Jonathan",
  "phoneNumber": "+1-555-9999",
  "city": "Los Angeles"
}
```

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "User updated successfully",
  "data": {
    "id": 1,
    "firstName": "Jonathan",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phoneNumber": "+1-555-9999",
    "city": "Los Angeles",
    ...
  }
}
```

---

### 6. Deactivate User
**POST** `/users/{id}/deactivate`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "User deactivated successfully",
  "data": {
    "id": 1,
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "isActive": false,
    ...
  }
}
```

---

### 7. Delete User
**DELETE** `/users/{id}`

**Response** (204 No Content)
```
(Empty response body)
```

---

## Product Management Endpoints

### 1. Create Product
**POST** `/products`

**Request**
```json
{
  "sku": "LAPTOP-001",
  "name": "Dell XPS 15 Laptop",
  "description": "High-performance laptop with 15-inch display",
  "price": 1299.99,
  "stockQuantity": 50,
  "minStockLevel": 10,
  "category": "Electronics",
  "supplier": "Dell Inc."
}
```

**Response** (201 Created)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 201,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "sku": "LAPTOP-001",
    "name": "Dell XPS 15 Laptop",
    "description": "High-performance laptop with 15-inch display",
    "price": 1299.99,
    "stockQuantity": 50,
    "minStockLevel": 10,
    "category": "Electronics",
    "isAvailable": true,
    "supplier": "Dell Inc.",
    "createdAt": "2026-05-09T10:30:45",
    "updatedAt": "2026-05-09T10:30:45"
  }
}
```

---

### 2. Get Product by ID
**GET** `/products/{id}`

**Example** `GET /products/1`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Product retrieved successfully",
  "data": { ... }
}
```

---

### 3. Search Products by Price Range
**GET** `/products/search?category=Electronics&minPrice=500&maxPrice=2000&page=0&size=10`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Search results retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "sku": "LAPTOP-001",
        "name": "Dell XPS 15 Laptop",
        "price": 1299.99,
        ...
      },
      {
        "id": 2,
        ...
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 15,
    "totalPages": 2,
    ...
  }
}
```

---

### 4. Get Low Stock Products
**GET** `/products/lowstock?page=0&size=20`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Low stock products retrieved successfully",
  "data": {
    "content": [
      {
        "id": 5,
        "sku": "MOUSE-001",
        "name": "Wireless Mouse",
        "price": 29.99,
        "stockQuantity": 3,
        "minStockLevel": 10,
        ...
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

---

### 5. Update Product
**PUT** `/products/{id}`

**Request** (All fields optional)
```json
{
  "name": "Dell XPS 15 Laptop (2026)",
  "price": 1399.99,
  "stockQuantity": 45
}
```

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Product updated successfully",
  "data": { ... }
}
```

---

### 6. Delete Product
**DELETE** `/products/{id}`

**Response** (204 No Content)

---

## Order Management Endpoints

### 1. Create Order
**POST** `/orders`

**Request**
```json
{
  "userId": 1,
  "orderItems": [
    {
      "productId": 1,
      "quantity": 2,
      "notes": "Gift wrap please"
    },
    {
      "productId": 2,
      "quantity": 1
    }
  ],
  "shippingAddress": "456 Oak Avenue, New York, NY 10001",
  "notes": "Please deliver before 5PM"
}
```

**Response** (201 Created)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 201,
  "message": "Order created successfully",
  "data": {
    "id": 1,
    "orderNumber": "ORD-20260509-ABC12",
    "user": {
      "id": 1,
      "firstName": "John",
      "lastName": "Doe",
      "email": "john.doe@example.com",
      ...
    },
    "orderStatus": "PENDING",
    "totalAmount": 2629.97,
    "shippingAddress": "456 Oak Avenue, New York, NY 10001",
    "notes": "Please deliver before 5PM",
    "estimatedDelivery": null,
    "orderItems": [
      {
        "id": 1,
        "orderId": 1,
        "productId": 1,
        "productName": "Dell XPS 15 Laptop",
        "productSku": "LAPTOP-001",
        "unitPrice": 1299.99,
        "quantity": 2,
        "discount": null,
        "notes": "Gift wrap please",
        "createdAt": "2026-05-09T10:30:45"
      },
      {
        "id": 2,
        "orderId": 1,
        "productId": 2,
        "productName": "Wireless Mouse",
        "productSku": "MOUSE-001",
        "unitPrice": 29.99,
        "quantity": 1,
        "discount": null,
        "notes": null,
        "createdAt": "2026-05-09T10:30:45"
      }
    ],
    "createdAt": "2026-05-09T10:30:45",
    "updatedAt": "2026-05-09T10:30:45"
  }
}
```

**Error** (400 - Insufficient Stock)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 400,
  "message": "Insufficient stock for product: Dell XPS 15 Laptop",
  "errorCode": "ORD-006"
}
```

---

### 2. Get Order by ID
**GET** `/orders/{id}`

**Example** `GET /orders/1`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Order retrieved successfully",
  "data": { ... }
}
```

---

### 3. Get Order by Order Number
**GET** `/orders/number/{orderNumber}`

**Example** `GET /orders/number/ORD-20260509-ABC12`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Order retrieved successfully",
  "data": { ... }
}
```

---

### 4. Get Orders for User
**GET** `/orders/user/{userId}?page=0&size=10`

**Example** `GET /orders/user/1?page=0&size=10&sort=createdAt,desc`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Orders retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "orderNumber": "ORD-20260509-ABC12",
        ...
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 5,
    "totalPages": 1
  }
}
```

---

### 5. Get Orders by Status
**GET** `/orders/by-status/{status}?page=0&size=20`

**Example** `GET /orders/by-status/CONFIRMED?page=0&size=20`

**Valid Status Values**: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Orders retrieved successfully",
  "data": {
    "content": [
      { ... }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 12,
    "totalPages": 1
  }
}
```

---

### 6. Update Order Status
**PUT** `/orders/{id}/status`

**Request**
```json
{
  "newStatus": "CONFIRMED"
}
```

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Order status updated successfully",
  "data": {
    "id": 1,
    "orderNumber": "ORD-20260509-ABC12",
    "orderStatus": "CONFIRMED",
    ...
  }
}
```

**Valid Transitions**:
- `PENDING` → `CONFIRMED` or `CANCELLED`
- `CONFIRMED` → `SHIPPED` or `CANCELLED`
- `SHIPPED` → `DELIVERED`
- `DELIVERED`, `CANCELLED` → Terminal (no transitions)

**Error** (400 - Invalid Transition)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 400,
  "message": "Invalid status transition from DELIVERED to SHIPPED",
  "errorCode": "ORD-002"
}
```

---

### 7. Cancel Order
**POST** `/orders/{id}/cancel`

**Response** (200 OK)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 200,
  "message": "Order cancelled successfully",
  "data": {
    "id": 1,
    "orderNumber": "ORD-20260509-ABC12",
    "orderStatus": "CANCELLED",
    ...
  }
}
```

**Error** (400 - Can't Cancel)
```json
{
  "timestamp": "2026-05-09T10:30:45",
  "status": 400,
  "message": "Cannot cancel order with status: DELIVERED",
  "errorCode": "ORD-002"
}
```

---

## Error Codes Reference

| Code | HTTP Status | Description |
|------|------------|-------------|
| USER-001 | 404 | User not found |
| USER-002 | 400 | Email already exists |
| USER-003 | 400 | Invalid email format |
| USER-004 | 400 | Invalid password |
| PROD-001 | 404 | Product not found |
| PROD-002 | 400 | Product out of stock |
| PROD-003 | 400 | Invalid price |
| ORD-001 | 404 | Order not found |
| ORD-002 | 400 | Invalid status transition |
| ORD-003 | 400 | Order is empty (no items) |
| ORD-004 | 404 | Product in order not found |
| ORD-005 | 400 | Duplicate items in order |
| ORD-006 | 400 | Insufficient stock |
| VAL-001 | 400 | Validation failed |
| ERR-001 | 500 | Internal server error |

---

## Correlation ID Tracking

Every request returns a correlation ID for tracing:

**Response Header**
```
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

Use this ID to track the request through logs and debug issues.

---

## Rate Limiting

Currently not implemented. Future versions will include rate limiting per API key.

---

## CORS

Current CORS configuration allows requests from `localhost:3000` (typical React dev server). Update in production.

---

## Versioning

API version is included in the URL path:
- Current: `/api/v1`
- Future versions: `/api/v2`, etc.

Breaking changes will increment the version number.

