package com.accessorchestrator.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts a controller (or a single endpoint) to users with the admin flag. Enforced by {@link AdminOnlyInterceptor}. */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}
