package com.accessorchestrator.exception;

/** The signed-in user is known but not allowed to perform this action (mapped to 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
