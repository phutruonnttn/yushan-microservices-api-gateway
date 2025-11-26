package com.yushan.gateway.filter;

import com.yushan.gateway.service.UserBlocklistService;
import com.yushan.gateway.util.HmacUtil;
import com.yushan.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * JWT Authentication Gateway Filter
 * 
 * This filter validates JWT tokens at the API Gateway level before routing to microservices.
 * 
 * Flow:
 * 1. Check if path is public (skip validation)
 * 2. Extract JWT token from Authorization header
 * 3. Validate token (signature, expiration, token type)
 * 4. Extract user info from token
 * 5. Add user info to request headers for downstream services
 * 6. Mark request as gateway-validated
 */
@Component
@Slf4j
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserBlocklistService userBlocklistService;
    
    @PostConstruct
    public void init() {
        log.info("JwtAuthenticationGatewayFilter initialized and registered!");
    }

    /**
     * Shared secret for HMAC signature generation
     * Must match the secret in all microservices
     */
    @Value("${gateway.hmac.secret:${GATEWAY_HMAC_SECRET:yushan-gateway-hmac-secret-key-2024}}")
    private String hmacSecret;

    /**
     * Public endpoints that don't require authentication (all HTTP methods)
     */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
        // Auth endpoints
        "/api/v1/auth/",
        "/api/v1/public/",
        
        // Health & monitoring
        "/api/v1/health",
        "/health",
        "/actuator/",
        "/error",
        
        // Swagger/OpenAPI documentation
        "/v3/api-docs/",
        "/swagger-ui/",
        "/swagger-ui.html",
        "/swagger-resources/",
        "/webjars/",
        "/api-docs/",
        
        // Test endpoints (development)
        "/api/test/",
        
        // Static resources
        "/uploads/",
        "/static/"
    );

    /**
     * Public GET endpoints (read-only, no authentication required)
     */
    private static final List<String> PUBLIC_GET_PATHS = Arrays.asList(
        // User Service
        "/api/v1/users/all/ranking",
        
        // Gamification Service
        "/api/v1/gamification/stats/all",    // Get all stats (exact match)
        
        // Content Service - Novels
        "/api/v1/novels",                    // List novels (exact match or with query params)
        "/api/v1/novels/count",              // Get count
        "/api/v1/novels/category/",          // Get by category
        "/api/v1/novels/uuid/",              // Get by UUID
        "/api/v1/novels/author/",            // Get by author
        "/api/v1/novels/",                    // Get novel by ID (pattern: /api/v1/novels/{id} but not /api/v1/novels/{id}/vote)
        
        // Content Service - Categories
        "/api/v1/categories",              // GET all categories (exact match)
        "/api/v1/categories/",            // GET category by ID or other paths
        
        // Content Service - Search
        "/api/v1/search",                  // GET search (exact match)
        "/api/v1/search/",                 // GET search with sub-paths
        
        // Content Service - Chapters
        "/api/v1/chapters/novel/",           // Get chapters by novel
        "/api/v1/chapters/search",            // Search chapters
        "/api/v1/chapters/exists",            // Check chapter existence
        "/api/v1/chapters/",                  // Get chapter by UUID (pattern: /api/v1/chapters/{uuid} but not /api/v1/chapters/{uuid}/next)
        
        // Engagement Service
        "/api/v1/comments/",                 // Get comments (read-only)
        "/api/v1/reviews",                   // Get reviews list (exact match)
        "/api/v1/reviews/",                  // Get reviews (read-only)
        
        // Analytics Service
        "/api/v1/ranking/"                   // Public rankings
    );

    /**
     * Public POST endpoints (write operations that don't require authentication)
     */
    private static final List<String> PUBLIC_POST_PATHS = Arrays.asList(
        // User Service
        "/api/v1/users/batch/get",           // Batch get users (POST method)
        
        // Content Service - Novels
        "/api/v1/novels/batch/get",          // Batch get novels (POST method)
        
        // Content Service - Chapters
        "/api/v1/chapters/batch/get",        // Batch get chapters (POST method)
        
        // Gamification Service
        "/api/v1/gamification/stats/all",    // Get all stats
        "/api/v1/gamification/stats/batch"   // Batch get stats
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().toString() : "UNKNOWN";

        log.error("JWT Filter - Processing request: {} {} - FILTER IS WORKING!", method, path);

        // Skip validation for public paths
        if (isPublicPath(path, method)) {
            log.info("JWT Filter - Public path, skipping validation: {}", path);
            return chain.filter(exchange);
        }

        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            log.debug("JWT Filter - OPTIONS request, skipping validation");
            return chain.filter(exchange);
        }

        // Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        log.info("JWT Filter - Authorization header present: {}", authHeader != null);
        if (authHeader != null) {
            log.info("JWT Filter - Authorization header starts with Bearer: {}", authHeader.startsWith("Bearer "));
        }
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter - Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix
        log.info("JWT Filter - Extracted token, length: {}", token.length());

        try {
            // Validate token
            log.info("JWT Filter - Validating token for path: {}", path);
            boolean isValid = jwtUtil.validateToken(token);
            log.info("JWT Filter - Token validation result: {} for path: {}", isValid, path);
            if (!isValid) {
                log.warn("JWT Filter - Invalid token for path: {}", path);
                return unauthorized(exchange, "Invalid or expired token");
            }
            log.info("JWT Filter - Token validated successfully for path: {}", path);

            // Extract user info from token
            String userId = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);
            Integer status = jwtUtil.extractStatus(token);

            if (userId == null || email == null) {
                log.warn("JWT Filter - Missing user info in token for path: {}", path);
                return unauthorized(exchange, "Invalid token: missing user information");
            }

            // Check if user is in blocklist (SUSPENDED or BANNED)
            try {
                UUID userUuid = UUID.fromString(userId);
                if (userBlocklistService.isBlocked(userUuid)) {
                    log.warn("JWT Filter - User {} is blocked (SUSPENDED or BANNED), rejecting request for path: {}", userId, path);
                    return forbidden(exchange, "User account is disabled or suspended");
                }
            } catch (IllegalArgumentException e) {
                log.warn("JWT Filter - Invalid user ID format: {}", userId);
                // Continue with request - invalid UUID format, but let downstream service handle it
            } catch (Exception e) {
                log.error("JWT Filter - Error checking blocklist for user: {}", userId, e);
                // If blocklist check fails, fallback to JWT status check (graceful degradation)
                // Continue with request - blocklist may not be synced yet
            }

            log.debug("JWT Filter - Token validated successfully for user: {} ({})", email, userId);

            // Generate HMAC signature to prove request is from Gateway
            long timestamp = System.currentTimeMillis();
            String signature;
            try {
                signature = HmacUtil.generateSignature(userId, email, role, timestamp, hmacSecret);
                log.debug("JWT Filter - Generated HMAC signature for path: {}", path);
            } catch (Exception e) {
                log.error("JWT Filter - Failed to generate HMAC signature", e);
                return unauthorized(exchange, "Internal server error");
            }

            // Add user info and HMAC signature to request headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Username", username != null ? username : "")
                    .header("X-User-Role", role != null ? role : "USER")
                    .header("X-User-Status", status != null ? String.valueOf(status) : "0")  // User status (0 = ACTIVE, 1 = SUSPENDED, etc.)
                    .header("X-Gateway-Validated", "true")  // Mark as gateway-validated
                    .header("X-Gateway-Timestamp", String.valueOf(timestamp))  // Timestamp for signature verification
                    .header("X-Gateway-Signature", signature)  // HMAC signature to prevent forgery
                    .header("Authorization", authHeader)     // Keep original token for backward compatibility
                    .build();

            log.info("JWT Filter - Forwarding request to {} with gateway headers (userId={}, email={})", path, userId, email);
            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("JWT Filter - Error validating token for path: {}", path, e);
            return unauthorized(exchange, "Token validation failed: " + e.getMessage());
        }
    }

    /**
     * Check if path is public (no authentication required)
     * 
     * @param path Request path
     * @param method HTTP method
     * @return true if path is public, false otherwise
     */
    private boolean isPublicPath(String path, String method) {
        // Remove query string for matching
        String pathWithoutQuery = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
        
        // Check exact matches first (before startsWith) - for all methods
        if (pathWithoutQuery.equals("/api/v1/reviews") ||
            pathWithoutQuery.equals("/api/v1/gamification/stats/all")) {
            return true;
        }
        
        // Check exact public paths (all methods)
        for (String publicPath : PUBLIC_PATHS) {
            if (pathWithoutQuery.startsWith(publicPath)) {
                return true;
            }
        }

        // Check public GET paths
        if ("GET".equals(method)) {
            // Check exact matches first (before startsWith)
            if (pathWithoutQuery.equals("/api/v1/categories") || 
                pathWithoutQuery.equals("/api/v1/search") ||
                pathWithoutQuery.equals("/api/v1/reviews") ||
                pathWithoutQuery.equals("/api/v1/gamification/stats/all")) {
                return true;
            }
            
            // Special GET endpoints with patterns (check first)
            // GET /api/v1/novels/{id}/vote-count
            if (pathWithoutQuery.matches("/api/v1/novels/\\d+/vote-count")) {
                return true;
            }
            // GET /api/v1/chapters/{uuid}/next
            if (pathWithoutQuery.matches("/api/v1/chapters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/next")) {
                return true;
            }
            // GET /api/v1/chapters/{uuid}/previous
            if (pathWithoutQuery.matches("/api/v1/chapters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/previous")) {
                return true;
            }
            // GET /api/v1/chapters/novel/{novelId}/number/{number}
            if (pathWithoutQuery.matches("/api/v1/chapters/novel/\\d+/number/\\d+")) {
                return true;
            }
            
            for (String publicGetPath : PUBLIC_GET_PATHS) {
                if (pathWithoutQuery.startsWith(publicGetPath)) {
                    // Special handling for paths that need exact matching
                    // /api/v1/novels/{id} - allow but not /api/v1/novels/{id}/vote
                    if (publicGetPath.equals("/api/v1/novels/")) {
                        // Match /api/v1/novels/{id} (numeric ID only, no sub-paths)
                        if (pathWithoutQuery.matches("/api/v1/novels/\\d+$")) {
                            return true;
                        }
                        // Also allow /api/v1/novels (list) and other specific paths
                        if (pathWithoutQuery.equals("/api/v1/novels") || 
                            pathWithoutQuery.startsWith("/api/v1/novels/count") ||
                            pathWithoutQuery.startsWith("/api/v1/novels/category/") || 
                            pathWithoutQuery.startsWith("/api/v1/novels/uuid/") ||
                            pathWithoutQuery.startsWith("/api/v1/novels/author/")) {
                            return true;
                        }
                    }
                    // /api/v1/chapters/{uuid} - allow but not /api/v1/chapters/{uuid}/next
                    else if (publicGetPath.equals("/api/v1/chapters/")) {
                        // Match /api/v1/chapters/{uuid} (UUID format, no sub-paths)
                        // UUID format: 8-4-4-4-12 hex digits
                        if (pathWithoutQuery.matches("/api/v1/chapters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                            return true;
                        }
                        // Also allow /api/v1/chapters/novel/, /api/v1/chapters/search, etc.
                        if (pathWithoutQuery.startsWith("/api/v1/chapters/novel/") || 
                            pathWithoutQuery.startsWith("/api/v1/chapters/search") ||
                            pathWithoutQuery.startsWith("/api/v1/chapters/exists")) {
                            return true;
                        }
                    }
                    // /api/v1/categories - exact match or with sub-paths
                    else if (publicGetPath.equals("/api/v1/categories")) {
                        // Exact match for /api/v1/categories
                        if (pathWithoutQuery.equals("/api/v1/categories")) {
                            return true;
                        }
                    }
                    // /api/v1/search - exact match or with sub-paths
                    else if (publicGetPath.equals("/api/v1/search")) {
                        // Exact match for /api/v1/search
                        if (pathWithoutQuery.equals("/api/v1/search")) {
                            return true;
                        }
                    }
                    // /api/v1/gamification/stats/all - exact match
                    else if (publicGetPath.equals("/api/v1/gamification/stats/all")) {
                        if (pathWithoutQuery.equals("/api/v1/gamification/stats/all")) {
                            return true;
                        }
                    }
                    // For other paths, simple startsWith check
                    else {
                        return true;
                    }
                }
            }
        }

        // Check public POST paths
        if ("POST".equals(method)) {
            for (String publicPostPath : PUBLIC_POST_PATHS) {
                if (pathWithoutQuery.startsWith(publicPostPath)) {
                    return true;
                }
            }
            
            // Special cases for POST endpoints with patterns
            // POST /api/v1/novels/{id}/view - increment view
            if (pathWithoutQuery.matches("/api/v1/novels/\\d+/view")) {
                return true;
            }
            // POST /api/v1/chapters/{uuid}/view - increment view
            // UUID format: 8-4-4-4-12 hex digits
            if (pathWithoutQuery.matches("/api/v1/chapters/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/view")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return unauthorized response
     * 
     * @param exchange ServerWebExchange
     * @param message Error message
     * @return Mono<Void>
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        String body = String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Return forbidden response (403)
     * 
     * @param exchange ServerWebExchange
     * @param message Error message
     * @return Mono<Void>
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        String body = String.format("{\"error\": \"Forbidden\", \"message\": \"%s\", \"status\": 403}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Set filter order (high priority, run early)
     * Lower number = higher priority
     */
    @Override
    public int getOrder() {
        return -100; // High priority, run before other filters
    }
}

