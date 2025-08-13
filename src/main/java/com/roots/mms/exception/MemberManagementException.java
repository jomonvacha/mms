package com.roots.mms.exception;

public class MemberManagementException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public MemberManagementException(String message) {
        super(message);
        this.errorCode = "GENERAL_ERROR";
        this.args = new Object[0];
    }

    public MemberManagementException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GENERAL_ERROR";
        this.args = new Object[0];
    }

    public MemberManagementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public MemberManagementException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public MemberManagementException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}
