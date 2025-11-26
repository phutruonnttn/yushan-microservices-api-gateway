package com.yushan.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private String secret = "test-secret-key-for-jwt-validation-minimum-256-bits-required-for-security";
    private String issuer = "yushan-platform";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "issuer", issuer);
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Should extract user ID from valid token")
    void testExtractUserId() {
        // Given
        String userId = "user-123";
        String token = createToken(userId, "user@example.com", "testuser", "USER", 0, "access");

        // When
        String extractedUserId = jwtUtil.extractUserId(token);

        // Then
        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("Should extract email from valid token")
    void testExtractEmail() {
        // Given
        String email = "user@example.com";
        String token = createToken("user-123", email, "testuser", "USER", 0, "access");

        // When
        String extractedEmail = jwtUtil.extractEmail(token);

        // Then
        assertEquals(email, extractedEmail);
    }

    @Test
    @DisplayName("Should extract username from valid token")
    void testExtractUsername() {
        // Given
        String username = "testuser";
        String token = createToken("user-123", "user@example.com", username, "USER", 0, "access");

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("Should extract role from valid token")
    void testExtractRole() {
        // Given
        String role = "ADMIN";
        String token = createToken("user-123", "user@example.com", "testuser", role, 0, "access");

        // When
        String extractedRole = jwtUtil.extractRole(token);

        // Then
        assertEquals(role, extractedRole);
    }

    @Test
    @DisplayName("Should extract status from valid token")
    void testExtractStatus() {
        // Given
        Integer status = 0; // ACTIVE
        String token = createToken("user-123", "user@example.com", "testuser", "USER", status, "access");

        // When
        Integer extractedStatus = jwtUtil.extractStatus(token);

        // Then
        assertEquals(status, extractedStatus);
    }

    @Test
    @DisplayName("Should return default status 0 when status is null")
    void testExtractStatusWhenNull() {
        // Given
        String token = createTokenWithoutStatus("user-123", "user@example.com", "testuser", "USER", "access");

        // When
        Integer extractedStatus = jwtUtil.extractStatus(token);

        // Then
        assertEquals(0, extractedStatus);
    }

    @Test
    @DisplayName("Should extract token type from valid token")
    void testExtractTokenType() {
        // Given
        String tokenType = "access";
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, tokenType);

        // When
        String extractedTokenType = jwtUtil.extractTokenType(token);

        // Then
        assertEquals(tokenType, extractedTokenType);
    }

    @Test
    @DisplayName("Should extract expiration date from valid token")
    void testExtractExpiration() {
        // Given
        Date expiration = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
        String token = createTokenWithExpiration("user-123", "user@example.com", "testuser", "USER", 0, "access", expiration);

        // When
        Date extractedExpiration = jwtUtil.extractExpiration(token);

        // Then
        assertNotNull(extractedExpiration);
        assertTrue(Math.abs(expiration.getTime() - extractedExpiration.getTime()) < 1000); // Within 1 second
    }

    @Test
    @DisplayName("Should return true for valid non-expired access token")
    void testValidateToken_ValidToken() {
        // Given
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, "access");

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should return false for expired token")
    void testValidateToken_ExpiredToken() {
        // Given
        Date pastDate = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        String token = createTokenWithExpiration("user-123", "user@example.com", "testuser", "USER", 0, "access", pastDate);

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false for refresh token (not access token)")
    void testValidateToken_RefreshToken() {
        // Given
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, "refresh");

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false for token with null token type")
    void testValidateToken_NullTokenType() {
        // Given
        String token = createTokenWithoutTokenType("user-123", "user@example.com", "testuser", "USER", 0);

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false for invalid token signature")
    void testValidateToken_InvalidSignature() {
        // Given
        String invalidSecret = "different-secret-key-for-jwt-validation-minimum-256-bits";
        SecretKey invalidKey = Keys.hmacShaKeyFor(invalidSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-123")
                .claim("userId", "user-123")
                .claim("email", "user@example.com")
                .claim("username", "testuser")
                .claim("role", "USER")
                .claim("status", 0)
                .claim("tokenType", "access")
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .signWith(invalidKey)
                .compact();

        // When
        Boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return false for malformed token")
    void testValidateToken_MalformedToken() {
        // Given
        String malformedToken = "not.a.valid.jwt.token";

        // When
        Boolean isValid = jwtUtil.validateToken(malformedToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should return true for access token")
    void testIsAccessToken_AccessToken() {
        // Given
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, "access");

        // When
        Boolean isAccessToken = jwtUtil.isAccessToken(token);

        // Then
        assertTrue(isAccessToken);
    }

    @Test
    @DisplayName("Should return false for refresh token")
    void testIsAccessToken_RefreshToken() {
        // Given
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, "refresh");

        // When
        Boolean isAccessToken = jwtUtil.isAccessToken(token);

        // Then
        assertFalse(isAccessToken);
    }

    @Test
    @DisplayName("Should return false for token with null token type")
    void testIsAccessToken_NullTokenType() {
        // Given
        String token = createTokenWithoutTokenType("user-123", "user@example.com", "testuser", "USER", 0);

        // When
        Boolean isAccessToken = jwtUtil.isAccessToken(token);

        // Then
        assertFalse(isAccessToken);
    }

    @Test
    @DisplayName("Should return true for non-expired token")
    void testIsTokenExpired_NotExpired() {
        // Given
        String token = createToken("user-123", "user@example.com", "testuser", "USER", 0, "access");

        // When
        Boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertFalse(isExpired);
    }

    @Test
    @DisplayName("Should return true for expired token")
    void testIsTokenExpired_Expired() {
        // Given
        Date pastDate = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        String token = createTokenWithExpiration("user-123", "user@example.com", "testuser", "USER", 0, "access", pastDate);

        // When
        Boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertTrue(isExpired);
    }

    @Test
    @DisplayName("Should extract all claims from token")
    void testExtractAllClaims() {
        // Given
        String userId = "user-123";
        String email = "user@example.com";
        String username = "testuser";
        String role = "ADMIN";
        Integer status = 1;
        String token = createToken(userId, email, username, role, status, "access");

        // When
        Claims claims = jwtUtil.extractAllClaims(token);

        // Then
        assertNotNull(claims);
        assertEquals(userId, claims.get("userId", String.class));
        assertEquals(email, claims.get("email", String.class));
        assertEquals(username, claims.get("username", String.class));
        assertEquals(role, claims.get("role", String.class));
        assertEquals(status, claims.get("status", Integer.class));
        assertEquals("access", claims.get("tokenType", String.class));
    }

    // Helper methods to create tokens
    private String createToken(String userId, String email, String username, String role, Integer status, String tokenType) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role)
                .claim("status", status)
                .claim("tokenType", tokenType)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .signWith(signingKey)
                .compact();
    }

    private String createTokenWithExpiration(String userId, String email, String username, String role, Integer status, String tokenType, Date expiration) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role)
                .claim("status", status)
                .claim("tokenType", tokenType)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private String createTokenWithoutStatus(String userId, String email, String username, String role, String tokenType) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .signWith(signingKey)
                .compact();
    }

    private String createTokenWithoutTokenType(String userId, String email, String username, String role, Integer status) {
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role)
                .claim("status", status)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .signWith(signingKey)
                .compact();
    }
}

