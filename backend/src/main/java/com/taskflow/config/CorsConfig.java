package com.taskflow.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the Vite dev server, configured via {@code CORS_ALLOWED_ORIGINS}.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    private static final List<String> DEFAULT_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final TaskflowProperties properties;

    public CorsConfig(TaskflowProperties properties) {
        this.properties = properties;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        List<String> origins = allowedOrigins();
        List<String> methods = allowedMethods();

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins.toArray(String[]::new))
                        .allowedMethods(methods.toArray(String[]::new))
                        .allowedHeaders("*")
                        .exposedHeaders("Location")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    private List<String> allowedOrigins() {
        List<String> configured = properties.cors().allowedOrigins();
        return configured.isEmpty() ? List.of("http://localhost:5173") : configured;
    }

    private List<String> allowedMethods() {
        List<String> configured = properties.cors().allowedMethods();
        return configured.isEmpty() ? DEFAULT_METHODS : configured;
    }
}