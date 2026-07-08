// test-automation/src/test/java/com/toshiba/pos/e2e/SelfCheckoutSystem7E2ETest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * Self Checkout System 7 E2E Test
 * 
 * Validates the complete flow for Self Checkout System 7:
 *   1. Multi-item basket
 *   2. Bagging-area/scale event
 *   3. Printer receipt
 *   4. Attended-assist escalation path
 *   5. Negative path: Unexpected item in bagging area
 */
public class SelfCheckoutSystem7E2ETest extends BaseTest {

    // Self Checkout System 7 simulator URL
    private static String scs7Url;

    // Test data
    private static final String TEST_REGION = "CA";
    private static final String TEST_CARD = "4111111111111111";
    private static final String TEST_BIOMETRIC = "BIOMETRIC-USER-001";
    private static final String TEST_NFC = "NFC-CARD-001";

    @BeforeClass
    public void setUpClass() {
        scs7Url = getEnv("SELF_CHECKOUT_7_URL", "http://localhost:5005");
        logger.info("=== Self Checkout System 7 E2E Test Initialized ===");
        logger.info("  SCS7 URL: {}", scs7Url);
        logger.info("====================================================");
    }

    /**
     * Helper: Start a transaction.
     */
    private Response startTransaction(String sessionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/transaction/start")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Scan an item.
     */
    private Response scanItem(String sessionId, String sku, int quantity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        payload.put("sku", sku);
        payload.put("quantity", quantity);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/scan")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Verify bagging area weight.
     */
    private Response verifyBagging(String sessionId, double measuredWeightKg) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        payload.put("measured_weight_kg", measuredWeightKg);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/bagging/verify")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Attended override.
     */
    private Response attendedOverride(String sessionId, String action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        payload.put("action", action);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/attended/override")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Awareness event.
     */
    private Response awarenessEvent(String sessionId, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        payload.put("event_type", eventType);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/awareness/event")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Complete transaction.
     */
    private Response completeTransaction(String sessionId, String region,
                                         Map<String, Object> payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", sessionId);
        payload.put("region", region);
        payload.put("payment", payment);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/transaction/complete")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Set mode.
     */
    private Response setMode(String mode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", mode);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(scs7Url + "/self-checkout/mode")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get mode.
     */
    private Response getMode() {
        return given()
            .spec(requestSpec)
            .when()
            .get(scs7Url + "/self-checkout/mode")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get awareness events.
     */
    private Response getAwarenessEvents() {
        return given()
            .spec(requestSpec)
            .when()
            .get(scs7Url + "/self-checkout/awareness/events")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Build a multi-item basket with known weights.
     */
    private List<Map<String, Object>> buildMultiItemBasket() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("sku", "SKU-1001", "quantity", 2));
        items.add(Map.of("sku", "SKU-1002", "quantity", 1));
        items.add(Map.of("sku", "SKU-1003", "quantity", 1));
        items.add(Map.of("sku", "SKU-1005", "quantity", 3));
        return items;
    }

    /**
     * Test: Self Checkout System 7 — Happy Path with Unattended Mode
     * 
     * Given: A multi-item basket
     * When: Items are scanned, bagged, and payment is completed
     * Then: The transaction completes successfully with receipt
     */
    @Test(description = "Self Checkout System 7 — Happy path with unattended mode")
    public void testHappyPathUnattendedMode() {
        logger.info("=== Self Checkout System 7: Happy Path (Unattended) ===");
        
        String sessionId = "happy-unattended-" + System.currentTimeMillis();
        List<Map<String, Object>> items = buildMultiItemBasket();
        
        // Step 1: Set mode to unattended
        logger.info("Step 1: Setting mode to unattended");
        setMode("unattended");
        Response modeResponse = getMode();
        assertEquals(modeResponse.jsonPath().getString("mode"), "unattended", 
            "Mode should be unattended");
        
        // Step 2: Start transaction
        logger.info("Step 2: Starting transaction: {}", sessionId);
        Response startResponse = startTransaction(sessionId);
        assertEquals(startResponse.statusCode(), 200, "Transaction should start");
        assertEquals(startResponse.jsonPath().getString("status"), "started", 
            "Status should be 'started'");
        
        // Step 3: Scan items
        logger.info("Step 3: Scanning {} items", items.size());
        double expectedWeight = 0.0;
        for (Map<String, Object> item : items) {
            String sku = (String) item.get("sku");
            int quantity = (Integer) item.get("quantity");
            Response scanResponse = scanItem(sessionId, sku, quantity);
            assertEquals(scanResponse.statusCode(), 200, "Scan should succeed for " + sku);
            logger.info("  Scanned {} x{}", sku, quantity);
            
            // Track expected weight
            // In real test, this would come from the service
        }
        
        // Step 4: Awareness event — scanning
        logger.info("Step 4: Logging awareness event: scanning");
        Response awarenessResponse = awarenessEvent(sessionId, "scanning");
        assertEquals(awarenessResponse.statusCode(), 200, "Awareness event should be logged");
        
        // Step 5: Bagging verification (using expected weights)
        logger.info("Step 5: Bagging verification");
        // Calculate expected weight from the items (mock)
        double measuredWeight = 3.78 * 2 + 0.45 + 0.68 + 0.18 * 3;
        Response baggingResponse = verifyBagging(sessionId, measuredWeight);
        assertEquals(baggingResponse.statusCode(), 200, "Bagging verification should pass");
        Boolean matches = baggingResponse.jsonPath().getBoolean("matches");
        assertTrue(matches, "Bagging weight should match expected");
        logger.info("  Bagging verified: {}", matches);
        
        // Step 6: Awareness event — bagging
        logger.info("Step 6: Logging awareness event: bagging");
        awarenessEvent(sessionId, "bagging");
        
        // Step 7: Complete transaction
        logger.info("Step 7: Completing transaction");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response completeResponse = completeTransaction(sessionId, TEST_REGION, payment);
        assertEquals(completeResponse.statusCode(), 200, "Transaction should complete");
        
        String status = completeResponse.jsonPath().getString("status");
        Double total = completeResponse.jsonPath().getDouble("total");
        String paymentStatus = completeResponse.jsonPath().getString("payment.status");
        Boolean baggingVerified = completeResponse.jsonPath().getBoolean("bagging_verified");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        logger.info("  Bagging verified: {}", baggingVerified);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertTrue(baggingVerified, "Bagging should be verified");
        assertTrue(total > 0, "Total should be greater than 0");
        
        // Step 8: Verify awareness events
        logger.info("Step 8: Verifying awareness events");
        Response eventsResponse = getAwarenessEvents();
        Integer eventCount = eventsResponse.jsonPath().getInt("count");
        logger.info("  Total awareness events: {}", eventCount);
        assertTrue(eventCount >= 3, "Should have at least 3 awareness events");
        
        logger.info("✅ Self Checkout System 7: Happy path (unattended) completed successfully");
        logger.info("  Total: ${}", total);
    }

    /**
     * Test: Self Checkout System 7 — Attended Mode with Override
     * 
     * Given: A multi-item basket
     * When: An attended override is needed
     * Then: The override is applied and the transaction completes
     */
    @Test(description = "Self Checkout System 7 — Attended mode with override")
    public void testAttendedModeWithOverride() {
        logger.info("=== Self Checkout System 7: Attended Mode with Override ===");
        
        String sessionId = "attended-override-" + System.currentTimeMillis();
        List<Map<String, Object>> items = buildMultiItemBasket();
        
        // Step 1: Set mode to attended
        logger.info("Step 1: Setting mode to attended");
        setMode("attended");
        Response modeResponse = getMode();
        assertEquals(modeResponse.jsonPath().getString("mode"), "attended", 
            "Mode should be attended");
        
        // Step 2: Start transaction
        logger.info("Step 2: Starting transaction: {}", sessionId);
        startTransaction(sessionId);
        
        // Step 3: Scan items
        logger.info("Step 3: Scanning items");
        for (Map<String, Object> item : items) {
            String sku = (String) item.get("sku");
            int quantity = (Integer) item.get("quantity");
            scanItem(sessionId, sku, quantity);
        }
        
        // Step 4: Simulate a weight mismatch
        logger.info("Step 4: Simulating weight mismatch");
        Response baggingResponse = verifyBagging(sessionId, 0.0);  // Wrong weight
        assertEquals(baggingResponse.statusCode(), 400, "Bagging mismatch should be detected");
        logger.info("  Bagging mismatch detected");
        
        // Step 5: Awareness event — assistance needed
        logger.info("Step 5: Logging awareness event: assistance_needed");
        awarenessEvent(sessionId, "assistance_needed");
        
        // Step 6: Attended override
        logger.info("Step 6: Attended override");
        Response overrideResponse = attendedOverride(sessionId, "approve_weight_mismatch");
        assertEquals(overrideResponse.statusCode(), 200, "Override should succeed");
        String overrideStatus = overrideResponse.jsonPath().getString("status");
        String overrideAction = overrideResponse.jsonPath().getString("action");
        logger.info("  Override status: {}, action: {}", overrideStatus, overrideAction);
        assertEquals(overrideStatus, "overridden", "Override should be applied");
        assertEquals(overrideAction, "approve_weight_mismatch", "Action should match");
        
        // Step 7: Complete transaction
        logger.info("Step 7: Completing transaction after override");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response completeResponse = completeTransaction(sessionId, TEST_REGION, payment);
        assertEquals(completeResponse.statusCode(), 200, "Transaction should complete");
        
        String status = completeResponse.jsonPath().getString("status");
        String paymentStatus = completeResponse.jsonPath().getString("payment.status");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Payment status: {}", paymentStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        
        logger.info("✅ Self Checkout System 7: Attended mode with override completed successfully");
    }

    /**
     * Test: Self Checkout System 7 — Unexpected Item in Bagging Area (Negative Path)
     * 
     * Given: A multi-item basket
     * When: An unexpected item is detected in the bagging area
     * Then: An assist event is raised
     */
    @Test(description = "Self Checkout System 7 — Unexpected item in bagging area (negative path)")
    public void testUnexpectedItemInBaggingArea() {
        logger.info("=== Self Checkout System 7: Unexpected Item in Bagging Area ===");
        
        String sessionId = "unexpected-item-" + System.currentTimeMillis();
        List<Map<String, Object>> items = buildMultiItemBasket();
        
        // Step 1: Set mode to unattended
        logger.info("Step 1: Setting mode to unattended");
        setMode("unattended");
        
        // Step 2: Start transaction
        logger.info("Step 2: Starting transaction: {}", sessionId);
        startTransaction(sessionId);
        
        // Step 3: Scan items (only some items)
        logger.info("Step 3: Scanning partial basket (2 items instead of all)");
        for (int i = 0; i < Math.min(2, items.size()); i++) {
            Map<String, Object> item = items.get(i);
            String sku = (String) item.get("sku");
            int quantity = (Integer) item.get("quantity");
            scanItem(sessionId, sku, quantity);
        }
        
        // Step 4: Bagging verification with weight that doesn't match
        // The bagging area has more weight than expected (unexpected item)
        logger.info("Step 4: Bagging verification with unexpected weight");
        // Calculate weight for all items (including unscanned)
        double measuredWeight = 3.78 * 2 + 0.45 + 0.68 + 0.18 * 3;
        Response baggingResponse = verifyBagging(sessionId, measuredWeight);
        
        // Should fail due to mismatched weight
        assertEquals(baggingResponse.statusCode(), 400, "Bagging verification should fail");
        Boolean matches = baggingResponse.jsonPath().getBoolean("matches");
        assertFalse(matches, "Weight should not match (unexpected item)");
        logger.info("  Bagging mismatch detected: weight does not match scanned items");
        
        // Step 5: Awareness event — assistance needed
        logger.info("Step 5: Logging awareness event: assistance_needed");
        awarenessEvent(sessionId, "assistance_needed");
        
        // Step 6: Switch to attended mode for assist
        logger.info("Step 6: Switching to attended mode for assist");
        setMode("attended");
        
        // Step 7: Attended override
        logger.info("Step 7: Attended override");
        Response overrideResponse = attendedOverride(sessionId, "approve_weight_mismatch");
        assertEquals(overrideResponse.statusCode(), 200, "Override should succeed");
        
        // Step 8: Complete transaction with all items
        logger.info("Step 8: Completing transaction after assist");
        // Complete with all items
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        // Now scan remaining items
        for (int i = 2; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            String sku = (String) item.get("sku");
            int quantity = (Integer) item.get("quantity");
            scanItem(sessionId, sku, quantity);
        }
        
        Response completeResponse = completeTransaction(sessionId, TEST_REGION, payment);
        assertEquals(completeResponse.statusCode(), 200, "Transaction should complete");
        
        String status = completeResponse.jsonPath().getString("status");
        logger.info("  Transaction status: {}", status);
        
        assertEquals(status, "completed", "Transaction should be completed");
        
        logger.info("✅ Self Checkout System 7: Unexpected item negative path completed successfully");
        logger.info("  Assist event was raised and handled");
    }

    /**
     * Test: Self Checkout System 7 — Receipt Generation
     * 
     * Given: A completed transaction
     * When: A receipt is generated
     * Then: The receipt contains all items and correct total
     */
    @Test(description = "Self Checkout System 7 — Receipt generation")
    public void testReceiptGeneration() {
        logger.info("=== Self Checkout System 7: Receipt Generation ===");
        
        String sessionId = "receipt-" + System.currentTimeMillis();
        List<Map<String, Object>> items = buildMultiItemBasket();
        
        // Step 1: Set mode to unattended
        setMode("unattended");
        
        // Step 2: Start transaction
        startTransaction(sessionId);
        
        // Step 3: Scan items
        logger.info("Step 3: Scanning items");
        for (Map<String, Object> item : items) {
            String sku = (String) item.get("sku");
            int quantity = (Integer) item.get("quantity");
            scanItem(sessionId, sku, quantity);
        }
        
        // Step 4: Bagging verification
        logger.info("Step 4: Bagging verification");
        double measuredWeight = 3.78 * 2 + 0.45 + 0.68 + 0.18 * 3;
        verifyBagging(sessionId, measuredWeight);
        
        // Step 5: Complete transaction
        logger.info("Step 5: Completing transaction");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response completeResponse = completeTransaction(sessionId, TEST_REGION, payment);
        assertEquals(completeResponse.statusCode(), 200, "Transaction should complete");
        
        // Step 6: Verify receipt details
        logger.info("Step 6: Verifying receipt");
        Double total = completeResponse.jsonPath().getDouble("total");
        Double subtotal = completeResponse.jsonPath().getDouble("subtotal");
        Double tax = completeResponse.jsonPath().getDouble("tax");
        List<Map<String, Object>> receiptItems = completeResponse.jsonPath().getList("items");
        
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${}", tax);
        logger.info("  Total: ${}", total);
        logger.info("  Items on receipt: {}", receiptItems.size());
        
        assertNotNull(total, "Total should be present");
        assertTrue(total > 0, "Total should be greater than 0");
        assertTrue(receiptItems.size() >= items.size(), 
            "Receipt should contain all items");
        
        // Verify receipt contains all item totals
        double calculatedTotal = 0.0;
        for (Map<String, Object> item : receiptItems) {
            Double price = (Double) item.get("price");
            Integer quantity = (Integer) item.get("quantity");
            if (price != null && quantity != null) {
                calculatedTotal += price * quantity;
            }
        }
        // Note: tax is added separately
        logger.info("  Calculated subtotal from receipt: ${}", calculatedTotal);
        assertEquals(calculatedTotal, subtotal, 0.01, 
            "Subtotal should match sum of item prices");
        
        logger.info("✅ Self Checkout System 7: Receipt generation verified successfully");
    }
}