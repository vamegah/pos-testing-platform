// test-automation/src/test/java/com/toshiba/pos/e2e/EleraE2ETest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import com.toshiba.pos.FixtureFactory;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;
import com.toshiba.pos.BasketItem;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * ELERA® E2E Test — Unified-Commerce Sale Across POS and Self-Service Modes
 * 
 * Validates that the same basket produces a consistent final total
 * across both modes.
 * 
 * Scenario:
 *   1. POS Mode: Complete a transaction with the test basket
 *   2. Self-Service Mode: Complete the same transaction with the same basket
 *   3. Verify: Both modes produce the same total
 */
public class EleraE2ETest extends BaseTest {

      private FixtureFactory fixtureFactory;

    // ELERA simulator URL
    private static String eleraUrl;

    // Test data
    //private static final String[] TEST_SKUS = {"SKU-1001", "SKU-1002", "SKU-1005"};
    //private static final String TEST_REGION = "CA";
    //private static final String TEST_CARD = "4111111111111111";

    @BeforeClass
    public void setUpClass() {
        fixtureFactory = FixtureFactory.defaultFactory();
        eleraUrl = getEnv("ELERA_SERVICE_URL", "http://localhost:5000");
        logger.info("=== ELERA E2E Test Initialized ===");
        logger.info("  ELERA URL: {}", eleraUrl);
        logger.info("=====================================");
    }

    /**
     * Helper: Build a test basket with multiple items.
     */
   private List<Map<String, Object>> buildTestBasket() {
        List<BasketItem> items = fixtureFactory.getStandardTestBasket();
        List<Map<String, Object>> result = new ArrayList<>();
        for (BasketItem item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("sku", item.getSku());
            map.put("quantity", item.getQuantity());
            result.add(map);
        }
        return result;
    }

     private String getTestCard() {
        return fixtureFactory.getStandardCard();
    }

    /**
     * Helper: Process a transaction through ELERA.
     */
    private Response processTransaction(String mode, List<Map<String, Object>> items, String region) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", mode);
        payload.put("items", items);
        payload.put("region", region);
        payload.put("payment", Map.of("card_number", getTestCard()));
        
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(eleraUrl + "/elera/transaction")
            .then()
            .extract()
            .response();
    }

    /**
     * Test: ELERA POS Mode Sale
     * 
     * Given: A customer basket with 3 items
     * When: The transaction is processed in POS mode
     * Then: The transaction completes successfully with a consistent total
     */
    @Test(description = "ELERA POS Mode — Complete sale with test basket", groups = {"e2e", "smoke", "regression", "product:elera"})
    public void testPosModeTransaction() {
        logger.info("=== ELERA POS Mode Transaction ===");
        
        // Set mode to POS
        Map<String, Object> modePayload = Map.of("mode", "pos");
        Response modeResponse = given()
            .spec(requestSpec)
            .body(modePayload)
            .when()
            .post(eleraUrl + "/elera/mode")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String mode = modeResponse.jsonPath().getString("mode");
        assertEquals(mode, "pos", "Mode should be set to pos");
        logger.info("  Mode set to: {}", mode);
        
        // Process transaction
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket: {} items", items.size());
        for (Map<String, Object> item : items) {
            logger.info("    - {}", item.get("sku"));
        }
        
        Response transactionResponse = processTransaction("pos", items, TEST_REGION);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        Double subtotal = transactionResponse.jsonPath().getDouble("subtotal");
        Double tax = transactionResponse.jsonPath().getDouble("tax");
        Double total = transactionResponse.jsonPath().getDouble("total");
        String paymentStatus = transactionResponse.jsonPath().getString("payment.status");
        
        logger.info("  Status: {}", status);
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${}", tax);
        logger.info("  Total: ${}", total);
        logger.info("  Payment: {}", paymentStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertNotNull(total, "Total should not be null");
        assertTrue(total > 0, "Total should be greater than 0");
        assertEquals(total, subtotal + tax, 0.01, "Total should equal subtotal + tax");
        
        // Store total for comparison
        Double posTotal = total;
        
        logger.info("✅ ELERA POS Mode transaction completed successfully");
        logger.info("  Total: ${}", posTotal);
    }

    /**
     * Test: ELERA Self-Service Mode Sale
     * 
     * Given: The same customer basket with 3 items
     * When: The transaction is processed in Self-Service mode
     * Then: The transaction completes successfully with the same total as POS mode
     */
    @Test(description = "ELERA Self-Service Mode — Complete sale with test basket", groups = {"e2e", "regression", "product:elera"})
    public void testSelfServiceModeTransaction() {
        logger.info("=== ELERA Self-Service Mode Transaction ===");
        
        // Set mode to Self-Service
        Map<String, Object> modePayload = Map.of("mode", "self_service");
        Response modeResponse = given()
            .spec(requestSpec)
            .body(modePayload)
            .when()
            .post(eleraUrl + "/elera/mode")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String mode = modeResponse.jsonPath().getString("mode");
        assertEquals(mode, "self_service", "Mode should be set to self_service");
        logger.info("  Mode set to: {}", mode);
        
        // Process transaction with same basket
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket: {} items", items.size());
        for (Map<String, Object> item : items) {
            logger.info("    - {}", item.get("sku"));
        }
        
        Response transactionResponse = processTransaction("self_service", items, TEST_REGION);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        Double subtotal = transactionResponse.jsonPath().getDouble("subtotal");
        Double tax = transactionResponse.jsonPath().getDouble("tax");
        Double total = transactionResponse.jsonPath().getDouble("total");
        String paymentStatus = transactionResponse.jsonPath().getString("payment.status");
        
        logger.info("  Status: {}", status);
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${}", tax);
        logger.info("  Total: ${}", total);
        logger.info("  Payment: {}", paymentStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertNotNull(total, "Total should not be null");
        assertTrue(total > 0, "Total should be greater than 0");
        assertEquals(total, subtotal + tax, 0.01, "Total should equal subtotal + tax");
        
        // Verify the security event was logged (Self-Service specific)
        Response eventsResponse = given()
            .spec(requestSpec)
            .when()
            .get(eleraUrl + "/elera/security/events")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Integer eventCount = eventsResponse.jsonPath().getInt("count");
        logger.info("  Security events logged: {}", eventCount);
        assertTrue(eventCount > 0, "Security events should be logged for self-service mode");
        
        logger.info("✅ ELERA Self-Service Mode transaction completed successfully");
        logger.info("  Total: ${}", total);
    }

    /**
     * Test: ELERA Mode Consistency
     * 
     * Given: The same basket processed in both modes
     * When: The totals are compared
     * Then: They are identical (consistent unified-commerce behavior)
     */
    @Test(description = "ELERA Mode Consistency — Same basket, same total across modes", groups = {"e2e", "regression", "product:elera"})
    public void testModeConsistency() {
        logger.info("=== ELERA Mode Consistency Test ===");
        logger.info("  Validating that the same basket produces the same total in both modes");
        
        // Run POS mode transaction
        Map<String, Object> modePayload = Map.of("mode", "pos");
        given()
            .spec(requestSpec)
            .body(modePayload)
            .when()
            .post(eleraUrl + "/elera/mode")
            .then()
            .statusCode(200);
        
        List<Map<String, Object>> items = buildTestBasket();
        Response posResponse = processTransaction("pos", items, TEST_REGION);
        Double posTotal = posResponse.jsonPath().getDouble("total");
        logger.info("  POS Mode Total: ${}", posTotal);
        
        // Run Self-Service mode transaction
        modePayload = Map.of("mode", "self_service");
        given()
            .spec(requestSpec)
            .body(modePayload)
            .when()
            .post(eleraUrl + "/elera/mode")
            .then()
            .statusCode(200);
        
        Response ssResponse = processTransaction("self_service", items, TEST_REGION);
        Double ssTotal = ssResponse.jsonPath().getDouble("total");
        logger.info("  Self-Service Mode Total: ${}", ssTotal);
        
        // Verify consistency
        assertEquals(posTotal, ssTotal, 0.01, 
            "POS and Self-Service modes should produce the same total for the same basket");
        
        logger.info("✅ Mode Consistency verified: Both modes produce the same total");
        logger.info("  Total: ${} across both modes", posTotal);
    }

    /**
     * Test: ELERA Produce Recognition Hook
     * 
     * Given: An image hint for produce
     * When: The produce recognition endpoint is called
     * Then: A recognized produce item is returned with confidence score
     */
    @Test(description = "ELERA Produce Recognition Hook — Mock vision recognition", groups = {"e2e", "regression", "product:elera"})
    public void testProduceRecognition() {
        logger.info("=== ELERA Produce Recognition Test ===");
        
        String imageHint = "apple";
        Map<String, Object> payload = Map.of("image_hint", imageHint);
        
        Response response = given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(eleraUrl + "/elera/produce/recognize")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Map<String, Object> recognized = response.jsonPath().getMap("recognized");
        Double confidence = response.jsonPath().getDouble("confidence");
        
        logger.info("  Image hint: {}", imageHint);
        logger.info("  Recognized: {}", recognized.get("name"));
        logger.info("  Confidence: {}", confidence);
        
        assertNotNull(recognized, "Recognized item should not be null");
        assertTrue(confidence > 0.5, "Confidence should be > 0.5 for a known item");
        assertTrue(confidence <= 1.0, "Confidence should be <= 1.0");
        
        logger.info("✅ Produce Recognition test passed");
        logger.info("  Recognized: {}", recognized.get("name"));
    }
}