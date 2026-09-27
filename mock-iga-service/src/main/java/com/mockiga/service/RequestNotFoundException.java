package com.mockiga.service;

public class RequestNotFoundException extends RuntimeException {

    public RequestNotFoundException(String requestId) {
        super("Access request not found: " + requestId);
    }
}
