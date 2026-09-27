package com.accessorchestrator.exception;

/** A well-formed request that violates a business rule (mapped to 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
