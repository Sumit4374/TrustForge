package com.sumit.trustforge.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Resource not found"); }
    public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"); }
    public static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
