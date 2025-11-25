package com.yushan.gateway.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * HMAC Utility for signing gateway-validated requests
 * 
 * This utility generates HMAC signatures to prove that requests
 * are actually from the API Gateway and not forged by attackers.
 */
public class HmacUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Generate HMAC signature for gateway-validated request
     * 
     * Signature includes:
     * - userId
     * - email
     * - role
     * - timestamp (to prevent replay attacks)
     * 
     * @param userId User ID
     * @param email User email
     * @param role User role
     * @param timestamp Request timestamp (milliseconds)
     * @param secret Shared secret key
     * @return Base64-encoded HMAC signature
     * @throws NoSuchAlgorithmException if HMAC algorithm not available
     * @throws InvalidKeyException if secret key is invalid
     */
    public static String generateSignature(String userId, String email, String role, long timestamp, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        // Create message to sign: userId|email|role|timestamp
        String message = String.format("%s|%s|%s|%d", userId, email, role != null ? role : "USER", timestamp);
        
        // Generate HMAC
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        
        byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        
        // Return Base64-encoded signature
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Verify HMAC signature
     * 
     * @param userId User ID
     * @param email User email
     * @param role User role
     * @param timestamp Request timestamp
     * @param signature Signature to verify
     * @param secret Shared secret key
     * @return true if signature is valid, false otherwise
     */
    public static boolean verifySignature(String userId, String email, String role, long timestamp, 
                                          String signature, String secret) {
        try {
            String expectedSignature = generateSignature(userId, email, role, timestamp, secret);
            // Use constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignature, signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

