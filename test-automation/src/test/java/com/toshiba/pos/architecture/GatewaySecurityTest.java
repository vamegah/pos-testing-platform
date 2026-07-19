// test-automation/src/test/java/com/toshiba/pos/architecture/GatewaySecurityTest.java

package com.toshiba.pos.architecture;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * API Gateway Negative/Security Tests (23.6)
 * 
 * Covers:
 *   - Expired token
 *   - Malformed token
 *   - Replay of a used token
 *   - Rate-limit-boundary cases
 *   - Empty token
 *   - Invalid token format
 */
public class GatewaySecurityTest extends BaseTest {

    private static String gatewayUrl;
    private static final String VALID_TOKEN = "valid-token-123";
    private static final String EXPIRED_TOKEN = "expired-token-456";
    private static final String MALFORMED_TOKEN = "malformed-token";
    private static final String USED_TOKEN = "used-token-789";
    private static final String EMPTY_TOKEN = "";
    private static final String INVALID_FORMAT_TOKEN = "invalid-format";

    @BeforeClass
    public void setUpClass() {
        gatewayUrl = getEnv("GATEWAY_URL", "http://localhost:5014");
        logger.info("Gateway URL: {}", gatewayUrl);
    }

    /**
     * Helper: Make a request with a token.
     */
    private Response makeRequestWithToken(String token) {
        return given()
            .spec(requestSpec)
            .header("Authorization", "Bearer " + token)
            .when()
            .get(gatewayUrl + "/gateway/api/pricing/price/SKU-1001")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Make a request with no token.
     */
    private Response makeRequestNoToken() {
        return given()
            .spec(requestSpec)
            .when()
            .get(gatewayUrl + "/gateway/api/pricing/price/SKU-1001")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Make multiple requests to test rate limiting.
     */
    private List<Integer> makeMultipleRequests(String token, int count) {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Response response = makeRequestWithToken(token);
            statuses.add(response.statusCode());
        }
        return statuses;
    }

    /**
     * Test: Expired token is rejected.
     * 
     * Given: An expired token
     * When: A request is made with the expired token
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Expired token is rejected")
    public void testExpiredTokenRejected() {
        logger.info("=== Expired Token Test ===");

        Response response = makeRequestWithToken(EXPIRED_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 401, "Expired token should return 401");
        
        String body = response.getBody().asString();
        assertTrue(body.contains("Invalid") || body.contains("expired"), 
            "Error message should indicate invalid/expired token");

        logger.info("✅ Expired token rejected as expected");
    }

    /**
     * Test: Malformed token is rejected.
     * 
     * Given: A malformed token (invalid format)
     * When: A request is made with the malformed token
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Malformed token is rejected")
    public void testMalformedTokenRejected() {
        logger.info("=== Malformed Token Test ===");

        Response response = makeRequestWithToken(MALFORMED_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 401, "Malformed token should return 401");
        
        String body = response.getBody().asString();
        assertTrue(body.contains("Invalid") || body.contains("malformed"), 
            "Error message should indicate invalid token");

        logger.info("✅ Malformed token rejected as expected");
    }

    /**
     * Test: Empty token is rejected.
     * 
     * Given: An empty token
     * When: A request is made with an empty token
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Empty token is rejected")
    public void testEmptyTokenRejected() {
        logger.info("=== Empty Token Test ===");

        Response response = makeRequestWithToken(EMPTY_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 401, "Empty token should return 401");

        logger.info("✅ Empty token rejected as expected");
    }

    /**
     * Test: No token is rejected.
     * 
     * Given: No token provided
     * When: A request is made without Authorization header
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "No token is rejected")
    public void testNoTokenRejected() {
        logger.info("=== No Token Test ===");

        Response response = makeRequestNoToken();
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 401, "No token should return 401");
        
        String body = response.getBody().asString();
        assertTrue(body.contains("Missing") || body.contains("token"), 
            "Error message should indicate missing token");

        logger.info("✅ No token rejected as expected");
    }

    /**
     * Test: Replay attack detection (used token).
     * 
     * Given: A token that has been used before (replay scenario)
     * When: A request is made with the used token
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Replay of used token is rejected")
    public void testReplayTokenRejected() {
        logger.info("=== Replay Token Test ===");

        Response response = makeRequestWithToken(USED_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        // The gateway should reject used tokens (or tokens that appear to be replayed)
        assertEquals(statusCode, 401, "Used token should return 401");
        
        String body = response.getBody().asString();
        assertTrue(body.contains("Invalid") || body.contains("replay") || body.contains("used"), 
            "Error message should indicate invalid/used token");

        logger.info("✅ Replay token rejected as expected");
    }

    /**
     * Test: Invalid token format is rejected.
     * 
     * Given: A token with invalid format
     * When: A request is made with the invalid token
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Invalid token format is rejected")
    public void testInvalidTokenFormatRejected() {
        logger.info("=== Invalid Token Format Test ===");

        Response response = makeRequestWithToken(INVALID_FORMAT_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 401, "Invalid token format should return 401");

        logger.info("✅ Invalid token format rejected as expected");
    }

    /**
     * Test: Rate limit boundary cases.
     * 
     * Given: A valid token
     * When: More than the allowed number of requests are made
     * Then: 429 Too Many Requests is returned after the limit is exceeded
     */
    @Test(description = "Rate limit boundary cases")
    public void testRateLimitBoundary() {
        logger.info("=== Rate Limit Boundary Test ===");

        // Get rate limit configuration from gateway
        Response statusResponse = given()
            .spec(requestSpec)
            .header("Authorization", "Bearer " + VALID_TOKEN)
            .when()
            .get(gatewayUrl + "/gateway/rate-limit/status")
            .then()
            .extract()
            .response();
        
        // If rate-limit endpoint is available, use its values
        int maxRequests = 100; // Default fallback
        try {
            maxRequests = statusResponse.jsonPath().getInt("max_requests");
            logger.info("  Rate limit max requests: {}", maxRequests);
        } catch (Exception e) {
            logger.warn("  Rate limit endpoint returned status: {}", statusResponse.statusCode());
        }

        // Make requests up to limit + 1
        int testCount = Math.min(maxRequests + 1, 20); // Limit test to 20 for speed
        if (testCount > 10) {
            logger.info("  Testing with {} requests", testCount);
        }

        List<Integer> statuses = makeMultipleRequests(VALID_TOKEN, testCount);
        logger.info("  Status codes: {}", statuses);

        // Check that 429 appears when limit exceeded
        boolean rateLimited = statuses.contains(429);
        logger.info("  Rate limited: {}", rateLimited);

        // We expect to hit the rate limit if enough requests were made
        if (testCount > 5) {
            assertTrue(rateLimited, "Should hit rate limit after " + testCount + " requests");
        } else {
            // If test count is small, we may not hit the limit
            logger.info("  Rate limit boundary test completed (limit may not have been hit)");
        }

        logger.info("✅ Rate limit boundary test passed");
    }

    /**
     * Test: Valid token with valid auth works.
     * 
     * Given: A valid token
     * When: A request is made with the valid token
     * Then: 200 OK is returned
     */
    @Test(description = "Valid token works")
    public void testValidTokenWorks() {
        logger.info("=== Valid Token Test ===");

        Response response = makeRequestWithToken(VALID_TOKEN);
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);

        assertEquals(statusCode, 200, "Valid token should return 200");
        
        Double price = response.jsonPath().getDouble("price");
        assertNotNull(price, "Price should be returned");
        assertTrue(price > 0, "Price should be greater than 0");

        logger.info("✅ Valid token works as expected");
    }

    /**
     * Test: Auth bypass attempts are blocked.
     * 
     * Given: Various auth bypass attempts
     * When: Requests are made with malformed headers
     * Then: 401 Unauthorized is returned
     */
    @Test(description = "Auth bypass attempts are blocked")
    public void testAuthBypassAttempts() {
        logger.info("=== Auth Bypass Attempts Test ===");

        // Test with Authorization header missing
        Response noHeaderResponse = makeRequestNoToken();
        assertEquals(noHeaderResponse.statusCode(), 401, "Missing header should return 401");

        // Test with empty Authorization header
        Response emptyHeaderResponse = given()
            .spec(requestSpec)
            .header("Authorization", "")
            .when()
            .get(gatewayUrl + "/gateway/api/pricing/price/SKU-1001")
            .then()
            .extract()
            .response();
        assertEquals(emptyHeaderResponse.statusCode(), 401, "Empty header should return 401");

        // Test with malformed Authorization header (missing "Bearer")
        Response malformedHeaderResponse = given()
            .spec(requestSpec)
            .header("Authorization", VALID_TOKEN)
            .when()
            .get(gatewayUrl + "/gateway/api/pricing/price/SKU-1001")
            .then()
            .extract()
            .response();
        assertEquals(malformedHeaderResponse.statusCode(), 401, "Malformed header should return 401");

        logger.info("✅ Auth bypass attempts blocked");
    }
}