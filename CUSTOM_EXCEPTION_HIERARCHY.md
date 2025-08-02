# Custom Exception Hierarchy Implementation

## Problem Identified
The service layer was throwing generic `RuntimeException` which loses original exception type information and makes it difficult for callers to handle specific error scenarios appropriately.

## Previous Implementation (Generic Exceptions)

### Problems with Generic RuntimeException
```java
// ❌ GENERIC: Loss of exception type information
public List<Transaction> getAllTransactions() {
    try {
        String userId = authUser.getCurrentUserId();
        return transactionRepository.findByUserId(new ObjectId(userId));
    } catch (Exception e) {
        log.error("Error retrieving transactions", e);
        throw new RuntimeException("Error retrieving transactions", e);  // ❌ Generic
    }
}

public ResponseEntity<Transaction> createTransaction(TransactionRequest request) {
    try {
        // ... business logic
        return ResponseEntity.ok(savedTransaction);
    } catch (Exception e) {
        log.error("Error creating transaction", e);
        throw new RuntimeException("Error creating transaction", e);  // ❌ Generic
    }
}
```

**Problems:**
- ❌ **Lost Context**: All exceptions become generic `RuntimeException`
- ❌ **Poor Error Handling**: Callers can't distinguish between different error types
- ❌ **Inconsistent HTTP Status**: All errors return same status code
- ❌ **Limited Debugging**: No categorization of error types
- ❌ **Poor User Experience**: Generic error messages for users

## New Implementation (Custom Exception Hierarchy)

### TransactionServiceException - Structured Exception Class
```java
/**
 * Custom exception class for transaction service operations
 * Preserves original exception information while providing specific transaction context
 */
public class TransactionServiceException extends RuntimeException {
    
    private final TransactionErrorType errorType;
    private final String operationContext;

    // ✅ MULTIPLE CONSTRUCTORS: Flexible exception creation
    public TransactionServiceException(TransactionErrorType errorType, String message) { ... }
    public TransactionServiceException(TransactionErrorType errorType, String message, String operationContext) { ... }
    public TransactionServiceException(TransactionErrorType errorType, String message, Throwable cause) { ... }
    public TransactionServiceException(TransactionErrorType errorType, String message, String operationContext, Throwable cause) { ... }

    // ✅ RICH CONTEXT: Detailed error information
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.name()).append("] ");
        sb.append(getMessage());
        
        if (operationContext != null) {
            sb.append(" (Operation: ").append(operationContext).append(")");
        }
        
        if (getCause() != null) {
            sb.append(" - Caused by: ").append(getCause().getClass().getSimpleName())
              .append(": ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }

    // ✅ ERROR CATEGORIZATION: Specific error types
    public enum TransactionErrorType {
        AUTHENTICATION_ERROR,    // Auth/authorization issues
        VALIDATION_ERROR,        // Data validation failures
        DATABASE_ERROR,          // Database access problems
        TRANSACTION_NOT_FOUND,   // Resource not found
        USER_ERROR,             // User-related issues
        BUSINESS_LOGIC_ERROR,   // Business rule violations
        EXTERNAL_SERVICE_ERROR, // Third-party service failures
        UNKNOWN_ERROR          // Unexpected errors
    }
}
```

### Updated Service Layer - Specific Exception Handling
```java
@Service
public class TransactionService {

    // ✅ SPECIFIC: Different exceptions for different error scenarios
    public List<Transaction> getAllTransactions() {
        try {
            String userId = authUser.getCurrentUserId();
            return transactionRepository.findByUserId(new ObjectId(userId));
        } catch (IllegalArgumentException e) {
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.AUTHENTICATION_ERROR,
                "Invalid user ID format",
                "getAllTransactions",
                e  // ✅ Preserves original exception
            );
        } catch (DataAccessException e) {
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
                "Failed to retrieve transactions from database",
                "getAllTransactions",
                e  // ✅ Preserves original exception
            );
        } catch (Exception e) {
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.UNKNOWN_ERROR,
                "Unexpected error retrieving transactions",
                "getAllTransactions",
                e  // ✅ Preserves original exception
            );
        }
    }

    // ✅ BUSINESS LOGIC: Custom exceptions for business scenarios
    public ResponseEntity<Transaction> createTransaction(TransactionRequest request) {
        try {
            User user = authUser.getCurrentUser();
            
            if (user.getId() == null) {
                throw new TransactionServiceException(
                    TransactionServiceException.TransactionErrorType.USER_ERROR,
                    "User ID not available for transaction creation",
                    "createTransaction"  // ✅ Operation context
                );
            }
            
            // ... business logic
            
        } catch (TransactionServiceException e) {
            throw e;  // ✅ Re-throw custom exceptions without wrapping
        } catch (IllegalArgumentException e) {
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.VALIDATION_ERROR,
                "Invalid transaction data: " + e.getMessage(),
                "createTransaction",
                e
            );
        } catch (DataAccessException e) {
            throw new TransactionServiceException(
                TransactionServiceException.TransactionErrorType.DATABASE_ERROR,
                "Failed to save transaction to database",
                "createTransaction",
                e
            );
        }
    }
}
```

### Updated Controller Layer - Smart Error Mapping
```java
@RestController
public class TransactionController {

    // ✅ SMART MAPPING: Different HTTP status codes for different error types
    @GetMapping
    public ResponseEntity<?> getTransactions(/* parameters */) {
        try {
            List<Transaction> transactions = transactionService.getFilteredTransactions(filter);
            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body("Invalid parameter value: " + e.getMessage());
        } catch (TransactionServiceException e) {
            log.error("Transaction service error: {}", e.getDetailedMessage());
            
            // ✅ ERROR TYPE MAPPING: Appropriate HTTP status for each error type
            HttpStatus status = switch (e.getErrorType()) {
                case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;           // 401
                case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;                // 400
                case TRANSACTION_NOT_FOUND -> HttpStatus.NOT_FOUND;             // 404
                case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;       // 500
                case BUSINESS_LOGIC_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;  // 422
                case EXTERNAL_SERVICE_ERROR -> HttpStatus.BAD_GATEWAY;         // 502
                default -> HttpStatus.INTERNAL_SERVER_ERROR;                   // 500
            };
            
            return ResponseEntity.status(status)
                .body("Transaction error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error retrieving transactions: " + e.getMessage());
        }
    }
}
```

## Benefits Achieved

### ✅ **Preserved Exception Hierarchy**
```java
// ✅ Original exception information is preserved
try {
    // MongoDB query fails
} catch (DataAccessException e) {
    throw new TransactionServiceException(
        DATABASE_ERROR,
        "Failed to retrieve transactions",
        "getAllTransactions",
        e  // ← Original exception preserved in cause chain
    );
}

// Exception chain: TransactionServiceException -> DataAccessException -> MongoException
```

### ✅ **Contextual Error Information**
```java
// ✅ Rich error context
TransactionServiceException exception = ...;
exception.getErrorType();          // → DATABASE_ERROR
exception.getOperationContext();   // → "getAllTransactions"
exception.getDetailedMessage();    // → "[DATABASE_ERROR] Failed to retrieve transactions (Operation: getAllTransactions) - Caused by: DataAccessException: Connection timeout"
```

### ✅ **Proper HTTP Status Mapping**
```java
// ✅ Appropriate HTTP status codes
AUTHENTICATION_ERROR    → 401 Unauthorized
VALIDATION_ERROR        → 400 Bad Request
TRANSACTION_NOT_FOUND   → 404 Not Found
DATABASE_ERROR          → 500 Internal Server Error
BUSINESS_LOGIC_ERROR    → 422 Unprocessable Entity
EXTERNAL_SERVICE_ERROR  → 502 Bad Gateway
UNKNOWN_ERROR          → 500 Internal Server Error
```

### ✅ **Enhanced Debugging & Monitoring**
```java
// ✅ Categorized logging for better monitoring
log.error("Transaction service error: {}", e.getDetailedMessage());
// Output: "[DATABASE_ERROR] Failed to retrieve transactions (Operation: getAllTransactions) - Caused by: DataAccessException: Connection timeout"

// ✅ Metrics and alerting can be based on error types
if (e.getErrorType() == DATABASE_ERROR) {
    alertingService.sendDatabaseAlert(e);
} else if (e.getErrorType() == AUTHENTICATION_ERROR) {
    securityMonitor.logAuthFailure(e);
}
```

### ✅ **Better User Experience**
```json
// ✅ Specific error responses
{
  "error": "Invalid parameter value: Invalid currency value: XYZ. Valid values are: [USD, EUR, INR, GBP, JPY]",
  "status": 400
}

{
  "error": "Transaction error: User ID not available for transaction creation",
  "status": 400
}

{
  "error": "Transaction error: Failed to retrieve transactions from database",
  "status": 500
}
```

## Exception Flow Examples

### Database Error Flow
```
1. MongoDB connection fails
2. DataAccessException thrown
3. Service catches and wraps:
   → TransactionServiceException(DATABASE_ERROR, "Failed to retrieve...", "getAllTransactions", DataAccessException)
4. Controller maps to HTTP 500
5. Client receives: "Transaction error: Failed to retrieve transactions from database"
6. Logs contain: "[DATABASE_ERROR] Failed to retrieve transactions (Operation: getAllTransactions) - Caused by: DataAccessException: Connection timeout"
```

### Validation Error Flow
```
1. Invalid user ID format
2. IllegalArgumentException thrown
3. Service catches and wraps:
   → TransactionServiceException(AUTHENTICATION_ERROR, "Invalid user ID format", "getAllTransactions", IllegalArgumentException)
4. Controller maps to HTTP 401
5. Client receives: "Transaction error: Invalid user ID format"
6. Logs contain: "[AUTHENTICATION_ERROR] Invalid user ID format (Operation: getAllTransactions) - Caused by: IllegalArgumentException: ..."
```

### Business Logic Error Flow
```
1. User ID not available during transaction creation
2. Service throws:
   → TransactionServiceException(USER_ERROR, "User ID not available for transaction creation", "createTransaction")
3. Controller maps to HTTP 400
4. Client receives: "Transaction creation error: User ID not available for transaction creation"
5. Logs contain: "[USER_ERROR] User ID not available for transaction creation (Operation: createTransaction)"
```

## Architecture Improvements

### Before vs After
| Aspect | Before (Generic) | After (Custom) |
|--------|------------------|----------------|
| **Exception Type** | ❌ Generic RuntimeException | ✅ Specific TransactionServiceException |
| **Error Context** | ❌ Generic message | ✅ Error type + operation context |
| **HTTP Mapping** | ❌ Always 500 | ✅ Appropriate status codes |
| **Debugging** | ❌ Limited context | ✅ Rich error information |
| **Monitoring** | ❌ Generic alerts | ✅ Categorized monitoring |
| **Client Experience** | ❌ Generic errors | ✅ Specific, actionable errors |
| **Exception Chain** | ❌ Lost original cause | ✅ Preserved exception hierarchy |

### Exception Handling Strategy
```
Controller Layer    │ HTTP Status Mapping & User-Friendly Messages
                   │
Service Layer      │ Business Logic + Custom Exception Creation
                   │
Repository Layer   │ Data Access + Original Exception Throwing
                   │
Database Layer     │ Native Database Exceptions
```

## Migration Impact
- ✅ **Better Error Responses**: Clients receive more specific and actionable error messages
- ✅ **Improved Monitoring**: Operations teams can set up alerts based on specific error types
- ✅ **Enhanced Debugging**: Developers get richer context for troubleshooting
- ✅ **No Breaking Changes**: API endpoints maintain same structure, just better error handling
- ✅ **Scalable Pattern**: Easy to extend for new error types and scenarios

This custom exception hierarchy provides a robust foundation for error handling that preserves exception context, enables appropriate HTTP status mapping, and improves both developer and user experience.
