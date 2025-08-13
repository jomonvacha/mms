package com.roots.mms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("status")
    private int status;

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("details")
    private String details;

    @JsonProperty("path")
    private String path;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("validation_errors")
    private List<ValidationError> validationErrors;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(int status, String error, String errorCode, String message, String path) {
        this(status, error, message, path);
        this.errorCode = errorCode;
    }

    // Getters and Setters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Builder pattern for fluent creation
    public static class Builder {
        private final ErrorResponse errorResponse;

        public Builder() {
            this.errorResponse = new ErrorResponse();
        }

        public Builder status(int status) {
            errorResponse.setStatus(status);
            return this;
        }

        public Builder error(String error) {
            errorResponse.setError(error);
            return this;
        }

        public Builder errorCode(String errorCode) {
            errorResponse.setErrorCode(errorCode);
            return this;
        }

        public Builder message(String message) {
            errorResponse.setMessage(message);
            return this;
        }

        public Builder details(String details) {
            errorResponse.setDetails(details);
            return this;
        }

        public Builder path(String path) {
            errorResponse.setPath(path);
            return this;
        }

        public Builder traceId(String traceId) {
            errorResponse.setTraceId(traceId);
            return this;
        }

        public Builder validationErrors(List<ValidationError> validationErrors) {
            errorResponse.setValidationErrors(validationErrors);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            errorResponse.setMetadata(metadata);
            return this;
        }

        public ErrorResponse build() {
            return errorResponse;
        }
    }
}
