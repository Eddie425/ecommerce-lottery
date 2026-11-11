package com.example.lottery.config;

import com.example.lottery.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager users(
            PasswordEncoder encoder,
            @Value("${app.security.user.username}") String userName,
            @Value("${app.security.user.password}") String userPassword,
            @Value("${app.security.admin.username}") String adminName,
            @Value("${app.security.admin.password}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername(userName).password(encoder.encode(userPassword)).roles("USER").build(),
                User.withUsername(adminName).password(encoder.encode(adminPassword)).roles("USER", "ADMIN").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> writeError(
                                objectMapper, response, HttpStatus.UNAUTHORIZED, "Authentication is required",
                                request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeError(
                                objectMapper, response, HttpStatus.FORBIDDEN, "Insufficient permissions",
                                request.getRequestURI())));
        return http.build();
    }

    private void writeError(
            ObjectMapper objectMapper,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String message,
            String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Lottery API\"");
        }
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path));
    }
}
