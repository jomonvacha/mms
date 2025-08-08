package com.roots.mms.exception;

public class AuthorizationException extends MemberManagementException {
    
    public AuthorizationException(String message) {
        super("AUTHORIZATION_FAILED", message);
    }

    public AuthorizationException(String resource, String action) {
        super("AUTHORIZATION_FAILED", 
              String.format("Access denied for action '%s' on resource '%s'", action, resource));
    }
}
