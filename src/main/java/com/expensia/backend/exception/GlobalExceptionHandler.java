package com.expensia.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });
    return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(TransactionServiceException.class)
  public ResponseEntity<Map<String, Object>> handleTransactionServiceException(TransactionServiceException ex) {
    HttpStatus status = switch (ex.getErrorType()) {
      case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
      case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
      case TRANSACTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      case BUSINESS_LOGIC_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
      case USER_ERROR -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };

    Map<String, Object> body = new HashMap<>();
    body.put("message", ex.getMessage());
    body.put("type", ex.getErrorType().name());
  body.put("context", ex.getOperationContext());
    return ResponseEntity.status(status).body(body);
  }
}
