package com.roots.mms.exception;

public class BusinessRuleException extends MemberManagementException {
    
    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String message, Object... args) {
        super("BUSINESS_RULE_VIOLATION", message, args);
    }
}
