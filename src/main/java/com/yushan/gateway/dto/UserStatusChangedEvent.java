package com.yushan.gateway.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Status Changed Event DTO
 * Matches the structure from User Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusChangedEvent {
    private String userId;
    private String oldStatus;  // Can be null if user is newly created
    private String newStatus;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}

