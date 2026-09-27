package com.accessorchestrator.web;

import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rejects requests to {@link AdminOnly} endpoints unless the signed-in user is an admin (403). */
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;
    private final UserService userService;

    public AdminOnlyInterceptor(CurrentUser currentUser, UserService userService) {
        this.currentUser = currentUser;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (handler instanceof HandlerMethod method && isAdminOnly(method)) {
            String userId = currentUser.id();
            if (!userService.getUser(userId).admin()) {
                throw new ForbiddenException("This area is available to administrators only");
            }
        }
        return true;
    }

    private static boolean isAdminOnly(HandlerMethod method) {
        return method.hasMethodAnnotation(AdminOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), AdminOnly.class);
    }
}
