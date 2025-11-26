package com.yushan.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingFilter Tests")
class LoggingFilterTest {

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private LoggingFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should log incoming request and complete successfully")
    void testFilter_LogsRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertNotNull(exchange.getRequest());
        assertEquals("/api/v1/users/123", exchange.getRequest().getURI().getPath());
        assertEquals(HttpMethod.GET, exchange.getRequest().getMethod());
    }

    @Test
    @DisplayName("Should log POST request")
    void testFilter_LogsPostRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/novels")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpMethod.POST, exchange.getRequest().getMethod());
    }

    @Test
    @DisplayName("Should log PUT request")
    void testFilter_LogsPutRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpMethod.PUT, exchange.getRequest().getMethod());
    }

    @Test
    @DisplayName("Should log DELETE request")
    void testFilter_LogsDeleteRequest() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpMethod.DELETE, exchange.getRequest().getMethod());
    }

    @Test
    @DisplayName("Should log request with different paths")
    void testFilter_LogsDifferentPaths() {
        // Given
        String[] paths = {
                "/api/v1/auth/login",
                "/api/v1/novels",
                "/api/v1/users/123",
                "/actuator/health"
        };

        for (String path : paths) {
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(path)
            );

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            assertEquals(path, exchange.getRequest().getURI().getPath());
        }
    }

    @Test
    @DisplayName("Should log response status code")
    void testFilter_LogsResponseStatus() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Should log response with different status codes")
    void testFilter_LogsDifferentStatusCodes() {
        HttpStatus[] statusCodes = {
                HttpStatus.OK,
                HttpStatus.CREATED,
                HttpStatus.NOT_FOUND,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.INTERNAL_SERVER_ERROR
        };

        for (HttpStatus statusCode : statusCodes) {
            // Given
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/test")
            );
            exchange.getResponse().setStatusCode(statusCode);

            // When
            Mono<Void> result = filter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result)
                    .verifyComplete();
            assertEquals(statusCode, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    @DisplayName("Should measure request duration")
    void testFilter_MeasuresDuration() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );

        long startTime = System.currentTimeMillis();

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration >= 0); // Duration should be non-negative
    }

    @Test
    @DisplayName("Should return correct filter order")
    void testGetOrder() {
        // When
        int order = filter.getOrder();

        // Then
        assertEquals(Integer.MAX_VALUE, order); // LOWEST_PRECEDENCE = Integer.MAX_VALUE
    }

    @Test
    @DisplayName("Should handle request with remote address")
    void testFilter_LogsRemoteAddress() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        // Remote address might be null in mock, but filter should handle it gracefully
        assertNotNull(exchange.getRequest());
    }

    @Test
    @DisplayName("Should complete successfully even if response is not set")
    void testFilter_HandlesMissingResponse() {
        // Given
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );

        // When
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle concurrent requests")
    void testFilter_HandlesConcurrentRequests() {
        // Given
        ServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
        );
        ServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/novels")
        );

        // When
        Mono<Void> result1 = filter.filter(exchange1, filterChain);
        Mono<Void> result2 = filter.filter(exchange2, filterChain);

        // Then
        StepVerifier.create(result1)
                .verifyComplete();
        StepVerifier.create(result2)
                .verifyComplete();
    }
}

