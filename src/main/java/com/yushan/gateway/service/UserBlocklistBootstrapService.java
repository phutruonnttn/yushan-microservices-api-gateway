package com.yushan.gateway.service;

import com.yushan.gateway.client.UserServiceClient;
import com.yushan.gateway.dto.ApiResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Bootstrap Service to sync blocked users from User Service on Gateway startup
 * 
 * Features:
 * - Background sync (doesn't block Gateway startup)
 * - Exponential backoff retry (handles case when Gateway starts before User Service)
 * - Graceful degradation (Gateway works even if sync fails)
 * - Periodic sync (optional, can be enabled for periodic updates)
 * 
 * Retry Strategy:
 * - Attempt 1: Wait 30s
 * - Attempt 2: Wait 60s
 * - Attempt 3: Wait 120s
 * - Attempt 4: Wait 240s
 * - Attempt 5: Wait 480s
 * - Max 5 attempts, then log warning and continue
 */
@Slf4j
@Component
public class UserBlocklistBootstrapService {
    
    @Autowired
    private UserBlocklistService userBlocklistService;
    
    @Autowired
    private UserServiceClient userServiceClient;
    
    @Value("${user-blocklist.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;
    
    @Value("${user-blocklist.bootstrap.max-retry-attempts:5}")
    private int maxRetryAttempts;
    
    /**
     * Bootstrap blocked users on Gateway startup
     * Runs in background thread, doesn't block Gateway startup
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void bootstrapBlocklist() {
        if (!bootstrapEnabled) {
            log.info("User blocklist bootstrap is disabled");
            return;
        }
        
        log.info("Starting user blocklist bootstrap (background thread)...");
        
        // Run bootstrap in background with retry
        CompletableFuture.runAsync(() -> {
            try {
                syncBlocklistWithRetry();
            } catch (Exception e) {
                log.error("Failed to bootstrap user blocklist after all retries", e);
                // Gateway continues to work - graceful degradation
                log.warn("Gateway will continue to work, but blocklist may not be synced. " +
                        "Blocklist will be updated via Kafka events when User Service is available.");
            }
        });
    }
    
    /**
     * Sync blocklist with exponential backoff retry
     */
    private void syncBlocklistWithRetry() {
        List<Duration> retryDelays = List.of(
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            Duration.ofSeconds(240),
            Duration.ofSeconds(480)
        );
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.info("Attempting to sync blocklist from User Service (attempt {}/{})", attempt, maxRetryAttempts);
                
                Set<UUID> blockedUserIds = fetchBlockedUsers();
                
                // Success - sync to Redis
                userBlocklistService.syncBlocklist(blockedUserIds);
                log.info("Successfully synced {} blocked users to Redis blocklist", blockedUserIds.size());
                return; // Success, exit retry loop
                
            } catch (feign.FeignException e) {
                // FeignException covers all Feign errors including ServiceUnavailable, BadGateway, GatewayTimeout
                // User Service not available yet
                if (attempt < maxRetryAttempts) {
                    Duration delay = retryDelays.get(Math.min(attempt - 1, retryDelays.size() - 1));
                    log.warn("User Service not available (attempt {}/{}), retrying in {}...", 
                        attempt, maxRetryAttempts, delay);
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Bootstrap thread interrupted", ie);
                        return;
                    }
                } else {
                    log.error("User Service not available after {} attempts", maxRetryAttempts);
                    throw new RuntimeException("Failed to sync blocklist: User Service unavailable", e);
                }
            } catch (Exception e) {
                log.error("Error syncing blocklist (attempt {}/{})", attempt, maxRetryAttempts, e);
                if (attempt < maxRetryAttempts) {
                    Duration delay = retryDelays.get(Math.min(attempt - 1, retryDelays.size() - 1));
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Bootstrap thread interrupted", ie);
                        return;
                    }
                } else {
                    throw new RuntimeException("Failed to sync blocklist after all retries", e);
                }
            }
        }
    }
    
    /**
     * Fetch blocked users from User Service internal endpoint using Feign Client
     * 
     * Note: This method handles Circuit Breaker fallback detection.
     * If Circuit Breaker is OPEN and fallback is called, the fallback returns:
     * ApiResponse.success("User service temporarily unavailable, using cached blocklist", emptyList)
     * 
     * We detect this fallback response and throw an exception to trigger bootstrap retry logic.
     * 
     * @return Set of blocked user UUIDs
     * @throws RuntimeException if User Service is unavailable (Circuit Breaker fallback detected)
     */
    private Set<UUID> fetchBlockedUsers() {
        log.debug("Fetching blocked users from User Service via Feign Client");
        
        try {
            ApiResponse<List<UUID>> response = userServiceClient.getBlockedUsers();
            
            if (response == null) {
                log.warn("Null response from User Service");
                throw new RuntimeException("User Service returned null response");
            }
            
            // Detect Circuit Breaker fallback response
            // Fallback message: "User service temporarily unavailable, using cached blocklist"
            String message = response.getMessage();
            if (message != null && message.contains("temporarily unavailable")) {
                log.warn("Circuit Breaker fallback detected - User Service is unavailable");
                throw new RuntimeException("User Service unavailable (Circuit Breaker fallback)");
            }
            
            if (response.getData() == null) {
                log.warn("Empty data in response from User Service");
                // This could be legitimate (no blocked users) or fallback
                // Check if it's a fallback by checking message
                if (message != null && message.contains("temporarily unavailable")) {
                    throw new RuntimeException("User Service unavailable (Circuit Breaker fallback)");
                }
                // Legitimate empty list - no blocked users
                return new HashSet<>();
            }
            
            Set<UUID> blockedUserIds = new HashSet<>(response.getData());
            log.debug("Fetched {} blocked users from User Service", blockedUserIds.size());
            return blockedUserIds;
            
        } catch (FeignException e) {
            // Let FeignException propagate so Circuit Breaker can see the failure
            // This allows Circuit Breaker to track failures and open when threshold is reached
            log.error("Failed to fetch blocked users from User Service (FeignException)", e);
            throw e; // Re-throw FeignException to propagate to Circuit Breaker
        } catch (RuntimeException e) {
            // Re-throw our custom exceptions (fallback detection)
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch blocked users from User Service", e);
            throw new RuntimeException("Failed to fetch blocked users", e);
        }
    }
}

