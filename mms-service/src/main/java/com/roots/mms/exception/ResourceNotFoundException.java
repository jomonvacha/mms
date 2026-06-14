package com.roots.mms.exception;

public class ResourceNotFoundException extends MemberManagementException {

  public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
    super("RESOURCE_NOT_FOUND",
      String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
  }

  public ResourceNotFoundException(String message) {
    super("RESOURCE_NOT_FOUND", message);
  }
}
