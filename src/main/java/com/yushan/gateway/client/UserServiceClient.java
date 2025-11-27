package com.yushan.gateway.client;

import com.yushan.gateway.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Feign Client for User Service
 * Used by Gateway to call User Service internal endpoints
 */
@FeignClient(
    name = "user-service",
    url = "${user-blocklist.bootstrap.user-service-url:http://user-service:8081}",
    fallback = UserServiceClient.UserServiceFallback.class
)
public interface UserServiceClient {

    Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    /**
     * Get list of blocked user IDs (SUSPENDED or BANNED)
     * Internal endpoint - no authentication required
     * 
     * @return List of blocked user UUIDs
     */
    @GetMapping("/api/v1/internal/blocked-users")
    ApiResponse<List<UUID>> getBlockedUsers();

    /**
     * Fallback class for UserServiceClient.
     * This class will be instantiated if the user-service is down or responses with an error.
     */
    @Component
    class UserServiceFallback implements UserServiceClient {
        private static final Logger logger = LoggerFactory.getLogger(UserServiceFallback.class);

        @Override
        public ApiResponse<List<UUID>> getBlockedUsers() {
            logger.error("Circuit breaker opened for user-service. Falling back for getBlockedUsers request.");
            // Return empty list when service is down - gateway will continue to work without blocklist sync
            return ApiResponse.success("User service temporarily unavailable, using cached blocklist", Collections.emptyList());
        }
    }
}

