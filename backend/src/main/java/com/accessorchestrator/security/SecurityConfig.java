package com.accessorchestrator.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP Basic sign-in for the API. Stateless: every call carries the credentials, no session cookie (so no CSRF
 * exposure). Authorization beyond "signed in" (admin, reporting line) stays in the application services.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .build();
    }

    /**
     * 401 as a ProblemDetail, deliberately <em>without</em> {@code WWW-Authenticate: Basic}: that header makes
     * browsers pop up their own login dialog instead of letting the app show its login page.
     */
    @Bean
    AuthenticationEntryPoint problemEntryPoint(ObjectMapper json) {
        return (request, response, ex) -> {
            ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    "Sign in with a valid user ID and password.");
            body.setTitle("Unauthorized");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(response.getOutputStream(), body);
        };
    }

    /** BCrypt, stored with its {@code {bcrypt}} prefix so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
