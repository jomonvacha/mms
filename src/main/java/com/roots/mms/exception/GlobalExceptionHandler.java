package com.roots.mms.exception;

import com.roots.mms.dto.response.ErrorResponse;
import com.roots.mms.dto.response.ValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Resource not found");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Duplicate resource");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleException(
            BusinessRuleException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Business rule violation");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Validation error");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Method argument validation failed");

        List<ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::buildValidationError)
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("VALIDATION_FAILED")
                .message("Input validation failed")
                .details("One or more fields have validation errors")
                .path(request.getRequestURI())
                .traceId(traceId)
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Binding validation failed");

        List<ValidationError> validationErrors = ex.getFieldErrors()
                .stream()
                .map(this::buildValidationError)
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("BINDING_FAILED")
                .message("Data binding validation failed")
                .path(request.getRequestURI())
                .traceId(traceId)
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Constraint validation failed");

        List<ValidationError> validationErrors = ex.getConstraintViolations()
                .stream()
                .map(this::buildValidationError)
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Constraint validation failed")
                .path(request.getRequestURI())
                .traceId(traceId)
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Authentication failed");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .errorCode("AUTHENTICATION_FAILED")
                .message("Authentication failed")
                .details(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Bad credentials");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .errorCode("INVALID_CREDENTIALS")
                .message("Invalid username or password")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Access denied");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .errorCode("ACCESS_DENIED")
                .message("Access denied")
                .details("You don't have permission to access this resource")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Method not supported");

        String supportedMethods = String.join(", ", 
                Optional.ofNullable(ex.getSupportedMethods()).orElse(new String[0]));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("supported_methods", supportedMethods);
        metadata.put("requested_method", ex.getMethod());

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .errorCode("METHOD_NOT_SUPPORTED")
                .message(String.format("Request method '%s' not supported", ex.getMethod()))
                .details(String.format("Supported methods: %s", supportedMethods))
                .path(request.getRequestURI())
                .traceId(traceId)
                .metadata(metadata)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Media type not supported");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Unsupported Media Type")
                .errorCode("MEDIA_TYPE_NOT_SUPPORTED")
                .message("Media type not supported")
                .details(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Malformed JSON request");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("MALFORMED_JSON")
                .message("Malformed JSON request")
                .details("Please check the request body format")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Missing request parameter");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("MISSING_PARAMETER")
                .message(String.format("Required parameter '%s' is missing", ex.getParameterName()))
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Method argument type mismatch");

        String expectedType = ex.getRequiredType() != null ? 
                ex.getRequiredType().getSimpleName() : "Unknown";

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("TYPE_MISMATCH")
                .message(String.format("Parameter '%s' should be of type '%s'", 
                        ex.getName(), expectedType))
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "No handler found");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .errorCode("ENDPOINT_NOT_FOUND")
                .message("The requested endpoint was not found")
                .details(String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL()))
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Data integrity violation");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .errorCode("DATA_INTEGRITY_VIOLATION")
                .message("Data integrity constraint violation")
                .details("The operation violates a database constraint")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> handleSQLException(
            SQLException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Database error");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .errorCode("DATABASE_ERROR")
                .message("A database error occurred")
                .details("Please contact system administrator")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        logError(ex, traceId, "Unexpected error");

        ErrorResponse errorResponse = new ErrorResponse.Builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .details("Please contact system administrator")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ValidationError buildValidationError(FieldError fieldError) {
        return new ValidationError(
                fieldError.getField(),
                fieldError.getRejectedValue(),
                fieldError.getDefaultMessage(),
                fieldError.getCode()
        );
    }

    private ValidationError buildValidationError(ConstraintViolation<?> violation) {
        String fieldName = violation.getPropertyPath().toString();
        return new ValidationError(
                fieldName,
                violation.getInvalidValue(),
                violation.getMessage(),
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
        );
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void logError(Exception ex, String traceId, String context) {
        MDC.put("traceId", traceId);
        try {
            if (ex instanceof MemberManagementException) {
                logger.warn("Business exception in {}: {} (traceId: {})", context, ex.getMessage(), traceId);
            } else {
                logger.error("Unexpected exception in {}: {} (traceId: {})", context, ex.getMessage(), traceId, ex);
            }
        } finally {
            MDC.remove("traceId");
        }
    }
}
