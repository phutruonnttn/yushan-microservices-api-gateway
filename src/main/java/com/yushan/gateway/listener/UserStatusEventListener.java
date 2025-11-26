package com.yushan.gateway.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yushan.gateway.dto.UserStatusChangedEvent;
import com.yushan.gateway.service.UserBlocklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka Event Listener for User Status Changes
 * 
 * Listens to "user-status-events" topic and updates Redis blocklist in real-time
 * 
 * Flow:
 * 1. User Service publishes UserStatusChangedEvent when user status changes
 * 2. Gateway receives event and updates Redis blocklist:
 *    - If newStatus = SUSPENDED or BANNED → Add to blocklist
 *    - If newStatus = NORMAL → Remove from blocklist
 */
@Slf4j
@Component
public class UserStatusEventListener {
    
    @Autowired
    private UserBlocklistService userBlocklistService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @KafkaListener(topics = "user-status-events", groupId = "api-gateway-user-status-listener")
    public void handleUserStatusChanged(@Payload String payload) {
        try {
            log.debug("Received user status changed event: {}", payload);
            
            // Deserialize event
            UserStatusChangedEvent event = objectMapper.readValue(payload, UserStatusChangedEvent.class);
            
            if (event.getUserId() == null || event.getNewStatus() == null) {
                log.warn("Received invalid user status changed event: {}", payload);
                return;
            }
            
            UUID userId;
            try {
                userId = UUID.fromString(event.getUserId());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid user ID format in event: {}", event.getUserId());
                return;
            }
            
            String newStatus = event.getNewStatus();
            String oldStatus = event.getOldStatus();
            
            log.info("Processing user status change: userId={}, oldStatus={}, newStatus={}", 
                userId, oldStatus, newStatus);
            
            // Update Redis blocklist based on new status
            if ("SUSPENDED".equals(newStatus) || "BANNED".equals(newStatus)) {
                // User is now blocked - add to blocklist
                userBlocklistService.addToBlocklist(userId);
                log.info("Added user {} to blocklist (status: {})", userId, newStatus);
            } else if ("NORMAL".equals(newStatus)) {
                // User is now active - remove from blocklist
                userBlocklistService.removeFromBlocklist(userId);
                log.info("Removed user {} from blocklist (status: {})", userId, newStatus);
            } else {
                log.debug("User status {} does not require blocklist update for user {}", newStatus, userId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process user status changed event: {}", payload, e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process UserStatusChangedEvent", e);
        }
    }
}

