package com.accessorchestrator.iga;

/** The IGA system could not be reached or answered unexpectedly (mapped to 502 Bad Gateway). */
public class IgaIntegrationException extends RuntimeException {

    public IgaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
