package com.accessorchestrator.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminOnlyInterceptor adminOnlyInterceptor;

    public WebConfig(AdminOnlyInterceptor adminOnlyInterceptor) {
        this.adminOnlyInterceptor = adminOnlyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminOnlyInterceptor).addPathPatterns("/api/**");
    }
}
