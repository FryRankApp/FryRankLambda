package com.fryrank.model.exceptions;

/**
 * Exception thrown when authorization fails.
 */
public class NotAuthorizedException extends Exception {
    
    public NotAuthorizedException(String message) {
        super(message);
    }
    
    public NotAuthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
