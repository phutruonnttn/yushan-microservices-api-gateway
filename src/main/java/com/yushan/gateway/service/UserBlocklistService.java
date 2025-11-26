package com.yushan.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to manage user blocklist in Redis
 * 
 * Uses Redis Set to store blocked user IDs (SUSPENDED or BANNED users)
 * Key: "user:blocklist"
 * Value: Set of user UUIDs (as strings)
 */
@Slf4j
@Service
public class UserBlocklistService {
    
    private static final String BLOCKLIST_KEY = "user:blocklist";
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * Check if user is in blocklist
     * 
     * @param userId User UUID
     * @return true if user is blocked (SUSPENDED or BANNED), false otherwise
     */
    public boolean isBlocked(UUID userId) {
        if (userId == null) {
            return false;
        }
        Boolean isMember = redisTemplate.opsForSet().isMember(BLOCKLIST_KEY, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }
    
    /**
     * Add user to blocklist
     * 
     * @param userId User UUID to block
     */
    public void addToBlocklist(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForSet().add(BLOCKLIST_KEY, userId.toString());
        log.debug("Added user {} to blocklist", userId);
    }
    
    /**
     * Remove user from blocklist (when status changes to NORMAL)
     * 
     * @param userId User UUID to unblock
     */
    public void removeFromBlocklist(UUID userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForSet().remove(BLOCKLIST_KEY, userId.toString());
        log.debug("Removed user {} from blocklist", userId);
    }
    
    /**
     * Sync blocklist with list of blocked user IDs
     * Used during bootstrap to sync existing blocked users
     * 
     * @param blockedUserIds List of blocked user UUIDs
     */
    public void syncBlocklist(Set<UUID> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            log.info("No blocked users to sync, clearing blocklist");
            redisTemplate.delete(BLOCKLIST_KEY);
            return;
        }
        
        // Convert UUIDs to strings
        Set<String> blockedUserIdsStr = blockedUserIds.stream()
            .map(UUID::toString)
            .collect(Collectors.toSet());
        
        // Clear existing blocklist and add all blocked users
        redisTemplate.delete(BLOCKLIST_KEY);
        if (!blockedUserIdsStr.isEmpty()) {
            redisTemplate.opsForSet().add(BLOCKLIST_KEY, blockedUserIdsStr.toArray(new String[0]));
        }
        
        log.info("Synced {} blocked users to Redis blocklist", blockedUserIds.size());
    }
    
    /**
     * Get current blocklist size
     * 
     * @return Number of users in blocklist
     */
    public long getBlocklistSize() {
        Long size = redisTemplate.opsForSet().size(BLOCKLIST_KEY);
        return size != null ? size : 0;
    }
}

