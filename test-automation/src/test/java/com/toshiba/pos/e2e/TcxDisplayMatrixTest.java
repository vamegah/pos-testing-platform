// test-automation/src/test/java/com/toshiba/pos/e2e/TcxDisplayMatrixTest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * TCx® Display E2E Matrix Test
 * 
 * Iterates the supported size/orientation matrix from the TCx Display profile.
 * 
 * Test Cases:
 *   1. Validates each supported size/orientation combo is accepted
 *   2. Validates a known-bad combo is rejected
 *   3. Data-driven test over the matrix
 */
public class TcxDisplayMatrixTest extends BaseTest {

    // TCx Display simulator URL
    private static String displayUrl;

    @BeforeClass
    public void setUpClass() {
        displayUrl = getEnv("TCX_DISPLAY_URL", "http://localhost:5006");
        logger.info("=== TCx Display Matrix E2E Test Initialized ===");
        logger.info("  Display URL: {}", displayUrl);
        logger.info("================================================");
    }

    /**
     * Helper: Validate a display configuration.
     */
    private Response validateDisplay(double size, String orientation, int width, int height) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("size_inches", size);
        payload.put("orientation", orientation);
        Map<String, Integer> resolution = new HashMap<>();
        resolution.put("width", width);
        resolution.put("height", height);
        payload.put("resolution", resolution);
        
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(displayUrl + "/tcx-display/render/validate")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get display capabilities.
     */
    private Response getCapabilities() {
        return given()
            .spec(requestSpec)
            .when()
            .get(displayUrl + "/tcx-display/capabilities")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get display state.
     */
    private Response getDisplayState() {
        return given()
            .spec(requestSpec)
            .when()
            .get(displayUrl + "/tcx-display/state")
            .then()
            .extract()
            .response();
    }

    /**
     * Data Provider: Supported combinations from the matrix.
     * 
     * Matrix from Phase 12.7:
     *   - 10.1": landscape, portrait
     *   - 12.1": landscape, portrait
     *   - 15.6": landscape, portrait
     *   - 21.5": landscape only
     * 
     * Also includes the bad combo for 21.5" portrait.
     */
    @DataProvider(name = "displayCombinations")
    public Object[][] displayCombinations() {
        return new Object[][] {
            // Valid combinations
            {"10.1", 10.1, "landscape", 1280, 800, true},
            {"10.1", 10.1, "portrait", 800, 1280, true},
            {"12.1", 12.1, "landscape", 1280, 800, true},
            {"12.1", 12.1, "portrait", 800, 1280, true},
            {"15.6", 15.6, "landscape", 1920, 1080, true},
            {"15.6", 15.6, "portrait", 1080, 1920, true},
            {"21.5", 21.5, "landscape", 1920, 1080, true},
            // Invalid combination (21.5" portrait is NOT supported)
            {"21.5", 21.5, "portrait", 1080, 1920, false}
        };
    }

    /**
     * Test: TCx Display — Validate Matrix Combinations
     * 
     * Given: A display configuration
     * When: The configuration is validated
     * Then: Valid combos are accepted, invalid combos are rejected
     */
    @Test(description = "TCx Display — Validate matrix combinations", 
          dataProvider = "displayCombinations")
    public void testDisplayCombination(String name, double size, String orientation, 
                                      int width, int height, boolean expectedValid) {
        logger.info("=== TCx Display: Testing Matrix Combination ===");
        logger.info("  Name: {}", name);
        logger.info("  Size: {}in, Orientation: {}, Resolution: {}x{}", 
            size, orientation, width, height);
        logger.info("  Expected valid: {}", expectedValid);
        
        // Validate the display configuration
        Response response = validateDisplay(size, orientation, width, height);
        
        if (expectedValid) {
            assertEquals(response.statusCode(), 200, 
                "Valid combination should be accepted");
            Boolean valid = response.jsonPath().getBoolean("valid");
            assertTrue(valid, "Combination should be valid");
            
            // Verify the response contains the configuration
            Double responseSize = response.jsonPath().getDouble("size");
            String responseOrientation = response.jsonPath().getString("orientation");
            
            assertEquals(responseSize, size, 0.01, "Size should match");
            assertEquals(responseOrientation, orientation, "Orientation should match");
            
            logger.info("  ✅ Valid combination accepted: {} {} {}x{}", 
                size, orientation, width, height);
        } else {
            assertNotEquals(response.statusCode(), 200, 
                "Invalid combination should be rejected");
            
            String reason = response.jsonPath().getString("reason");
            logger.info("  ❌ Invalid combination rejected: {}", reason);
            assertNotNull(reason, "Reason should be provided for rejection");
            assertTrue(reason.contains("not supported"), 
                "Reason should indicate not supported");
            
            logger.info("  ✅ Invalid combination rejected as expected");
            logger.info("  Reason: {}", reason);
        }
        
        // Verify the display state was updated (for valid combos)
        if (expectedValid) {
            Response stateResponse = getDisplayState();
            double currentSize = stateResponse.jsonPath().getDouble("current_size");
            String currentOrientation = stateResponse.jsonPath().getString("current_orientation");
            
            assertEquals(currentSize, size, 0.01, "State should reflect new size");
            assertEquals(currentOrientation, orientation, "State should reflect new orientation");
        }
    }

    /**
     * Test: TCx Display — Get Capabilities
     * 
     * Given: The display capabilities endpoint
     * When: It is called
     * Then: The matrix is returned correctly
     */
    @Test(description = "TCx Display — Get capabilities matrix")
    public void testGetCapabilities() {
        logger.info("=== TCx Display: Get Capabilities ===");
        
        Response response = getCapabilities();
        assertEquals(response.statusCode(), 200, "Should get capabilities");
        
        // Verify supported sizes
        List<Double> supportedSizes = response.jsonPath().getList("supported_sizes");
        logger.info("  Supported sizes: {}", supportedSizes);
        assertNotNull(supportedSizes, "Supported sizes should not be null");
        assertTrue(supportedSizes.contains(10.1), "Should include 10.1");
        assertTrue(supportedSizes.contains(12.1), "Should include 12.1");
        assertTrue(supportedSizes.contains(15.6), "Should include 15.6");
        assertTrue(supportedSizes.contains(21.5), "Should include 21.5");
        assertEquals(supportedSizes.size(), 4, "Should have 4 sizes");
        
        // Verify supported orientations
        List<String> orientations = response.jsonPath().getList("supported_orientations");
        logger.info("  Supported orientations: {}", orientations);
        assertTrue(orientations.contains("landscape"), "Should include landscape");
        assertTrue(orientations.contains("portrait"), "Should include portrait");
        
        // Verify size-orientation matrix
        Map<String, List<String>> matrix = response.jsonPath().getMap("size_orientation_matrix");
        logger.info("  Size-Orientation Matrix:");
        for (Map.Entry<String, List<String>> entry : matrix.entrySet()) {
            logger.info("    {}: {}", entry.getKey(), entry.getValue());
        }
        
        // Verify 21.5" only supports landscape
        List<String> twentyOneFive = matrix.get("21.5");
        assertNotNull(twentyOneFive, "21.5 should be in matrix");
        assertTrue(twentyOneFive.contains("landscape"), "21.5 should support landscape");
        assertFalse(twentyOneFive.contains("portrait"), "21.5 should NOT support portrait");
        
        logger.info("✅ TCx Display: Capabilities retrieved successfully");
    }

    /**
     * Test: TCx Display — Validation History
     * 
     * Given: Multiple validation requests
     * When: The history endpoint is called
     * Then: All validations are logged
     */
    @Test(description = "TCx Display — Validation history", dependsOnMethods = "testDisplayCombination")
    public void testValidationHistory() {
        logger.info("=== TCx Display: Validation History ===");
        
        // Make a few validation requests
        validateDisplay(15.6, "landscape", 1920, 1080);
        validateDisplay(10.1, "portrait", 800, 1280);
        validateDisplay(21.5, "landscape", 1920, 1080);
        
        // Get history
        Response historyResponse = given()
            .spec(requestSpec)
            .when()
            .get(displayUrl + "/tcx-display/validation/history")
            .then()
            .extract()
            .response();
        
        assertEquals(historyResponse.statusCode(), 200, "Should get history");
        
        List<Map<String, Object>> history = historyResponse.jsonPath().getList("history");
        Integer count = historyResponse.jsonPath().getInt("count");
        
        logger.info("  Validation history count: {}", count);
        assertNotNull(history, "History should not be null");
        assertTrue(count >= 3, "Should have at least 3 validation entries");
        
        // Verify last entry
        if (!history.isEmpty()) {
            Map<String, Object> lastEntry = history.get(history.size() - 1);
            Boolean valid = (Boolean) lastEntry.get("valid");
            logger.info("  Last entry valid: {}", valid);
            assertNotNull(valid, "Valid flag should be present");
        }
        
        logger.info("✅ TCx Display: Validation history verified successfully");
    }

    /**
     * Test: TCx Display — Invalid Resolution for Orientation
     * 
     * Given: A resolution that doesn't match the orientation
     * When: The configuration is validated
     * Then: It is rejected
     */
    @Test(description = "TCx Display — Invalid resolution for orientation")
    public void testInvalidResolutionForOrientation() {
        logger.info("=== TCx Display: Invalid Resolution for Orientation ===");
        
        // Try a landscape resolution with portrait orientation
        // 1280x800 is landscape, but we request portrait
        double size = 15.6;
        String orientation = "portrait";
        int width = 1280;
        int height = 800;
        
        logger.info("  Configuration: {}in, {}, {}x{}", size, orientation, width, height);
        
        Response response = validateDisplay(size, orientation, width, height);
        
        // Should be rejected (invalid resolution for orientation)
        assertNotEquals(response.statusCode(), 200, 
            "Invalid resolution for orientation should be rejected");
        
        String reason = response.jsonPath().getString("reason");
        logger.info("  Rejection reason: {}", reason);
        assertNotNull(reason, "Reason should be provided");
        assertTrue(reason.contains("resolution") || reason.contains("supported"), 
            "Reason should mention resolution or support");
        
        logger.info("✅ TCx Display: Invalid resolution for orientation rejected");
    }

    /**
     * Test: TCx Display — Transaction with Display Validation
     * 
     * Given: A transaction request with display configuration
     * When: The transaction is processed
     * Then: The display is validated before processing
     */
    @Test(description = "TCx Display — Transaction with display validation")
    public void testTransactionWithDisplayValidation() {
        logger.info("=== TCx Display: Transaction with Display Validation ===");
        
        // Step 1: Build a transaction with a valid display config
        logger.info("Step 1: Sending transaction with valid display config");
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", List.of(Map.of("sku", "SKU-1001", "quantity", 1)));
        payload.put("region", "CA");
        payload.put("payment", Map.of("card_number", "4111111111111111"));
        payload.put("size_inches", 15.6);
        payload.put("orientation", "landscape");
        payload.put("resolution", Map.of("width", 1920, "height", 1080));
        
        Response validResponse = given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(displayUrl + "/tcx-display/transaction")
            .then()
            .extract()
            .response();
        
        assertEquals(validResponse.statusCode(), 200, "Valid display should be accepted");
        String status = validResponse.jsonPath().getString("status");
        logger.info("  Valid display transaction status: {}", status);
        assertEquals(status, "display_validated", "Status should be display_validated");
        
        // Step 2: Build a transaction with an invalid display config
        logger.info("Step 2: Sending transaction with invalid display config (21.5\" portrait)");
        payload.put("size_inches", 21.5);
        payload.put("orientation", "portrait");
        payload.put("resolution", Map.of("width", 1080, "height", 1920));
        
        Response invalidResponse = given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(displayUrl + "/tcx-display/transaction")
            .then()
            .extract()
            .response();
        
        // Should fail at display validation
        int statusCode = invalidResponse.statusCode();
        logger.info("  Invalid display transaction status code: {}", statusCode);
        assertNotEquals(statusCode, 200, "Invalid display should be rejected");
        
        String errorStatus = invalidResponse.jsonPath().getString("status");
        String errorStep = invalidResponse.jsonPath().getString("step");
        logger.info("  Error status: {}, step: {}", errorStatus, errorStep);
        
        assertEquals(errorStatus, "failed", "Transaction should fail");
        assertEquals(errorStep, "display_validation", "Should fail at display validation");
        
        logger.info("✅ TCx Display: Transaction with display validation completed");
    }
}