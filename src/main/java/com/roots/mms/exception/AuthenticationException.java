package com.roots.mms.exception;

public class AuthenticationException extends MemberManagementException {
    
    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super("AUTHENTICATION_FAILED", message, cause);
    }
}
