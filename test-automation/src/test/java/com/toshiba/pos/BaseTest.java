// test-automation/src/test/java/com/toshiba/pos/BaseTest.java

package com.toshiba.pos;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeClass;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.HashMap;
import java.util.Map;

/**
 * Base test class for all POS test automation.
 * Handles configuration loading, setup, and shared specifications.
 */
public class BaseTest {
    
    protected static final Logger logger = LogManager.getLogger(BaseTest.class);
    protected static Dotenv dotenv;
    protected static RequestSpecification requestSpec;
    
    // Service URLs - configurable via environment variables
    protected static String pricingServiceUrl;
    protected static String promotionsServiceUrl;
    protected static String taxServiceUrl;
    protected static String paymentGatewayUrl;
    
    /**
     * Set up test environment once before all tests.
     * Loads configuration and initializes RestAssured.
     */
    @BeforeSuite
    public void setUpSuite() {
        logger.info("========================================");
        logger.info("  Toshiba POS Test Automation Suite");
        logger.info("========================================");
        
        // Load environment variables
        dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
        
        // Read service URLs from environment or use defaults
        pricingServiceUrl = getEnv("PRICING_SERVICE_URL", "http://localhost:8081");
        promotionsServiceUrl = getEnv("PROMOTIONS_SERVICE_URL", "http://localhost:8082");
        taxServiceUrl = getEnv("TAX_SERVICE_URL", "http://localhost:8083");
        paymentGatewayUrl = getEnv("PAYMENT_GATEWAY_URL", "http://localhost:8084");
        
        // Configure RestAssured
        RestAssured.baseURI = pricingServiceUrl; // Default, overridden per test
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        // Build shared request specification
        requestSpec = new RequestSpecBuilder()
            .setContentType("application/json")
            .setAccept("application/json")
            .log(LogDetail.URI)
            .log(LogDetail.METHOD)
            .log(LogDetail.BODY)
            .build();
        
        logConfiguration();
    }
    
    /**
     * Log the test configuration for debugging.
     */
    protected void logConfiguration() {
        logger.info("=== Test Configuration ===");
        logger.info("Pricing Service:    {}", pricingServiceUrl);
        logger.info("Promotions Service: {}", promotionsServiceUrl);
        logger.info("Tax Service:        {}", taxServiceUrl);
        logger.info("Payment Gateway:    {}", paymentGatewayUrl);
        logger.info("==========================");
    }
    
    /**
     * Get environment variable with default fallback.
     */
    protected String getEnv(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            value = dotenv.get(key);
        }
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get environment variable as integer with default fallback.
     */
    protected int getEnvInt(String key, int defaultValue) {
        String value = getEnv(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get environment variable as boolean with default fallback.
     */
    protected boolean getEnvBool(String key, boolean defaultValue) {
        String value = getEnv(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Build test data for a transaction.
     * Returns a map of test item data.
     */
    protected Map<String, Object> buildTestItem(String sku, int quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("sku", sku);
        item.put("quantity", quantity);
        return item;
    }
}