package com.mockiga.domain;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String requestId, IgaRequestStatus from, IgaRequestStatus to) {
        super("Request " + requestId + " cannot move from " + from + " to " + to);
    }
}
