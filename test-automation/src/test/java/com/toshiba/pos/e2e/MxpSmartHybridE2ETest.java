// test-automation/src/test/java/com/toshiba/pos/e2e/MxpSmartHybridE2ETest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * MxP™ SMART | hybrid E2E Test
 * 
 * Validates the complete flow for the MxP SMART Hybrid:
 *   1. Start in self-service mode
 *   2. Switch to assisted mode mid-transaction
 *   3. Complete the sale
 * 
 * Verifies that the basket state is preserved during the mode switch.
 */
public class MxpSmartHybridE2ETest extends BaseTest {

    // MxP SMART Hybrid simulator URL
    private static String hybridUrl;

    // Test data
    private static final String TEST_REGION = "CA";
    private static final String TEST_CARD = "4111111111111111";
    private static final String[] TEST_SKUS = {"SKU-1001", "SKU-1002", "SKU-1005"};

    @BeforeClass
    public void setUpClass() {
        hybridUrl = getEnv("SMART_HYBRID_URL", "http://localhost:5002");
        logger.info("=== MxP SMART Hybrid E2E Test Initialized ===");
        logger.info("  Hybrid URL: {}", hybridUrl);
        logger.info("=============================================");
    }

    /**
     * Helper: Build a test basket with multiple items.
     */
    private List<Map<String, Object>> buildTestBasket() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String sku : TEST_SKUS) {
            Map<String, Object> item = new HashMap<>();
            item.put("sku", sku);
            item.put("quantity", 1);
            items.add(item);
        }
        return items;
    }

    /**
     * Helper: Get current mode.
     */
    private Response getMode() {
        return given()
            .spec(requestSpec)
            .when()
            .get(hybridUrl + "/mxp-smart-hybrid/mode")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Toggle mode.
     */
    private Response toggleMode(String targetMode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", targetMode);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(hybridUrl + "/mxp-smart-hybrid/toggle")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Process a transaction.
     */
    private Response processTransaction(List<Map<String, Object>> items, String region, 
                                       Map<String, Object> payment, boolean override) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("region", region);
        payload.put("payment", payment);
        if (override) {
            payload.put("override", true);
        }
        
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(hybridUrl + "/mxp-smart-hybrid/transaction")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get mode history.
     */
    private Response getModeHistory() {
        return given()
            .spec(requestSpec)
            .when()
            .get(hybridUrl + "/mxp-smart-hybrid/mode/event")
            .then()
            .extract()
            .response();
    }

    /**
     * Test: MxP SMART Hybrid — Start Self-Service, Switch to Assisted, Complete Sale
     * 
     * Given: A customer starts a transaction in self-service mode
     * When: They need assistance and switch to assisted mode
     * Then: The basket state is preserved and the sale completes successfully
     */
    @Test(description = "MxP SMART Hybrid — Start in self-service, switch to assisted, complete sale")
    public void testSelfServiceToAssistedModeSwitch() {
        logger.info("=== MxP SMART Hybrid: Self-Service → Assisted Mode Switch ===");
        
        // Step 1: Ensure we start in self-service mode
        logger.info("Step 1: Setting initial mode to self-service");
        Response modeResponse = toggleMode("self_service");
        assertEquals(modeResponse.statusCode(), 200, "Mode toggle should succeed");
        assertEquals(modeResponse.jsonPath().getString("current_mode"), "self_service", 
            "Mode should be self_service");
        logger.info("  Initial mode: self_service");
        
        // Step 2: Build basket and start transaction in self-service mode
        logger.info("Step 2: Building basket in self-service mode");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        for (Map<String, Object> item : items) {
            logger.info("    - {}", item.get("sku"));
        }
        
        // Step 3: Attempt to process transaction in self-service mode (without override)
        logger.info("Step 3: Processing transaction in self-service mode (without override)");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response initialResponse = processTransaction(items, TEST_REGION, payment, false);
        
        // In self-service mode, the transaction should process normally
        // (or we can capture the state before switching)
        logger.info("  Initial transaction status: {}", 
            initialResponse.jsonPath().getString("status"));
        
        // Store the initial state for later comparison
        Double initialSubtotal = initialResponse.jsonPath().getDouble("subtotal");
        logger.info("  Initial subtotal: ${}", initialSubtotal);
        
        // Step 4: Switch to assisted mode mid-transaction
        logger.info("Step 4: Switching to assisted mode mid-transaction");
        Response switchResponse = toggleMode("assisted");
        assertEquals(switchResponse.statusCode(), 200, "Mode switch should succeed");
        
        String currentMode = switchResponse.jsonPath().getString("current_mode");
        Integer rotationDegrees = switchResponse.jsonPath().getInt("rotation_degrees");
        assertEquals(currentMode, "assisted", "Mode should be assisted");
        assertEquals(rotationDegrees, Integer.valueOf(180), "Rotation should be 180 degrees");
        logger.info("  Mode switched to: {} (rotated {}°)", currentMode, rotationDegrees);
        
        // Verify mode history includes the switch
        Response historyResponse = getModeHistory();
        List<Map<String, Object>> history = historyResponse.jsonPath().getList("history");
        logger.info("  Mode history entries: {}", history.size());
        assertTrue(history.size() >= 2, "Mode history should track mode changes");
        
        // Step 5: Complete the transaction in assisted mode
        logger.info("Step 5: Completing transaction in assisted mode");
        payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response finalResponse = processTransaction(items, TEST_REGION, payment, true);
        
        // Verify the transaction completes successfully
        assertEquals(finalResponse.statusCode(), 200, "Transaction should succeed in assisted mode");
        
        String status = finalResponse.jsonPath().getString("status");
        String mode = finalResponse.jsonPath().getString("mode");
        Double finalSubtotal = finalResponse.jsonPath().getDouble("subtotal");
        Double total = finalResponse.jsonPath().getDouble("total");
        String paymentStatus = finalResponse.jsonPath().getString("payment.status");
        Boolean overrideApplied = finalResponse.jsonPath().getBoolean("override_applied");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Mode: {}", mode);
        logger.info("  Subtotal: ${}", finalSubtotal);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        logger.info("  Override applied: {}", overrideApplied);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(mode, "assisted", "Transaction should be in assisted mode");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertNotNull(total, "Total should not be null");
        assertTrue(total > 0, "Total should be greater than 0");
        assertEquals(finalSubtotal, initialSubtotal, 0.01, 
            "Subtotal should be preserved during mode switch");
        
        logger.info("✅ MxP SMART Hybrid: Self-Service → Assisted mode switch completed successfully");
        logger.info("  Basket preserved: ${}", finalSubtotal);
        logger.info("  Total: ${}", total);
    }

    /**
     * Test: MxP SMART Hybrid — Assisted Mode Override Feature
     * 
     * Given: A transaction in assisted mode
     * When: An override action is performed
     * Then: The override is applied successfully
     */
    @Test(description = "MxP SMART Hybrid — Assisted mode override feature")
    public void testAssistedModeOverride() {
        logger.info("=== MxP SMART Hybrid: Assisted Mode Override ===");
        
        // Step 1: Switch to assisted mode
        logger.info("Step 1: Switching to assisted mode");
        Response modeResponse = toggleMode("assisted");
        assertEquals(modeResponse.statusCode(), 200, "Mode toggle should succeed");
        assertEquals(modeResponse.jsonPath().getString("current_mode"), "assisted", 
            "Mode should be assisted");
        
        // Step 2: Build basket
        logger.info("Step 2: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        
        // Step 3: Process transaction with override
        logger.info("Step 3: Processing transaction with override in assisted mode");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response transactionResponse = processTransaction(items, TEST_REGION, payment, true);
        
        // Verify override was applied
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        Boolean overrideApplied = transactionResponse.jsonPath().getBoolean("override_applied");
        String mode = transactionResponse.jsonPath().getString("mode");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Mode: {}", mode);
        logger.info("  Override applied: {}", overrideApplied);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(mode, "assisted", "Transaction should be in assisted mode");
        assertTrue(overrideApplied, "Override should be applied in assisted mode");
        
        logger.info("✅ MxP SMART Hybrid: Assisted mode override completed successfully");
    }

    /**
     * Test: MxP SMART Hybrid — Self-Service Mode Restriction
     * 
     * Given: A transaction in self-service mode
     * When: An override is attempted
     * Then: The override is rejected (not allowed in self-service mode)
     */
    @Test(description = "MxP SMART Hybrid — Self-service mode rejects override")
    public void testSelfServiceOverrideRejected() {
        logger.info("=== MxP SMART Hybrid: Self-Service Override Rejected ===");
        
        // Step 1: Switch to self-service mode
        logger.info("Step 1: Switching to self-service mode");
        Response modeResponse = toggleMode("self_service");
        assertEquals(modeResponse.statusCode(), 200, "Mode toggle should succeed");
        assertEquals(modeResponse.jsonPath().getString("current_mode"), "self_service", 
            "Mode should be self_service");
        
        // Step 2: Build basket
        logger.info("Step 2: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        
        // Step 3: Attempt override in self-service mode
        logger.info("Step 3: Attempting override in self-service mode (should be rejected)");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response transactionResponse = processTransaction(items, TEST_REGION, payment, true);
        
        // Should be rejected (403 Forbidden)
        int statusCode = transactionResponse.statusCode();
        logger.info("  Status code: {}", statusCode);
        
        // In self-service mode, override should be rejected
        // The simulator should return 403
        if (statusCode == 403) {
            logger.info("  Override rejected as expected in self-service mode");
        } else {
            // If it's 200, verify that override was not applied
            Boolean overrideApplied = transactionResponse.jsonPath().getBoolean("override_applied");
            logger.info("  Override applied: {}", overrideApplied);
            assertFalse(overrideApplied, "Override should not be applied in self-service mode");
        }
        
        logger.info("✅ MxP SMART Hybrid: Self-service override rejection completed");
    }

    /**
     * Test: MxP SMART Hybrid — Mode Toggle Endpoint
     * 
     * Given: Current mode is self-service
     * When: The toggle endpoint is called
     * Then: The mode switches to assisted mode
     */
    @Test(description = "MxP SMART Hybrid — Mode toggle endpoint")
    public void testModeToggleEndpoint() {
        logger.info("=== MxP SMART Hybrid: Mode Toggle Endpoint ===");
        
        // Step 1: Set to self-service mode
        logger.info("Step 1: Setting mode to self-service");
        toggleMode("self_service");
        
        // Step 2: Get current mode
        Response getResponse = getMode();
        assertEquals(getResponse.statusCode(), 200, "Get mode should succeed");
        String currentMode = getResponse.jsonPath().getString("mode");
        logger.info("  Current mode: {}", currentMode);
        assertEquals(currentMode, "self_service", "Mode should be self_service");
        
        // Step 3: Toggle mode
        logger.info("Step 3: Toggling mode");
        Response toggleResponse = toggleMode(null);  // Toggle to other mode
        assertEquals(toggleResponse.statusCode(), 200, "Toggle should succeed");
        
        String newMode = toggleResponse.jsonPath().getString("current_mode");
        String previousMode = toggleResponse.jsonPath().getString("previous_mode");
        Integer rotation = toggleResponse.jsonPath().getInt("rotation_degrees");
        
        logger.info("  Previous mode: {}", previousMode);
        logger.info("  New mode: {}", newMode);
        logger.info("  Rotation: {}°", rotation);
        
        assertEquals(previousMode, "self_service", "Previous mode should be self_service");
        assertEquals(newMode, "assisted", "New mode should be assisted");
        assertEquals(rotation, Integer.valueOf(180), "Rotation should be 180° for assisted mode");
        
        logger.info("✅ MxP SMART Hybrid: Mode toggle endpoint test completed");
    }
}