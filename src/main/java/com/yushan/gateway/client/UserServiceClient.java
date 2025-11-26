package com.yushan.gateway.client;

import com.yushan.gateway.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

/**
 * Feign Client for User Service
 * Used by Gateway to call User Service internal endpoints
 */
@FeignClient(
    name = "user-service",
    url = "${user-blocklist.bootstrap.user-service-url:http://user-service:8081}"
)
public interface UserServiceClient {

    /**
     * Get list of blocked user IDs (SUSPENDED or BANNED)
     * Internal endpoint - no authentication required
     * 
     * @return List of blocked user UUIDs
     */
    @GetMapping("/api/v1/internal/blocked-users")
    ApiResponse<List<UUID>> getBlockedUsers();
}

