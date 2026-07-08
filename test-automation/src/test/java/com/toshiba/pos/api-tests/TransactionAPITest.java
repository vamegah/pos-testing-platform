// test-automation/src/test/java/com/toshiba/pos/api-tests/TransactionAPITest.java

package com.toshiba.pos.api_tests;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * Transaction API Test - Sale Flow
 * 
 * Tests the complete POS transaction flow:
 *   1. Scan item (get price from pricing service)
 *   2. Check promotions (get discounts from promotions service)
 *   3. Calculate tax (tax service)
 *   4. Authorize payment (payment gateway)
 *   5. Generate receipt (simulated)
 * 
 * All tests run against local Phase 1 mock services.
 */
public class TransactionAPITest extends BaseTest {
    
    // Test data constants
    private static final String TEST_SKU = "SKU-1001";
    private static final String TEST_REGION = "CA";
    private static final String TEST_MERCHANT = "TEST_MERCHANT_001";
    private static final String TEST_CARD_APPROVED = "4111111111111111";
    private static final String TEST_CARD_DECLINED = "4111111111110000";
    
    // Hold transaction state for multi-step tests
    private Double itemPrice;
    private Double subtotal;
    private List<Map<String, Object>> appliedPromotions;
    private Double taxAmount;
    private Double total;
    private String transactionId;
    
    @BeforeClass
    public void setUpClass() {
        logger.info("=== TransactionAPITest Initialized ===");
        logger.info("Target Services:");
        logger.info("  Pricing:    {}", pricingServiceUrl);
        logger.info("  Promotions: {}", promotionsServiceUrl);
        logger.info("  Tax:        {}", taxServiceUrl);
        logger.info("  Payment:    {}", paymentGatewayUrl);
        logger.info("=====================================");
    }
    
    /**
     * Test: Complete Happy Path Transaction
     * 
     * Given: A customer wants to buy one item (SKU-1001)
     * When: The POS scans the item and processes the transaction
     * Then: The payment is approved and a receipt is generated
     */
    @Test(description = "Happy path - Complete sale flow with approved payment")
    public void testHappyPathTransaction() {
        logger.info("Starting happy path transaction test...");
        
        // Step 1: Get price for SKU
        logger.info("Step 1: Price lookup for SKU: {}", TEST_SKU);
        Response priceResponse = given()
            .spec(requestSpec)
            .when()
            .get(pricingServiceUrl + "/price/" + TEST_SKU)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        itemPrice = priceResponse.jsonPath().getDouble("price");
        String itemName = priceResponse.jsonPath().getString("name");
        logger.info("  Item: {} - ${}", itemName, itemPrice);
        assertNotNull(itemPrice, "Price should not be null");
        assertTrue(itemPrice > 0, "Price should be greater than 0");
        
        subtotal = itemPrice;
        logger.info("  Subtotal: ${}", subtotal);
        
        // Step 2: Check promotions for the item
        logger.info("Step 2: Promotion lookup for SKU: {}", TEST_SKU);
        Response promoResponse = given()
            .spec(requestSpec)
            .when()
            .get(promotionsServiceUrl + "/promotions/sku/" + TEST_SKU)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        appliedPromotions = promoResponse.jsonPath().getList("promotions");
        logger.info("  Found {} promotion(s)", appliedPromotions != null ? appliedPromotions.size() : 0);
        
        // Step 3: Calculate tax (simple - use subtotal)
        logger.info("Step 3: Tax calculation for region: {}", TEST_REGION);
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", subtotal);
        taxRequest.put("region", TEST_REGION);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        taxAmount = taxResponse.jsonPath().getDouble("tax_amount");
        total = taxResponse.jsonPath().getDouble("total");
        logger.info("  Tax: ${}, Total: ${}", taxAmount, total);
        assertNotNull(taxAmount, "Tax amount should not be null");
        assertTrue(taxAmount >= 0, "Tax should not be negative");
        
        // Step 4: Authorize payment
        logger.info("Step 4: Payment authorization for ${}", total);
        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", total);
        paymentRequest.put("currency", "USD");
        paymentRequest.put("card_number", TEST_CARD_APPROVED);
        paymentRequest.put("card_expiry", "12/25");
        paymentRequest.put("cvv", "123");
        paymentRequest.put("merchant_id", TEST_MERCHANT);
        paymentRequest.put("order_id", "ORDER-" + System.currentTimeMillis());
        
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", "Test Customer");
        customer.put("email", "test@example.com");
        paymentRequest.put("customer", customer);
        
        Response paymentResponse = given()
            .spec(requestSpec)
            .body(paymentRequest)
            .when()
            .post(paymentGatewayUrl + "/payment/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String status = paymentResponse.jsonPath().getString("status");
        transactionId = paymentResponse.jsonPath().getString("transaction_id");
        String authCode = paymentResponse.jsonPath().getString("auth_code");
        
        logger.info("  Payment Status: {}", status);
        logger.info("  Transaction ID: {}", transactionId);
        logger.info("  Auth Code: {}", authCode);
        
        assertEquals(status, "approved", "Payment should be approved");
        assertNotNull(transactionId, "Transaction ID should be provided");
        assertNotNull(authCode, "Auth code should be provided");
        
        // Step 5: Verify receipt (simulated - check payment was recorded)
        logger.info("Step 5: Verifying transaction record");
        Response transactionResponse = given()
            .spec(requestSpec)
            .when()
            .get(paymentGatewayUrl + "/payment/transaction/" + transactionId)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String txnStatus = transactionResponse.jsonPath().getString("status");
        Double txnAmount = transactionResponse.jsonPath().getDouble("amount");
        
        assertEquals(txnStatus, "approved", "Transaction should be approved in record");
        assertEquals(txnAmount, total, "Transaction amount should match");
        
        logger.info("✅ Happy path transaction completed successfully!");
        logger.info("  Total: ${}, Status: {}, Txn: {}", total, txnStatus, transactionId);
    }
    
    /**
     * Test: Declined Payment Transaction
     * 
     * Given: A customer tries to pay with a card that should be declined
     * When: The POS processes the payment
     * Then: The payment is declined with an appropriate reason
     */
    @Test(description = "Declined payment - Card ending in 0000 should be declined")
    public void testDeclinedPaymentTransaction() {
        logger.info("Starting declined payment transaction test...");
        
        // Step 1: Price lookup (reuse SKU-1001)
        Response priceResponse = given()
            .spec(requestSpec)
            .when()
            .get(pricingServiceUrl + "/price/" + TEST_SKU)
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Double price = priceResponse.jsonPath().getDouble("price");
        logger.info("  Item price: ${}", price);
        
        // Step 2: Calculate tax for simplicity (no promotions for this test)
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", price);
        taxRequest.put("region", TEST_REGION);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Double totalWithTax = taxResponse.jsonPath().getDouble("total");
        logger.info("  Total with tax: ${}", totalWithTax);
        
        // Step 3: Attempt payment with declined card
        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", totalWithTax);
        paymentRequest.put("currency", "USD");
        paymentRequest.put("card_number", TEST_CARD_DECLINED);
        paymentRequest.put("card_expiry", "12/25");
        paymentRequest.put("cvv", "123");
        paymentRequest.put("merchant_id", TEST_MERCHANT);
        paymentRequest.put("order_id", "ORDER-DECLINED-" + System.currentTimeMillis());
        
        Response paymentResponse = given()
            .spec(requestSpec)
            .body(paymentRequest)
            .when()
            .post(paymentGatewayUrl + "/payment/authorize");
        
        // Expect 402 Payment Required for declined
        assertEquals(paymentResponse.statusCode(), 402, "Declined payment should return HTTP 402");
        
        String status = paymentResponse.jsonPath().getString("status");
        String reason = paymentResponse.jsonPath().getString("reason");
        
        logger.info("  Payment Status: {}", status);
        logger.info("  Decline Reason: {}", reason);
        
        assertEquals(status, "declined", "Payment should be declined");
        assertNotNull(reason, "Decline reason should be provided");
        
        logger.info("✅ Declined payment test passed!");
        logger.info("  Reason: {}", reason);
    }
    
    /**
     * Test: Multi-Item Transaction
     * 
     * Given: A customer buys multiple items
     * When: The POS processes the transaction
     * Then: The total reflects all items with correct pricing and tax
     */
    @Test(description = "Multi-item transaction with multiple SKUs")
    public void testMultiItemTransaction() {
        logger.info("Starting multi-item transaction test...");
        
        List<String> skus = Arrays.asList("SKU-1001", "SKU-1002", "SKU-1005");
        double runningSubtotal = 0.0;
        Map<String, Double> itemPrices = new HashMap<>();
        
        // Step 1: Get prices for all items
        for (String sku : skus) {
            Response priceResponse = given()
                .spec(requestSpec)
                .when()
                .get(pricingServiceUrl + "/price/" + sku)
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            Double price = priceResponse.jsonPath().getDouble("price");
            itemPrices.put(sku, price);
            runningSubtotal += price;
            logger.info("  {}: ${}", sku, price);
        }
        
        logger.info("  Subtotal (3 items): ${}", runningSubtotal);
        
        // Step 2: Check promotions for the cart
        List<Map<String, Object>> cartItems = new ArrayList<>();
        for (String sku : skus) {
            Map<String, Object> item = new HashMap<>();
            item.put("sku", sku);
            item.put("quantity", 1);
            cartItems.add(item);
        }
        
        Map<String, Object> promoRequest = new HashMap<>();
        promoRequest.put("items", cartItems);
        
        Response promoResponse = given()
            .spec(requestSpec)
            .body(promoRequest)
            .when()
            .post(promotionsServiceUrl + "/promotions/cart")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        List<Map<String, Object>> cartPromotions = promoResponse.jsonPath().getList("cart_promotions");
        Double estimatedDiscount = promoResponse.jsonPath().getDouble("total_discount_estimate");
        
        logger.info("  Found {} cart promotion(s)", cartPromotions != null ? cartPromotions.size() : 0);
        logger.info("  Estimated discount: ${}", estimatedDiscount != null ? estimatedDiscount : 0.0);
        
        // Step 3: Calculate tax on subtotal
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", runningSubtotal);
        taxRequest.put("region", TEST_REGION);
        taxRequest.put("items", cartItems); // Item-level tax calculation
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Double taxAmount = taxResponse.jsonPath().getDouble("tax_amount");
        Double total = taxResponse.jsonPath().getDouble("total");
        
        logger.info("  Tax: ${}, Total: ${}", taxAmount, total);
        assertTrue(total > runningSubtotal, "Total should be greater than subtotal (with tax)");
        
        // Step 4: Authorize payment
        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("amount", total);
        paymentRequest.put("currency", "USD");
        paymentRequest.put("card_number", TEST_CARD_APPROVED);
        paymentRequest.put("card_expiry", "12/25");
        paymentRequest.put("cvv", "123");
        paymentRequest.put("merchant_id", TEST_MERCHANT);
        paymentRequest.put("order_id", "ORDER-MULTI-" + System.currentTimeMillis());
        
        Response paymentResponse = given()
            .spec(requestSpec)
            .body(paymentRequest)
            .when()
            .post(paymentGatewayUrl + "/payment/authorize")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String status = paymentResponse.jsonPath().getString("status");
        String txnId = paymentResponse.jsonPath().getString("transaction_id");
        
        logger.info("  Payment Status: {}", status);
        logger.info("  Transaction ID: {}", txnId);
        
        assertEquals(status, "approved", "Multi-item payment should be approved");
        
        logger.info("✅ Multi-item transaction completed successfully!");
        logger.info("  Total items: {}, Total: ${}", skus.size(), total);
    }
    
    /**
     * Test: Invalid SKU Handling
     * 
     * Given: A customer tries to scan an invalid SKU
     * When: The POS looks up the price
     * Then: A 404 error is returned
     */
    @Test(description = "Invalid SKU returns 404")
    public void testInvalidSku() {
        logger.info("Starting invalid SKU test...");
        
        String invalidSku = "SKU-9999";
        
        Response priceResponse = given()
            .spec(requestSpec)
            .when()
            .get(pricingServiceUrl + "/price/" + invalidSku);
        
        assertEquals(priceResponse.statusCode(), 404, "Invalid SKU should return 404");
        
        String errorMessage = priceResponse.jsonPath().getString("message");
        logger.info("  Error message: {}", errorMessage);
        assertNotNull(errorMessage, "Error message should be provided");
        assertTrue(errorMessage.contains(invalidSku), "Error should reference the invalid SKU");
        
        logger.info("✅ Invalid SKU test passed!");
    }
}