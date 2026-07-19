// test-automation/src/test/java/com/toshiba/pos/contract/GatewayInventoryContractTest.java

package com.toshiba.pos.contract;

import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

/**
 * Contract test between API Gateway and Inventory Service.
 * 
 * Validates that the Gateway correctly forwards inventory requests
 * and handles responses from the Inventory service.
 */
public class GatewayInventoryContractTest extends ContractTestBase {

    private static String gatewayUrl;
    private static String inventoryUrl;
    private static final String TOKEN = "valid-token-123";

    @BeforeClass
    public void setUpClass() {
        gatewayUrl = getEnv("GATEWAY_URL", "http://localhost:5014");
        inventoryUrl = getEnv("INVENTORY_URL", "http://localhost:8085");
        logger.info("Gateway URL: {}", gatewayUrl);
        logger.info("Inventory URL: {}", inventoryUrl);
    }

    /**
     * Test: Gateway correctly routes inventory stock lookup.
     * 
     * Given: A valid SKU
     * When: A request is sent to Gateway /gateway/api/inventory/stock/{sku}
     * Then: The Gateway forwards to Inventory and returns the response
     */
    @Test(description = "Gateway → Inventory: Stock lookup contract")
    public void testGatewayInventoryStockLookup() {
        logger.info("=== Gateway → Inventory: Stock Lookup Contract ===");

        // Step 1: Direct call to Inventory (baseline)
        logger.info("Step 1: Direct Inventory call (baseline)");
        Response directResponse = given()
            .spec(requestSpec)
            .when()
            .get(inventoryUrl + "/inventory/stock/" + TEST_SKU)
            .then()
            .extract()
            .response();
        
        assertSuccess(directResponse);
        Integer directStock = directResponse.jsonPath().getInt("stock");
        logger.info("  Direct stock: {}", directStock);

        // Step 2: Call via Gateway
        logger.info("Step 2: Call via Gateway");
        Response gatewayResponse = given()
            .spec(requestSpec)
            .header("Authorization", "Bearer " + TOKEN)
            .when()
            .get(gatewayUrl + "/gateway/api/inventory/stock/" + TEST_SKU)
            .then()
            .extract()
            .response();
        
        assertSuccess(gatewayResponse);
        Integer gatewayStock = gatewayResponse.jsonPath().getInt("stock");
        logger.info("  Gateway stock: {}", gatewayStock);

        // Step 3: Compare results
        assertEquals(gatewayStock, directStock, "Stock should match between Gateway and direct call");
        logger.info("✅ Gateway → Inventory contract passed");
    }

    /**
     * Test: Gateway correctly routes inventory decrement.
     * 
     * Given: A valid SKU with sufficient stock
     * When: A decrement request is sent via Gateway
     * Then: The Gateway forwards to Inventory and returns the response
     */
    @Test(description = "Gateway → Inventory: Decrement contract")
    public void testGatewayInventoryDecrement() {
        logger.info("=== Gateway → Inventory: Decrement Contract ===");

        Map<String, Object> payload = Map.of(
            "quantity", 1,
            "transaction_id", "CONTRACT-TEST-001"
        );

        // Step 1: Direct call to Inventory (baseline)
        logger.info("Step 1: Direct Inventory call (baseline)");
        Response directResponse = given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(inventoryUrl + "/inventory/stock/" + TEST_SKU + "/decrement")
            .then()
            .extract()
            .response();
        
        assertSuccess(directResponse);
        Integer directRemaining = directResponse.jsonPath().getInt("remaining");
        logger.info("  Direct remaining: {}", directRemaining);

        // Step 2: Call via Gateway
        logger.info("Step 2: Call via Gateway");
        // Reset stock first
        resetInventory();
        
        Response gatewayResponse = given()
            .spec(requestSpec)
            .header("Authorization", "Bearer " + TOKEN)
            .body(payload)
            .when()
            .post(gatewayUrl + "/gateway/api/inventory/stock/" + TEST_SKU + "/decrement")
            .then()
            .extract()
            .response();
        
        assertSuccess(gatewayResponse);
        Integer gatewayRemaining = gatewayResponse.jsonPath().getInt("remaining");
        logger.info("  Gateway remaining: {}", gatewayRemaining);

        // Step 3: Compare results
        assertEquals(gatewayRemaining, directRemaining, "Remaining stock should match");
        logger.info("✅ Gateway → Inventory decrement contract passed");
    }

    private void resetInventory() {
        given().spec(requestSpec).post(inventoryUrl + "/test/reset");
    }
}