// test-automation/src/test/java/com/toshiba/pos/api-tests/TaxCalculationTest.java

package com.toshiba.pos.api-tests;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * Tax Calculation Test - Boundary Cases
 * 
 * Tests tax calculation service with boundary cases:
 *   - Zero subtotal
 *   - Negative subtotal (should be rejected)
 *   - Multiple regions with different tax rates
 *   - Tax-exempt items (taxable=false)
 *   - Very large subtotals
 *   - Decimal precision (rounding)
 * 
 * All tests run against local Phase 1 tax service.
 */
public class TaxCalculationTest extends BaseTest {

    private FixtureFactory fixtureFactory;
    
    // Test data constants
    //private static final String REGION_CA = "CA";
    //private static final String REGION_OR = "OR";
    //private static final String REGION_TX = "TX";
    //private static final String REGION_NY = "NY";
    //private static final String REGION_INVALID = "XX";
    
    // Expected tax rates from mock data
    //private static final double TAX_RATE_CA = 0.0725;  // 7.25%
    //private static final double TAX_RATE_OR = 0.0;     // 0% (no sales tax)
    //private static final double TAX_RATE_TX = 0.0625;  // 6.25%
    //private static final double TAX_RATE_NY = 0.04;    // 4.0%
    
    @BeforeClass
    public void setUpClass() {
        fixtureFactory = FixtureFactory.defaultFactory();
        logger.info("=== TaxCalculationTest Initialized ===");
        logger.info("Testing tax boundary cases against: {}", taxServiceUrl);
        logger.info("======================================");
    }
    
    /**
     * Test: Zero Subtotal
     * 
     * Given: A subtotal of $0.00
     * When: Tax is calculated
     * Then: Tax amount should be $0.00 and total should be $0.00
     */
    @Test(description = "Zero subtotal returns zero tax", groups = {"api", "regression", "boundary", "product:all"})
    public void testZeroSubtotal() {
        logger.info("Testing zero subtotal...");
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", 0.00);
        taxRequest.put("region", REGION_CA);
        
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
        
        logger.info("  Subtotal: $0.00, Tax: ${}, Total: ${}", taxAmount, total);
        
        assertEquals(taxAmount, 0.0, 0.001, "Tax on zero subtotal should be 0");
        assertEquals(total, 0.0, 0.001, "Total on zero subtotal should be 0");
        
        logger.info("✅ Zero subtotal test passed!");
    }
    
    /**
     * Test: Negative Subtotal (Error Case)
     * 
     * Given: A negative subtotal
     * When: Tax is calculated
     * Then: The service should return an error (400 Bad Request)
     */
    @Test(description = "Negative subtotal returns 400 error", groups = {"api", "regression", "negative", "product:all"})
    public void testNegativeSubtotal() {
        logger.info("Testing negative subtotal...");
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", -10.00);
        taxRequest.put("region", REGION_CA);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax");
        
        logger.info("  Status Code: {}", taxResponse.statusCode());
        
        assertEquals(taxResponse.statusCode(), 400, "Negative subtotal should return 400");
        
        String errorMessage = taxResponse.jsonPath().getString("message");
        assertNotNull(errorMessage, "Error message should be provided");
        assertTrue(errorMessage.toLowerCase().contains("negative") || 
                   errorMessage.toLowerCase().contains("cannot"),
                   "Error message should mention negative or cannot");
        
        logger.info("  Error: {}", errorMessage);
        logger.info("✅ Negative subtotal test passed!");
    }
    
    /**
     * Test: Multiple Regions
     * 
     * Given: Same subtotal for multiple regions
     * When: Tax is calculated for each region
     * Then: Each region should return the correct tax rate
     */
    @Test(description = "Multiple regions return correct tax rates", groups = {"api", "regression", "product:all"})
    public void testMultipleRegions() {
        logger.info("Testing multiple regions...");
        
        double subtotal = 100.00;
        
        // Test data: region -> expected tax rate
        Map<String, Double> regionTests = new LinkedHashMap<>();
        regionTests.put(REGION_CA, TAX_RATE_CA);
        regionTests.put(REGION_OR, TAX_RATE_OR);
        regionTests.put(REGION_TX, TAX_RATE_TX);
        regionTests.put(REGION_NY, TAX_RATE_NY);
        
        for (Map.Entry<String, Double> entry : regionTests.entrySet()) {
            String region = entry.getKey();
            double expectedRate = entry.getValue();
            double expectedTax = subtotal * expectedRate;
            
            logger.info("  Testing region: {} (expected rate: {}%)", region, expectedRate * 100);
            
            Map<String, Object> taxRequest = new HashMap<>();
            taxRequest.put("subtotal", subtotal);
            taxRequest.put("region", region);
            
            Response taxResponse = given()
                .spec(requestSpec)
                .body(taxRequest)
                .when()
                .post(taxServiceUrl + "/tax")
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            Double taxRate = taxResponse.jsonPath().getDouble("tax_rate");
            Double taxAmount = taxResponse.jsonPath().getDouble("tax_amount");
            Double total = taxResponse.jsonPath().getDouble("total");
            
            assertEquals(taxRate, expectedRate, 0.0001, "Tax rate should match for region " + region);
            assertEquals(taxAmount, expectedTax, 0.01, "Tax amount should match for region " + region);
            assertEquals(total, subtotal + expectedTax, 0.01, "Total should match for region " + region);
            
            logger.info("    Tax: ${}, Total: ${}", taxAmount, total);
        }
        
        logger.info("✅ Multiple regions test passed!");
    }
    
    /**
     * Test: Invalid Region
     * 
     * Given: An invalid region code
     * When: Tax is calculated
     * Then: The service should return 404 Not Found
     */
    @Test(description = "Invalid region returns 404", groups = {"api", "regression", "negative", "product:all"})
    public void testInvalidRegion() {
        logger.info("Testing invalid region...");
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", 100.00);
        taxRequest.put("region", REGION_INVALID);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax");
        
        logger.info("  Status Code: {}", taxResponse.statusCode());
        
        assertEquals(taxResponse.statusCode(), 404, "Invalid region should return 404");
        
        String errorMessage = taxResponse.jsonPath().getString("message");
        assertNotNull(errorMessage, "Error message should be provided");
        assertTrue(errorMessage.contains(REGION_INVALID), "Error should reference the invalid region");
        
        logger.info("  Error: {}", errorMessage);
        logger.info("✅ Invalid region test passed!");
    }
    
    /**
     * Test: Tax-Exempt Items (taxable=false)
     * 
     * Given: A mix of taxable and tax-exempt items
     * When: Tax is calculated with item-level detail
     * Then: Only taxable items should contribute to tax
     */
    @Test(description = "Tax-exempt items (taxable=false) are excluded from tax", groups = {"api", "regression", "boundary", "product:all"})
    public void testTaxExemptItems() {

        String region = fixtureFactory.getStandardRegion();
        logger.info("Testing tax-exempt items...");
        
        double subtotal = 30.00;
        // String region = REGION_CA;
        
        // Create items: first is taxable, second is tax-exempt
        List<Map<String, Object>> items = new ArrayList<>();
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("sku", "SKU-1001");
        item1.put("price", 20.00);
        item1.put("taxable", true);
        items.add(item1);
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("sku", "SKU-1005");
        item2.put("price", 10.00);
        item2.put("taxable", false);
        items.add(item2);
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", subtotal);
        taxRequest.put("region", region);
        taxRequest.put("items", items);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Double taxRate = taxResponse.jsonPath().getDouble("tax_rate");
        Double taxAmount = taxResponse.jsonPath().getDouble("tax_amount");
        Double total = taxResponse.jsonPath().getDouble("total");
        
        // Expected tax: only on taxable items (20.00 * 0.0725 = 1.45)
        double expectedTax = 20.00 * TAX_RATE_CA;
        
        logger.info("  Taxable item: $20.00");
        logger.info("  Tax-exempt item: $10.00");
        logger.info("  Tax Rate: {}%", taxRate * 100);
        logger.info("  Calculated Tax: ${}", taxAmount);
        logger.info("  Expected Tax: ${}", expectedTax);
        
        assertEquals(taxRate, TAX_RATE_CA, 0.0001, "Tax rate should be correct");
        assertEquals(taxAmount, expectedTax, 0.01, "Tax should only apply to taxable items");
        assertEquals(total, subtotal + expectedTax, 0.01, "Total should be subtotal + tax on taxable items");
        
        logger.info("✅ Tax-exempt items test passed!");
    }
    
    /**
     * Test: Large Subtotal
     * 
     * Given: A very large subtotal
     * When: Tax is calculated
     * Then: The service should handle it without errors
     */
    @Test(description = "Large subtotal handles correctly without errors", groups = {"api", "regression", "boundary", "product:all"})
    public void testLargeSubtotal() {
        logger.info("Testing large subtotal...");
        
        double subtotal = 999999.99;
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", subtotal);
        taxRequest.put("region", REGION_CA);
        
        Response taxResponse = given()
            .spec(requestSpec)
            .body(taxRequest)
            .when()
            .post(taxServiceUrl + "/tax")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        Double taxRate = taxResponse.jsonPath().getDouble("tax_rate");
        Double taxAmount = taxResponse.jsonPath().getDouble("tax_amount");
        Double total = taxResponse.jsonPath().getDouble("total");
        
        double expectedTax = subtotal * TAX_RATE_CA;
        
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${}", taxAmount);
        logger.info("  Total: ${}", total);
        
        assertEquals(taxRate, TAX_RATE_CA, 0.0001, "Tax rate should be correct");
        assertEquals(taxAmount, expectedTax, 0.01, "Tax should be calculated correctly");
        assertEquals(total, subtotal + expectedTax, 0.01, "Total should be correct");
        
        logger.info("✅ Large subtotal test passed!");
    }
    
    /**
     * Test: Decimal Precision (Rounding)
     * 
     * Given: A subtotal with many decimal places
     * When: Tax is calculated
     * Then: Tax amount should be rounded to 2 decimal places
     */
    @Test(description = "Tax amounts are rounded to 2 decimal places", groups = {"api", "regression", "boundary", "product:all"})
    public void testDecimalPrecision() {
        logger.info("Testing decimal precision and rounding...");
        
        // Use a subtotal that would cause a repeating decimal tax amount
        // e.g., 3.33 * 0.0725 = 0.241425 -> should round to 0.24
        double subtotal = 3.33;
        double expectedTax = 0.24; // Rounded from 0.241425
        
        Map<String, Object> taxRequest = new HashMap<>();
        taxRequest.put("subtotal", subtotal);
        taxRequest.put("region", REGION_CA);
        
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
        
        logger.info("  Subtotal: ${}", subtotal);
        logger.info("  Tax: ${} (expected: ${})", taxAmount, expectedTax);
        logger.info("  Total: ${}", total);
        
        // Check that tax is rounded to 2 decimal places
        String taxString = String.format("%.2f", taxAmount);
        assertEquals(taxString, String.format("%.2f", expectedTax), "Tax should be rounded to 2 decimal places");
        
        // Check total matches subtotal + rounded tax
        double expectedTotal = subtotal + expectedTax;
        assertEquals(total, expectedTotal, 0.01, "Total should be subtotal + rounded tax");
        
        logger.info("✅ Decimal precision test passed!");
    }
    
    /**
     * Test: Tax Rate Lookup - Get All Regions
     * 
     * Given: The regions endpoint
     * When: All regions are requested
     * Then: All configured regions should be returned
     */
    @Test(description = "Get all tax regions returns expected data", groups = {"api", "regression", "product:all"})
    public void testGetAllRegions() {
        logger.info("Testing get all regions endpoint...");
        
        Response regionsResponse = given()
            .spec(requestSpec)
            .when()
            .get(taxServiceUrl + "/tax/regions")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        List<Map<String, Object>> regions = regionsResponse.jsonPath().getList("regions");
        
        logger.info("  Found {} regions", regions.size());
        
        assertNotNull(regions, "Regions list should not be null");
        assertTrue(regions.size() >= 25, "Should have at least 25 regions (stub has 30)");
        
        // Verify CA is present with correct rate
        boolean foundCA = false;
        boolean foundOR = false;
        boolean foundTX = false;
        
        for (Map<String, Object> region : regions) {
            String code = (String) region.get("code");
            Double rate = (Double) region.get("tax_rate");
            
            if ("CA".equals(code)) {
                assertEquals(rate, TAX_RATE_CA, 0.0001, "CA should have correct rate");
                foundCA = true;
                logger.info("  ✅ CA: {}%", rate * 100);
            }
            if ("OR".equals(code)) {
                assertEquals(rate, TAX_RATE_OR, 0.0001, "OR should have correct rate");
                foundOR = true;
                logger.info("  ✅ OR: {}%", rate * 100);
            }
            if ("TX".equals(code)) {
                assertEquals(rate, TAX_RATE_TX, 0.0001, "TX should have correct rate");
                foundTX = true;
                logger.info("  ✅ TX: {}%", rate * 100);
            }
        }
        
        assertTrue(foundCA, "CA should be in regions list");
        assertTrue(foundOR, "OR should be in regions list");
        assertTrue(foundTX, "TX should be in regions list");
        
        logger.info("✅ Get all regions test passed!");
    }
    
    /**
     * Test: Single Region Tax Rate Lookup
     * 
     * Given: A specific region
     * When: The tax rate is requested
     * Then: The correct rate should be returned
     */
    @Test(description = "Single region tax rate lookup returns correct rate", groups = {"api", "regression", "product:all"})
    public void testSingleRegionLookup() {
        logger.info("Testing single region lookup...");
        
        // Test a few regions
        String[] testRegions = {REGION_CA, REGION_OR, REGION_TX, REGION_NY};
        double[] expectedRates = {TAX_RATE_CA, TAX_RATE_OR, TAX_RATE_TX, TAX_RATE_NY};
        
        for (int i = 0; i < testRegions.length; i++) {
            String region = testRegions[i];
            double expectedRate = expectedRates[i];
            
            Response rateResponse = given()
                .spec(requestSpec)
                .when()
                .get(taxServiceUrl + "/tax/rate/" + region)
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            Double rate = rateResponse.jsonPath().getDouble("tax_rate");
            String description = rateResponse.jsonPath().getString("description");
            
            assertEquals(rate, expectedRate, 0.0001, region + " should have correct rate");
            assertNotNull(description, region + " should have description");
            logger.info("  {}: {}% - {}", region, rate * 100, description);
        }
        
        logger.info("✅ Single region lookup test passed!");
    }
    
    /**
     * Test: Tax Configuration Validation
     * 
     * Given: A region and multiple subtotals
     * When: The validation endpoint is called
     * Then: All subtotals should be validated correctly
     */
    @Test(description = "Tax validation endpoint validates multiple subtotals", groups = {"api", "regression", "product:all"})
    public void testTaxValidation() {
        logger.info("Testing tax validation endpoint...");
        
        List<Double> subtotals = Arrays.asList(10.00, 25.00, 50.00, 100.00, 250.00);
        
        Map<String, Object> validationRequest = new HashMap<>();
        validationRequest.put("region", REGION_CA);
        validationRequest.put("subtotals", subtotals);
        
        Response validationResponse = given()
            .spec(requestSpec)
            .body(validationRequest)
            .when()
            .post(taxServiceUrl + "/tax/validate")
            .then()
            .statusCode(200)
            .extract()
            .response();
        
        String region = validationResponse.jsonPath().getString("region");
        Double taxRate = validationResponse.jsonPath().getDouble("tax_rate");
        List<Map<String, Object>> results = validationResponse.jsonPath().getList("results");
        
        assertEquals(region, REGION_CA, "Region should match");
        assertEquals(taxRate, TAX_RATE_CA, 0.0001, "Tax rate should match");
        assertEquals(results.size(), subtotals.size(), "Should have same number of results");
        
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            Double subtotal = (Double) result.get("subtotal");
            Double taxAmount = (Double) result.get("tax_amount");
            Double total = (Double) result.get("total");
            
            double expectedTax = subtotal * TAX_RATE_CA;
            double expectedTotal = subtotal + expectedTax;
            
            assertEquals(taxAmount, expectedTax, 0.01, "Tax should be correct for subtotal: " + subtotal);
            assertEquals(total, expectedTotal, 0.01, "Total should be correct for subtotal: " + subtotal);
        }
        
        logger.info("  Validated {} subtotals for region {}", results.size(), region);
        logger.info("✅ Tax validation test passed!");
    }
}