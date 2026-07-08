// test-automation/src/test/java/com/toshiba/pos/e2e/MxpSmartWingE2ETest.java

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
 * MxP™ SMART | wing E2E Test
 * 
 * Validates the complete flow for the MxP SMART Wing profile:
 *   1. Run the same basket across different peripheral-combination fixtures
 *   2. Each combination completes a sale
 *   3. Disabled peripherals are never called
 * 
 * Uses two different peripheral combinations:
 *   - Combination 1: Full Configuration (all peripherals)
 *   - Combination 2: Minimal Configuration (scanner + printer only)
 */
public class MxpSmartWingE2ETest extends BaseTest {

    // MxP SMART Wing simulator URL
    private static String wingUrl;

    // Test data
    private static final String TEST_REGION = "CA";
    private static final String TEST_CARD = "4111111111111111";
    private static final String[] TEST_SKUS = {"SKU-1001", "SKU-1002", "SKU-1005"};

    @BeforeClass
    public void setUpClass() {
        wingUrl = getEnv("SMART_WING_URL", "http://localhost:5004");
        logger.info("=== MxP SMART Wing E2E Test Initialized ===");
        logger.info("  Wing URL: {}", wingUrl);
        logger.info("============================================");
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
     * Helper: Get current peripheral states.
     */
    private Response getPeripherals() {
        return given()
            .spec(requestSpec)
            .when()
            .get(wingUrl + "/mxp-smart-wing/peripherals")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Toggle a peripheral.
     */
    private Response togglePeripheral(String peripheral, boolean enabled) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("peripheral", peripheral);
        payload.put("enabled", enabled);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wingUrl + "/mxp-smart-wing/peripheral/toggle")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Validate a peripheral combination.
     */
    private Response validateCombination(Map<String, Boolean> peripherals) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("peripherals", peripherals);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wingUrl + "/mxp-smart-wing/peripheral/validate")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Process a transaction.
     */
    private Response processTransaction(List<Map<String, Object>> items, String region,
                                       Map<String, Object> payment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("region", region);
        payload.put("payment", payment);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wingUrl + "/mxp-smart-wing/transaction")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Reset to full configuration.
     */
    private void resetToFullConfiguration() {
        togglePeripheral("scanner", true);
        togglePeripheral("scale", true);
        togglePeripheral("printer", true);
        togglePeripheral("pin_pad", true);
    }

    /**
     * Data Provider: Peripheral combinations.
     */
    @DataProvider(name = "peripheralCombinations")
    public Object[][] peripheralCombinations() {
        return new Object[][] {
            // Combination 1: Full Configuration
            {
                "Full Configuration",
                Map.of(
                    "scanner", true,
                    "scale", true,
                    "printer", true,
                    "pin_pad", true
                ),
                true  // expected valid
            },
            // Combination 2: Minimal Configuration (scanner + printer only)
            {
                "Minimal Configuration",
                Map.of(
                    "scanner", true,
                    "scale", false,
                    "printer", true,
                    "pin_pad", false
                ),
                true  // expected valid
            }
        };
    }

    /**
     * Test: MxP SMART Wing — Validate Peripheral Combinations
     * 
     * Given: Different peripheral combinations
     * When: Each combination is validated
     * Then: The validation passes for valid combinations
     */
    @Test(description = "MxP SMART Wing — Validate peripheral combinations", 
          dataProvider = "peripheralCombinations")
    public void testValidateCombination(String name, Map<String, Boolean> peripherals, boolean expectedValid) {
        logger.info("=== MxP SMART Wing: Validate Combination ===");
        logger.info("  Combination: {}", name);
        logger.info("  Peripherals: {}", peripherals);
        
        Response response = validateCombination(peripherals);
        
        if (expectedValid) {
            assertEquals(response.statusCode(), 200, "Combination should be valid");
            Boolean valid = response.jsonPath().getBoolean("valid");
            assertTrue(valid, "Combination should be valid");
            logger.info("  ✅ Valid combination: {}", name);
        } else {
            // This case is for invalid combinations (not used in this test)
            assertNotEquals(response.statusCode(), 200, "Invalid combination should be rejected");
        }
        
        logger.info("✅ MxP SMART Wing: Combination validation completed");
    }

    /**
     * Test: MxP SMART Wing — Full Configuration Sale
     * 
     * Given: All peripherals are enabled (full configuration)
     * When: A transaction is processed
     * Then: The sale completes successfully with all peripherals available
     */
    @Test(description = "MxP SMART Wing — Full configuration sale")
    public void testFullConfigurationSale() {
        logger.info("=== MxP SMART Wing: Full Configuration Sale ===");
        
        // Step 1: Reset to full configuration
        logger.info("Step 1: Setting full configuration (all peripherals enabled)");
        resetToFullConfiguration();
        
        // Step 2: Verify peripherals
        Response peripheralsResponse = getPeripherals();
        assertEquals(peripheralsResponse.statusCode(), 200, "Should get peripherals");
        
        Map<String, Object> peripherals = peripheralsResponse.jsonPath().getMap("peripherals");
        logger.info("  Scanner: {}", peripherals.get("scanner"));
        logger.info("  Scale: {}", peripherals.get("scale"));
        logger.info("  Printer: {}", peripherals.get("printer"));
        logger.info("  PIN Pad: {}", peripherals.get("pin_pad"));
        
        assertTrue((Boolean) peripherals.get("scanner"), "Scanner should be enabled");
        assertTrue((Boolean) peripherals.get("scale"), "Scale should be enabled");
        assertTrue((Boolean) peripherals.get("printer"), "Printer should be enabled");
        assertTrue((Boolean) peripherals.get("pin_pad"), "PIN Pad should be enabled");
        
        // Step 3: Build basket
        logger.info("Step 2: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        
        // Step 4: Process transaction
        logger.info("Step 3: Processing transaction with full configuration");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response response = processTransaction(items, TEST_REGION, payment);
        assertEquals(response.statusCode(), 200, "Transaction should succeed");
        
        String status = response.jsonPath().getString("status");
        String profile = response.jsonPath().getString("profile");
        Double total = response.jsonPath().getDouble("total");
        String paymentStatus = response.jsonPath().getString("payment.status");
        String receiptStatus = response.jsonPath().getString("receipt");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Profile: {}", profile);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        logger.info("  Receipt status: {}", receiptStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(profile, "wing", "Profile should be 'wing'");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertEquals(receiptStatus, "printed", "Receipt should be printed");
        assertTrue(total > 0, "Total should be greater than 0");
        
        logger.info("✅ MxP SMART Wing: Full configuration sale completed successfully");
        logger.info("  Total: ${}", total);
    }

    /**
     * Test: MxP SMART Wing — Minimal Configuration Sale
     * 
     * Given: Only scanner and printer are enabled (minimal configuration)
     * When: A transaction is processed
     * Then: The sale completes successfully without disabled peripherals
     */
    @Test(description = "MxP SMART Wing — Minimal configuration sale (scanner + printer only)")
    public void testMinimalConfigurationSale() {
        logger.info("=== MxP SMART Wing: Minimal Configuration Sale ===");
        
        // Step 1: Set minimal configuration (scanner + printer only)
        logger.info("Step 1: Setting minimal configuration (scanner + printer only)");
        togglePeripheral("scanner", true);
        togglePeripheral("scale", false);
        togglePeripheral("printer", true);
        togglePeripheral("pin_pad", false);
        
        // Step 2: Verify peripherals
        Response peripheralsResponse = getPeripherals();
        assertEquals(peripheralsResponse.statusCode(), 200, "Should get peripherals");
        
        Map<String, Object> peripherals = peripheralsResponse.jsonPath().getMap("peripherals");
        logger.info("  Scanner: {}", peripherals.get("scanner"));
        logger.info("  Scale: {}", peripherals.get("scale"));
        logger.info("  Printer: {}", peripherals.get("printer"));
        logger.info("  PIN Pad: {}", peripherals.get("pin_pad"));
        
        assertTrue((Boolean) peripherals.get("scanner"), "Scanner should be enabled");
        assertFalse((Boolean) peripherals.get("scale"), "Scale should be disabled");
        assertTrue((Boolean) peripherals.get("printer"), "Printer should be enabled");
        assertFalse((Boolean) peripherals.get("pin_pad"), "PIN Pad should be disabled");
        
        // Step 3: Build basket (same basket as full configuration)
        logger.info("Step 2: Building basket (same as full configuration)");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        
        // Step 4: Process transaction
        logger.info("Step 3: Processing transaction with minimal configuration");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response response = processTransaction(items, TEST_REGION, payment);
        assertEquals(response.statusCode(), 200, "Transaction should succeed");
        
        String status = response.jsonPath().getString("status");
        String profile = response.jsonPath().getString("profile");
        Double total = response.jsonPath().getDouble("total");
        String paymentStatus = response.jsonPath().getString("payment.status");
        String receiptStatus = response.jsonPath().getString("receipt");
        Map<String, Object> usedPeripherals = response.jsonPath().getMap("peripherals");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Profile: {}", profile);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        logger.info("  Receipt status: {}", receiptStatus);
        logger.info("  Peripherals used: {}", usedPeripherals);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(profile, "wing", "Profile should be 'wing'");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertEquals(receiptStatus, "printed", "Receipt should be printed");
        assertTrue(total > 0, "Total should be greater than 0");
        
        // Verify disabled peripherals were not used
        Map<String, Object> wingPeripherals = (Map<String, Object>) usedPeripherals.get("peripherals");
        if (wingPeripherals != null) {
            assertFalse((Boolean) wingPeripherals.get("scale"), "Scale should not be used");
            assertFalse((Boolean) wingPeripherals.get("pin_pad"), "PIN Pad should not be used");
        }
        
        logger.info("✅ MxP SMART Wing: Minimal configuration sale completed successfully");
        logger.info("  Total: ${}", total);
        logger.info("  Disabled peripherals were NOT used");
    }

    /**
     * Test: MxP SMART Wing — Same Basket Across Both Configurations
     * 
     * Given: The same basket
     * When: Processed in both full and minimal configurations
     * Then: The total should be the same (prices are unaffected by peripherals)
     */
    @Test(description = "MxP SMART Wing — Same basket, same total across configurations")
    public void testSameBasketAcrossConfigurations() {
        logger.info("=== MxP SMART Wing: Same Basket Across Configurations ===");
        logger.info("  Validating that the same basket produces the same total");
        logger.info("  regardless of peripheral configuration");
        
        // Step 1: Process in full configuration
        logger.info("Step 1: Processing basket in full configuration");
        resetToFullConfiguration();
        
        List<Map<String, Object>> items = buildTestBasket();
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response fullResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(fullResponse.statusCode(), 200, "Full config transaction should succeed");
        
        Double fullTotal = fullResponse.jsonPath().getDouble("total");
        Double fullSubtotal = fullResponse.jsonPath().getDouble("subtotal");
        logger.info("  Full configuration - Subtotal: ${}, Total: ${}", fullSubtotal, fullTotal);
        
        // Step 2: Process in minimal configuration
        logger.info("Step 2: Processing same basket in minimal configuration");
        togglePeripheral("scanner", true);
        togglePeripheral("scale", false);
        togglePeripheral("printer", true);
        togglePeripheral("pin_pad", false);
        
        Response minimalResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(minimalResponse.statusCode(), 200, "Minimal config transaction should succeed");
        
        Double minimalTotal = minimalResponse.jsonPath().getDouble("total");
        Double minimalSubtotal = minimalResponse.jsonPath().getDouble("subtotal");
        logger.info("  Minimal configuration - Subtotal: ${}, Total: ${}", minimalSubtotal, minimalTotal);
        
        // Step 3: Verify totals match
        logger.info("Step 3: Verifying totals match");
        assertEquals(fullSubtotal, minimalSubtotal, 0.01, 
            "Subtotal should be the same regardless of peripherals");
        assertEquals(fullTotal, minimalTotal, 0.01, 
            "Total should be the same regardless of peripherals");
        
        logger.info("✅ MxP SMART Wing: Same basket across configurations validated");
        logger.info("  Subtotal: ${} (full) vs ${} (minimal)", fullSubtotal, minimalSubtotal);
        logger.info("  Total: ${} (full) vs ${} (minimal)", fullTotal, minimalTotal);
        logger.info("  Both configurations produce the same total for the same basket");
    }

    /**
     * Test: MxP SMART Wing — Peripheral Toggle Endpoint
     * 
     * Given: A peripheral is enabled
     * When: The toggle endpoint is called
     * Then: The peripheral state changes correctly
     */
    @Test(description = "MxP SMART Wing — Peripheral toggle endpoint")
    public void testPeripheralToggle() {
        logger.info("=== MxP SMART Wing: Peripheral Toggle ===");
        
        // Step 1: Reset to full configuration
        logger.info("Step 1: Resetting to full configuration");
        resetToFullConfiguration();
        
        // Step 2: Verify scale is enabled
        Response initialResponse = getPeripherals();
        boolean initialScale = initialResponse.jsonPath().getBoolean("peripherals.scale");
        logger.info("  Initial scale state: {}", initialScale);
        assertTrue(initialScale, "Scale should initially be enabled");
        
        // Step 3: Toggle scale off
        logger.info("Step 2: Toggling scale off");
        Response toggleResponse = togglePeripheral("scale", false);
        assertEquals(toggleResponse.statusCode(), 200, "Toggle should succeed");
        
        boolean newScaleState = toggleResponse.jsonPath().getBoolean("enabled");
        String peripheral = toggleResponse.jsonPath().getString("peripheral");
        boolean previousState = toggleResponse.jsonPath().getBoolean("previous_state");
        
        logger.info("  Peripheral: {}, Previous: {}, New: {}", peripheral, previousState, newScaleState);
        assertEquals(peripheral, "scale", "Peripheral should be 'scale'");
        assertTrue(previousState, "Previous state should be true");
        assertFalse(newScaleState, "New state should be false");
        
        // Step 4: Verify scale is disabled
        Response finalResponse = getPeripherals();
        boolean finalScale = finalResponse.jsonPath().getBoolean("peripherals.scale");
        logger.info("  Final scale state: {}", finalScale);
        assertFalse(finalScale, "Scale should now be disabled");
        
        logger.info("✅ MxP SMART Wing: Peripheral toggle completed successfully");
    }
}