// test-automation/src/test/java/com/toshiba/pos/e2e/MxpSmartWallE2ETest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * MxP™ SMART | wall E2E Test
 * 
 * Validates the complete flow for the MxP SMART Wall profile:
 *   1. Full sale without a scale peripheral
 *   2. Confirms wall profile's reduced peripheral set doesn't break checkout
 *   3. Uses only available peripherals (scanner, compact printer, PIN pad)
 * 
 * Verifies that:
 *   - Transactions complete without scale operations
 *   - Scale endpoints are not called (or are gracefully skipped)
 *   - Receipts print correctly on compact printer
 */
public class MxpSmartWallE2ETest extends BaseTest {

    // MxP SMART Wall simulator URL
    private static String wallUrl;

    // Test data
    private static final String TEST_REGION = "CA";
    private static final String TEST_CARD = "4111111111111111";
    private static final String[] TEST_SKUS = {"SKU-1001", "SKU-1002", "SKU-1005"};

    @BeforeClass
    public void setUpClass() {
        wallUrl = getEnv("SMART_WALL_URL", "http://localhost:5003");
        logger.info("=== MxP SMART Wall E2E Test Initialized ===");
        logger.info("  Wall URL: {}", wallUrl);
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
     * Helper: Get available peripherals for wall profile.
     */
    private Response getPeripherals() {
        return given()
            .spec(requestSpec)
            .when()
            .get(wallUrl + "/mxp-smart-wall/peripherals")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Scan an item.
     */
    private Response scanItem(String sku) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", sku);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wallUrl + "/mxp-smart-wall/scan")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Print a receipt.
     */
    private Response printReceipt(String transactionId, List<Map<String, Object>> items, double total) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", transactionId);
        payload.put("items", items);
        payload.put("total", total);
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wallUrl + "/mxp-smart-wall/print")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Process a transaction through the wall profile.
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
            .post(wallUrl + "/mxp-smart-wall/transaction")
            .then()
            .extract()
            .response();
    }

    /**
     * Test: MxP SMART Wall — Full Sale Without Scale
     * 
     * Given: The wall profile has no scale peripheral
     * When: A transaction is processed
     * Then: The sale completes successfully without any scale operations
     */
    @Test(description = "MxP SMART Wall — Full sale without scale peripheral")
    public void testFullSaleWithoutScale() {
        logger.info("=== MxP SMART Wall: Full Sale Without Scale ===");
        
        // Step 1: Verify peripherals
        logger.info("Step 1: Verifying wall profile peripherals");
        Response peripheralsResponse = getPeripherals();
        assertEquals(peripheralsResponse.statusCode(), 200, "Should get peripherals");
        
        Map<String, Object> peripherals = peripheralsResponse.jsonPath().getMap("peripherals");
        boolean scaleAvailable = (Boolean) peripherals.get("scale.available");
        boolean scannerAvailable = (Boolean) peripherals.get("scanner.available");
        boolean printerAvailable = (Boolean) peripherals.get("printer.available");
        boolean pinPadAvailable = (Boolean) peripherals.get("pin_pad.available");
        
        logger.info("  Scanner available: {}", scannerAvailable);
        logger.info("  Scale available: {}", scaleAvailable);
        logger.info("  Printer available: {}", printerAvailable);
        logger.info("  PIN Pad available: {}", pinPadAvailable);
        
        assertFalse(scaleAvailable, "Scale should NOT be available in wall profile");
        assertTrue(scannerAvailable, "Scanner should be available in wall profile");
        assertTrue(printerAvailable, "Printer should be available in wall profile");
        assertTrue(pinPadAvailable, "PIN Pad should be available in wall profile");
        
        // Step 2: Build basket
        logger.info("Step 2: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        for (Map<String, Object> item : items) {
            logger.info("    - {}", item.get("sku"));
        }
        
        // Step 3: Scan each item
        logger.info("Step 3: Scanning items with handheld scanner");
        for (Map<String, Object> item : items) {
            String sku = (String) item.get("sku");
            Response scanResponse = scanItem(sku);
            assertEquals(scanResponse.statusCode(), 200, "Scan should succeed for " + sku);
            String status = scanResponse.jsonPath().getString("status");
            Double price = scanResponse.jsonPath().getDouble("price");
            logger.info("  Scanned {}: ${}", sku, price);
            assertEquals(status, "scanned", "Item should be scanned");
        }
        
        // Step 4: Process transaction (no scale operations)
        logger.info("Step 4: Processing transaction without scale operations");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response transactionResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        String profile = transactionResponse.jsonPath().getString("profile");
        Double subtotal = transactionResponse.jsonPath().getDouble("subtotal");
        Double tax = transactionResponse.jsonPath().getDouble("tax");
        Double total = transactionResponse.jsonPath().getDouble("total");
        String paymentStatus = transactionResponse.jsonPath().getString("payment.status");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Profile: {}", profile);
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${}", tax);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(profile, "wall", "Profile should be 'wall'");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertNotNull(total, "Total should not be null");
        assertTrue(total > 0, "Total should be greater than 0");
        assertEquals(total, subtotal + tax, 0.01, "Total should equal subtotal + tax");
        
        // Step 5: Verify receipt was printed (compact printer)
        logger.info("Step 5: Verifying receipt was printed");
        String receiptStatus = transactionResponse.jsonPath().getString("receipt.status");
        String receiptType = transactionResponse.jsonPath().getString("receipt.type");
        
        logger.info("  Receipt status: {}", receiptStatus);
        logger.info("  Receipt type: {}", receiptType);
        
        assertEquals(receiptStatus, "printed", "Receipt should be printed");
        assertEquals(receiptType, "compact_receipt", "Receipt should be compact type");
        
        logger.info("✅ MxP SMART Wall: Full sale without scale completed successfully");
        logger.info("  Total: ${}", total);
        logger.info("  Scale was NOT used (as expected for wall profile)");
    }

    /**
     * Test: MxP SMART Wall — Scale Operation Rejected
     * 
     * Given: The wall profile has no scale peripheral
     * When: A scale operation is attempted
     * Then: The operation is rejected with a clear error
     */
    @Test(description = "MxP SMART Wall — Scale operation rejected")
    public void testScaleOperationRejected() {
        logger.info("=== MxP SMART Wall: Scale Operation Rejected ===");
        
        // Step 1: Build basket
        List<Map<String, Object>> items = buildTestBasket();
        
        // Step 2: Attempt transaction with scale operation
        logger.info("Step 2: Attempting transaction with scale operation");
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("region", TEST_REGION);
        payload.put("payment", Map.of("card_number", TEST_CARD));
        payload.put("scale_operation", Map.of("type", "weigh", "sku", "SKU-1001"));
        
        Response response = given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(wallUrl + "/mxp-smart-wall/transaction")
            .then()
            .extract()
            .response();
        
        // Should be rejected (400 Bad Request)
        int statusCode = response.statusCode();
        logger.info("  Status code: {}", statusCode);
        
        assertEquals(statusCode, 400, "Scale operation should be rejected");
        
        String errorMessage = response.jsonPath().getString("message");
        logger.info("  Error message: {}", errorMessage);
        assertNotNull(errorMessage, "Error message should be provided");
        assertTrue(errorMessage.contains("scale"), "Error should mention scale");
        
        logger.info("✅ MxP SMART Wall: Scale operation rejected as expected");
        logger.info("  Error: {}", errorMessage);
    }

    /**
     * Test: MxP SMART Wall — Compact Printer Receipt
     * 
     * Given: The wall profile has a compact printer
     * When: A receipt is printed
     * Then: The receipt uses the correct compact format (3-inch paper)
     */
    @Test(description = "MxP SMART Wall — Compact printer receipt format")
    public void testCompactPrinterReceipt() {
        logger.info("=== MxP SMART Wall: Compact Printer Receipt ===");
        
        // Step 1: Process a transaction to get a receipt
        List<Map<String, Object>> items = buildTestBasket();
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response transactionResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String receiptText = transactionResponse.jsonPath().getString("receipt.receipt_text");
        String receiptType = transactionResponse.jsonPath().getString("receipt.type");
        String paperWidth = transactionResponse.jsonPath().getString("receipt.paper_width");
        
        logger.info("  Receipt type: {}", receiptType);
        logger.info("  Paper width: {}", paperWidth);
        logger.info("  Receipt text length: {}", receiptText != null ? receiptText.length() : 0);
        
        assertNotNull(receiptText, "Receipt text should be provided");
        assertEquals(receiptType, "compact_receipt", "Receipt should be compact type");
        assertEquals(paperWidth, "3in", "Paper width should be 3 inches");
        
        // Verify receipt contains expected content
        assertTrue(receiptText.contains("RECEIPT"), "Receipt should contain receipt header");
        assertTrue(receiptText.contains("TOTAL"), "Receipt should contain total");
        assertTrue(receiptText.contains("Thank you"), "Receipt should contain thank you message");
        
        logger.info("✅ MxP SMART Wall: Compact printer receipt format validated");
        logger.info("  Receipt preview:\n{}", receiptText.substring(0, Math.min(200, receiptText.length())) + "...");
    }

    /**
     * Test: MxP SMART Wall — Multiple Item Sale
     * 
     * Given: Multiple items are scanned
     * When: The transaction is processed
     * Then: All items are included in the total
     */
    @Test(description = "MxP SMART Wall — Multiple item sale")
    public void testMultipleItemSale() {
        logger.info("=== MxP SMART Wall: Multiple Item Sale ===");
        
        // Step 1: Build basket with multiple items
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("sku", "SKU-1001", "quantity", 2));
        items.add(Map.of("sku", "SKU-1002", "quantity", 1));
        items.add(Map.of("sku", "SKU-1005", "quantity", 3));
        
        logger.info("  Basket: {} items", items.size());
        for (Map<String, Object> item : items) {
            logger.info("    - {} x{}", item.get("sku"), item.get("quantity"));
        }
        
        // Step 2: Process transaction
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response response = processTransaction(items, TEST_REGION, payment);
        assertEquals(response.statusCode(), 200, "Transaction should succeed");
        
        String status = response.jsonPath().getString("status");
        Double subtotal = response.jsonPath().getDouble("subtotal");
        Double total = response.jsonPath().getDouble("total");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Total: ${}", total);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertTrue(subtotal > 0, "Subtotal should be greater than 0");
        assertTrue(total > subtotal, "Total should be greater than subtotal (with tax)");
        
        // Verify item count in receipt
        List<Map<String, Object>> receiptItems = response.jsonPath().getList("receipt.items");
        logger.info("  Receipt items: {}", receiptItems.size());
        assertTrue(receiptItems.size() >= 3, "Receipt should contain all items");
        
        logger.info("✅ MxP SMART Wall: Multiple item sale completed successfully");
        logger.info("  Total: ${}", total);
    }
}