package com.accessorchestrator.web;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The signed-in user's ID, taken from the authenticated principal (HTTP Basic). */
@Component
public class CurrentUser {

    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        return auth.getName();
    }
}
