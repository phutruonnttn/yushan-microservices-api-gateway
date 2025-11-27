package com.yushan.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Rate Limiter Gateway Filter
 * 
 * This filter applies rate limiting to all requests passing through the gateway.
 * Uses Resilience4j RateLimiter to control request rate.
 * 
 * Configuration:
 * - limitForPeriod: Maximum number of requests allowed in the time window
 * - limitRefreshPeriod: Time window for rate limiting
 * - timeoutDuration: Maximum time to wait for a permit (0 = fail immediately)
 */
@Component
@Slf4j
public class RateLimiterGatewayFilter implements GlobalFilter, Ordered {

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    /**
     * Paths that should be excluded from rate limiting
     */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/actuator/",
        "/health",
        "/api/v1/health",
        "/error"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip rate limiting for excluded paths
        if (isExcludedPath(path)) {
            log.debug("Rate Limiter - Excluded path, skipping: {}", path);
            return chain.filter(exchange);
        }

        // Get rate limiter instance
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("api-gateway-global");
        
        if (rateLimiter == null) {
            log.warn("Rate Limiter - Rate limiter 'api-gateway-global' not found, skipping rate limiting");
            return chain.filter(exchange);
        }

        // Try to acquire a permit
        boolean permitAcquired = rateLimiter.acquirePermission();
        
        if (!permitAcquired) {
            log.warn("Rate Limiter - Rate limit exceeded for path: {} from IP: {}", 
                    path, request.getRemoteAddress());
            return rateLimitExceeded(exchange);
        }

        // Permit acquired, continue with the request
        log.debug("Rate Limiter - Permit acquired for path: {}", path);
        return chain.filter(exchange);
    }

    /**
     * Check if path should be excluded from rate limiting
     * 
     * @param path Request path
     * @return true if path should be excluded, false otherwise
     */
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Return rate limit exceeded response (429 Too Many Requests)
     * 
     * @param exchange ServerWebExchange
     * @return Mono<Void>
     */
    private Mono<Void> rateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        response.getHeaders().add("Retry-After", "60"); // Suggest retry after 60 seconds

        String body = "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again later.\", \"status\": 429}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Set filter order (run after JWT authentication but before routing)
     * Lower number = higher priority
     */
    @Override
    public int getOrder() {
        return -50; // Run after JWT filter (-100) but before routing
    }
}

