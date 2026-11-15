package com.copmap.exceptions;

import com.copmap.dtos.ApiError;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            UsernameNotFoundException.class,
            BadCredentialsException.class,
            CredentialsExpiredException.class,
            DisabledException.class
    })
    public ResponseEntity<ApiError> handleAuthException(Exception e, HttpServletRequest request) {
        log.warn("Authentication failure at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler({
            EntityNotFoundException.class,
            ResourceNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(Exception e, HttpServletRequest request) {
        log.info("Resource not found at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failure at {}: {}", request.getRequestURI(), errors);
        ApiError apiError = ApiError.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                "Request validation failed on one or more fields",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Constraint Violation",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("Illegal state transition at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("Malformed JSON request at {}: {}", request.getRequestURI(), e.getMessage());
        ApiError apiError = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON",
                "Malformed JSON request payload or unsupported data type",
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("Parameter type mismatch at {}: {}", request.getRequestURI(), e.getMessage());
        String msg = String.format("Parameter '%s' should be of type '%s'", e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "valid type");
        ApiError apiError = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Type Mismatch",
                msg,
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Unhandled internal server error at " + request.getRequestURI(), e);
        ApiError apiError = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected internal error occurred. Please contact system administrator.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }
}
