package com.roots.mms.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ValidationError {

    @JsonProperty("field")
    private String field;

    @JsonProperty("rejected_value")
    private Object rejectedValue;

    @JsonProperty("message")
    private String message;

    @JsonProperty("code")
    private String code;

    public ValidationError() {
    }

    public ValidationError(String field, Object rejectedValue, String message) {
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.message = message;
    }

    public ValidationError(String field, Object rejectedValue, String message, String code) {
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.message = message;
        this.code = code;
    }

    // Getters and Setters
    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public void setRejectedValue(Object rejectedValue) {
        this.rejectedValue = rejectedValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
