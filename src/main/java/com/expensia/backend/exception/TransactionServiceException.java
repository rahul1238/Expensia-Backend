package com.expensia.backend.exception;

/**
 * Custom exception class for transaction service operations
 * Preserves original exception information while providing specific transaction context
 */
public class TransactionServiceException extends RuntimeException {

    private final TransactionErrorType errorType;
    private final String operationContext;

    /**
     * Constructor with error type and message
     * 
     * @param errorType Type of transaction error
     * @param message Error message
     */
    public TransactionServiceException(TransactionErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.operationContext = null;
    }

    /**
     * Constructor with error type, message, and operation context
     * 
     * @param errorType Type of transaction error
     * @param message Error message
     * @param operationContext Context of the operation that failed
     */
    public TransactionServiceException(TransactionErrorType errorType, String message, String operationContext) {
        super(message);
        this.errorType = errorType;
        this.operationContext = operationContext;
    }

    /**
     * Constructor with error type, message, and original cause
     * Preserves the original exception hierarchy
     * 
     * @param errorType Type of transaction error
     * @param message Error message
     * @param cause Original exception that caused this error
     */
    public TransactionServiceException(TransactionErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.operationContext = null;
    }

    /**
     * Constructor with all parameters
     * 
     * @param errorType Type of transaction error
     * @param message Error message
     * @param operationContext Context of the operation that failed
     * @param cause Original exception that caused this error
     */
    public TransactionServiceException(TransactionErrorType errorType, String message, String operationContext, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.operationContext = operationContext;
    }

    /**
     * Get the specific error type
     * 
     * @return TransactionErrorType indicating the category of error
     */
    public TransactionErrorType getErrorType() {
        return errorType;
    }

    /**
     * Get the operation context where the error occurred
     * 
     * @return String describing the operation context, or null if not provided
     */
    public String getOperationContext() {
        return operationContext;
    }

    /**
     * Get a detailed error message including context
     * 
     * @return Formatted error message with context information
     */
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

    /**
     * Enumeration of transaction error types for better categorization
     */
    public enum TransactionErrorType {
        /**
         * Authentication or authorization related errors
         */
        AUTHENTICATION_ERROR,
        
        /**
         * Data validation errors (invalid input parameters)
         */
        VALIDATION_ERROR,
        
        /**
         * Database access or query errors
         */
        DATABASE_ERROR,
        
        /**
         * Transaction not found errors
         */
        TRANSACTION_NOT_FOUND,
        
        /**
         * User not found or invalid user errors
         */
        USER_ERROR,
        
        /**
         * Business logic validation errors
         */
        BUSINESS_LOGIC_ERROR,
        
        /**
         * External service integration errors
         */
        EXTERNAL_SERVICE_ERROR,
        
        /**
         * Unknown or unexpected errors
         */
        UNKNOWN_ERROR
    }
}
