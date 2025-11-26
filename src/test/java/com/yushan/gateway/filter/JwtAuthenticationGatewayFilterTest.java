package com.yushan.gateway.filter;

import com.yushan.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationGatewayFilter Tests")
class JwtAuthenticationGatewayFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationGatewayFilter filter;

    private String hmacSecret = "test-hmac-secret-key";
    private String jwtSecret = "test-secret-key-for-jwt-validation-minimum-256-bits-required";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "hmacSecret", hmacSecret);
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should allow public auth path without authentication")
    void testFilter_PublicAuthPath() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public health endpoint without authentication")
    void testFilter_PublicHealthEndpoint() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow OPTIONS request without authentication")
    void testFilter_OptionsRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should reject request without Authorization header")
    void testFilter_MissingAuthorizationHeader() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should reject request with invalid Authorization header format")
    void testFilter_InvalidAuthorizationHeader() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should reject request with invalid token")
    void testFilter_InvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
        );

        when(jwtUtil.validateToken(invalidToken)).thenReturn(false);

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(jwtUtil).validateToken(invalidToken);
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should allow request with valid token and add headers")
    void testFilter_ValidToken() {
        // Given
        String token = createValidToken("user-123", "user@example.com", "testuser", "USER", 0);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );

        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn("user-123");
        when(jwtUtil.extractEmail(token)).thenReturn("user@example.com");
        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(jwtUtil.extractRole(token)).thenReturn("USER");
        when(jwtUtil.extractStatus(token)).thenReturn(0);

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        
        verify(filterChain).filter(any(ServerWebExchange.class));
        
        // Verify headers were added to the modified request
        verify(filterChain).filter(argThat(ex -> {
            ServerHttpRequest modifiedRequest = ex.getRequest();
            return "user-123".equals(modifiedRequest.getHeaders().getFirst("X-User-Id")) &&
                   "user@example.com".equals(modifiedRequest.getHeaders().getFirst("X-User-Email")) &&
                   "testuser".equals(modifiedRequest.getHeaders().getFirst("X-User-Username")) &&
                   "USER".equals(modifiedRequest.getHeaders().getFirst("X-User-Role")) &&
                   "0".equals(modifiedRequest.getHeaders().getFirst("X-User-Status")) &&
                   "true".equals(modifiedRequest.getHeaders().getFirst("X-Gateway-Validated")) &&
                   modifiedRequest.getHeaders().getFirst("X-Gateway-Timestamp") != null &&
                   modifiedRequest.getHeaders().getFirst("X-Gateway-Signature") != null;
        }));
    }

    @Test
    @DisplayName("Should reject request when token validation throws exception")
    void testFilter_TokenValidationException() {
        // Given
        String token = "some.token";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );

        when(jwtUtil.validateToken(token)).thenThrow(new RuntimeException("Token validation error"));

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should reject request when token missing user info")
    void testFilter_TokenMissingUserInfo() {
        // Given
        String token = createValidToken("user-123", "user@example.com", "testuser", "USER", 0);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );

        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn(null);
        when(jwtUtil.extractEmail(token)).thenReturn("user@example.com");

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should allow public GET novels endpoint")
    void testFilter_PublicGetNovels() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/novels")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public GET novel by ID")
    void testFilter_PublicGetNovelById() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/novels/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public GET categories")
    void testFilter_PublicGetCategories() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/categories")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public GET search")
    void testFilter_PublicGetSearch() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/search")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public POST batch endpoints")
    void testFilter_PublicPostBatch() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/novels/batch/get")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should require authentication for protected endpoint")
    void testFilter_ProtectedEndpoint() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/novels")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should handle HMAC signature generation failure")
    void testFilter_HmacGenerationFailure() {
        // Given
        String token = createValidToken("user-123", "user@example.com", "testuser", "USER", 0);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );

        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn("user-123");
        when(jwtUtil.extractEmail(token)).thenReturn("user@example.com");
        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(jwtUtil.extractRole(token)).thenReturn("USER");
        when(jwtUtil.extractStatus(token)).thenReturn(0);

        // Set invalid secret to cause HMAC failure
        ReflectionTestUtils.setField(filter, "hmacSecret", null);

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should return correct filter order")
    void testGetOrder() {
        // When
        int order = filter.getOrder();

        // Then
        assertEquals(-100, order);
    }

    @Test
    @DisplayName("Should handle path with query parameters")
    void testFilter_PathWithQueryParams() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/novels?page=1&size=10")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public GET reviews endpoint")
    void testFilter_PublicGetReviews() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/reviews")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Should allow public GET chapters by novel")
    void testFilter_PublicGetChaptersByNovel() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chapters/novel/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }

    // Helper method to create valid JWT token
    private String createValidToken(String userId, String email, String username, String role, Integer status) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role)
                .claim("status", status)
                .claim("tokenType", "access")
                .issuer("yushan-platform")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .signWith(signingKey)
                .compact();
    }
}

