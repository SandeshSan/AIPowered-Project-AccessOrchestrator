package com.accessorchestrator.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object key) {
        super(resource + " not found: " + key);
    }
}
