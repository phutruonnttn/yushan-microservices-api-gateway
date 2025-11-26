package com.yushan.gateway.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HmacUtil Tests")
class HmacUtilTest {

    private static final String SECRET = "test-hmac-secret-key-for-signature-generation-2024";
    private static final String USER_ID = "user-123";
    private static final String EMAIL = "user@example.com";
    private static final String ROLE = "USER";
    private static final long TIMESTAMP = 1234567890L;

    @Test
    @DisplayName("Should generate valid HMAC signature")
    void testGenerateSignature() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // Base64 encoded HMAC-SHA256 should be 44 characters
        assertTrue(signature.length() > 0);
    }

    @Test
    @DisplayName("Should generate same signature for same inputs")
    void testGenerateSignature_Consistency() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature1 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);
        String signature2 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different inputs")
    void testGenerateSignature_DifferentInputs() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature1 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);
        String signature2 = HmacUtil.generateSignature("user-456", EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different timestamps")
    void testGenerateSignature_DifferentTimestamps() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature1 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);
        String signature2 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP + 1000, SECRET);

        // Then
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should handle null role by defaulting to USER")
    void testGenerateSignature_NullRole() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signatureWithNull = HmacUtil.generateSignature(USER_ID, EMAIL, null, TIMESTAMP, SECRET);
        String signatureWithUser = HmacUtil.generateSignature(USER_ID, EMAIL, "USER", TIMESTAMP, SECRET);

        // Then
        assertEquals(signatureWithNull, signatureWithUser);
    }

    @Test
    @DisplayName("Should verify valid signature")
    void testVerifySignature_ValidSignature() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, ROLE, TIMESTAMP, signature, SECRET);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void testVerifySignature_InvalidSignature() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String invalidSignature = "invalid-signature";

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, ROLE, TIMESTAMP, invalidSignature, SECRET);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject signature with wrong secret")
    void testVerifySignature_WrongSecret() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);
        String wrongSecret = "wrong-secret-key";

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, ROLE, TIMESTAMP, signature, wrongSecret);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject signature with wrong user ID")
    void testVerifySignature_WrongUserId() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature("wrong-user-id", EMAIL, ROLE, TIMESTAMP, signature, SECRET);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject signature with wrong email")
    void testVerifySignature_WrongEmail() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, "wrong@email.com", ROLE, TIMESTAMP, signature, SECRET);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject signature with wrong role")
    void testVerifySignature_WrongRole() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, "ADMIN", TIMESTAMP, signature, SECRET);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject signature with wrong timestamp")
    void testVerifySignature_WrongTimestamp() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, ROLE, TIMESTAMP + 1000, signature, SECRET);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should generate Base64 encoded signature")
    void testGenerateSignature_Base64Encoded() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        // Base64 characters: A-Z, a-z, 0-9, +, /, =
        assertTrue(signature.matches("^[A-Za-z0-9+/=]+$"));
    }

    @Test
    @DisplayName("Should handle different roles correctly")
    void testGenerateSignature_DifferentRoles() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signatureUser = HmacUtil.generateSignature(USER_ID, EMAIL, "USER", TIMESTAMP, SECRET);
        String signatureAdmin = HmacUtil.generateSignature(USER_ID, EMAIL, "ADMIN", TIMESTAMP, SECRET);
        String signatureAuthor = HmacUtil.generateSignature(USER_ID, EMAIL, "AUTHOR", TIMESTAMP, SECRET);

        // Then
        assertNotEquals(signatureUser, signatureAdmin);
        assertNotEquals(signatureUser, signatureAuthor);
        assertNotEquals(signatureAdmin, signatureAuthor);
    }

    @Test
    @DisplayName("Should verify signature with null role")
    void testVerifySignature_NullRole() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String signature = HmacUtil.generateSignature(USER_ID, EMAIL, null, TIMESTAMP, SECRET);

        // When
        boolean isValid = HmacUtil.verifySignature(USER_ID, EMAIL, null, TIMESTAMP, signature, SECRET);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should generate deterministic signature")
    void testGenerateSignature_Deterministic() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature1 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);
        String signature2 = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should handle empty string inputs")
    void testGenerateSignature_EmptyInputs() throws NoSuchAlgorithmException, InvalidKeyException {
        // When
        String signature = HmacUtil.generateSignature("", "", "", TIMESTAMP, SECRET);

        // Then
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }

    @Test
    @DisplayName("Should verify signature matches manual HMAC calculation")
    void testVerifySignature_ManualCalculation() throws NoSuchAlgorithmException, InvalidKeyException {
        // Given
        String message = String.format("%s|%s|%s|%d", USER_ID, EMAIL, ROLE, TIMESTAMP);
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        String expectedSignature = java.util.Base64.getEncoder().encodeToString(hmacBytes);

        // When
        String actualSignature = HmacUtil.generateSignature(USER_ID, EMAIL, ROLE, TIMESTAMP, SECRET);

        // Then
        assertEquals(expectedSignature, actualSignature);
    }
}

