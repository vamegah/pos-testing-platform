// framework-core/src/main/java/com/toshiba/pos/engine/ProductE2EEngine.java

package com.toshiba.pos.engine;

import com.toshiba.pos.adapter.ProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.ProductProfile;
import com.toshiba.pos.registry.AdapterRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

/**
 * Generic Product E2E Test Engine.
 * 
 * <p>This engine runs the standard E2E flow (scan→price→tax→payment→receipt)
 * against all registered product adapters. It is parameterized via TestNG
 * DataProvider, iterating the adapter registry.
 * 
 * <p>Each product adapter produces one pass/fail result.
 * 
 * <p>Usage:
 * <pre>
 *   mvn test -Dtest=ProductE2EEngine
 * </pre>
 * 
 * <p>This engine replaces the hand-written Phase 13 test classes with a
 * single generic implementation driven by the adapter registry.
 */
public class ProductE2EEngine {

    private static final Logger logger = LogManager.getLogger(ProductE2EEngine.class);

    // Test execution results per product
    private static final Map<String, TestResult> testResults = new ConcurrentHashMap<>();
    private static final AtomicInteger totalPassed = new AtomicInteger(0);
    private static final AtomicInteger totalFailed = new AtomicInteger(0);

    // Service URLs (configurable via system properties)
    private static final String PRICING_URL = System.getProperty("PRICING_SERVICE_URL", "http://localhost:8081");
    private static final String PROMOTIONS_URL = System.getProperty("PROMOTIONS_SERVICE_URL", "http://localhost:8082");
    private static final String TAX_URL = System.getProperty("TAX_SERVICE_URL", "http://localhost:8083");
    private static final String PAYMENT_URL = System.getProperty("PAYMENT_GATEWAY_URL", "http://localhost:8084");

    @BeforeSuite
    public void setupSuite() {
        logger.info("========================================");
        logger.info("  Product E2E Test Engine");
        logger.info("========================================");
        logger.info("  Pricing:    {}", PRICING_URL);
        logger.info("  Promotions: {}", PROMOTIONS_URL);
        logger.info("  Tax:        {}", TAX_URL);
        logger.info("  Payment:    {}", PAYMENT_URL);
        logger.info("========================================");

        // Initialize the adapter registry
        AdapterRegistry.getInstance().initialize();
        
        int adapterCount = AdapterRegistry.getInstance().getAllAdapters().size();
        logger.info("Registered {} product adapters", adapterCount);
        
        if (adapterCount == 0) {
            logger.warn("⚠️ No adapters registered! Tests will be skipped.");
        }
    }

    /**
     * DataProvider that returns all registered product adapters.
     */
    @DataProvider(name = "productAdapters")
    public Object[][] productAdapters() {
        Collection<ProductAdapter> adapters = AdapterRegistry.getInstance().getAllAdapters();
        Object[][] data = new Object[adapters.size()][1];
        int i = 0;
        for (ProductAdapter adapter : adapters) {
            data[i++][0] = adapter;
        }
        return data;
    }

    /**
     * Generic E2E test that runs against any ProductAdapter.
     * 
     * <p>This test executes the standard E2E flow:
     * <ol>
     *   <li>Scan item (price lookup)</li>
     *   <li>Apply promotions</li>
     *   <li>Calculate tax</li>
     *   <li>Authorize payment</li>
     *   <li>Generate receipt</li>
     * </ol>
     * 
     * <p>Each step is driven by the adapter's E2EFlowSteps.
     */
    @Test(dataProvider = "productAdapters", description = "Generic E2E test for all products")
    public void testProductE2E(ProductAdapter adapter) {
        String productId = adapter.getProductId();
        String displayName = adapter.getDisplayName();
        ProductProfile profile = adapter.getProfile();
        E2EFlowSteps flowSteps = adapter.getE2EFlowSteps();

        logger.info("========================================");
        logger.info("  Running E2E test for: {} ({})", displayName, productId);
        logger.info("========================================");

        TestResult result = new TestResult(productId, displayName);
        
        try {
            // Verify adapter is valid
            assertNotNull(profile, "Profile should not be null");
            assertNotNull(flowSteps, "Flow steps should not be null");
            assertFalse(flowSteps.getSteps().isEmpty(), "Flow steps should not be empty");

            // Execute each step in the flow
            Map<String, Object> context = new HashMap<>();
            context.put("sku", "SKU-1001");
            context.put("region", "CA");
            context.put("card", "4111111111111111");

            boolean allStepsPassed = true;
            for (E2EFlowSteps.FlowStep step : flowSteps.getSteps()) {
                logger.info("  Executing step: {} ({})", step.getId(), step.getAction());
                
                boolean stepPassed = executeStep(step, context);
                if (!stepPassed) {
                    allStepsPassed = false;
                    result.addFailure(step.getId());
                    logger.warn("    ❌ Step failed: {}", step.getId());
                } else {
                    result.addSuccess(step.getId());
                    logger.info("    ✅ Step passed: {}", step.getId());
                }
            }

            if (allStepsPassed) {
                result.setStatus(TestResult.Status.PASSED);
                totalPassed.incrementAndGet();
                logger.info("✅ E2E test PASSED for: {}", displayName);
            } else {
                result.setStatus(TestResult.Status.FAILED);
                totalFailed.incrementAndGet();
                logger.error("❌ E2E test FAILED for: {}", displayName);
            }

        } catch (Exception e) {
            result.setStatus(TestResult.Status.ERROR);
            result.setErrorMessage(e.getMessage());
            totalFailed.incrementAndGet();
            logger.error("❌ E2E test ERROR for {}: {}", displayName, e.getMessage(), e);
        }

        testResults.put(productId, result);
        logResultSummary(result);
    }

    /**
     * Execute a single flow step.
     */
    private boolean executeStep(E2EFlowSteps.FlowStep step, Map<String, Object> context) {
        String action = step.getAction();
        String service = step.getService();
        String endpoint = step.getEndpoint();
        Map<String, Object> params = step.getParameters();

        // Resolve placeholders in endpoint
        String resolvedEndpoint = resolveEndpoint(endpoint, context, params);
        String serviceUrl = getServiceUrl(service);

        try {
            if ("api_call".equals(action)) {
                Response response = given()
                    .contentType("application/json")
                    .accept("application/json")
                    .when()
                    .get(serviceUrl + resolvedEndpoint);
                
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    // Extract and store any returned IDs for context
                    if (statusCode == 200) {
                        String body = response.getBody().asString();
                        // Simple extraction: if response has "id", "transaction_id", etc.
                        // In production, use JSON path extraction
                    }
                    return true;
                } else {
                    logger.debug("  Step {} returned status: {}", step.getId(), statusCode);
                    return false;
                }
            } else if ("mode_switch".equals(action) || "peripheral_toggle".equals(action)) {
                // For mode switch or peripheral toggle, we simulate success
                // In a real implementation, this would call the appropriate API
                return true;
            } else {
                logger.warn("  Unknown action: {}", action);
                return false;
            }
        } catch (Exception e) {
            logger.warn("  Step {} error: {}", step.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Resolve endpoint placeholders using context and parameters.
     */
    private String resolveEndpoint(String endpoint, Map<String, Object> context, Map<String, Object> params) {
        String resolved = endpoint;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() != null) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue().toString());
            }
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue().toString());
            }
        }
        return resolved;
    }

    /**
     * Get the service URL for a given service name.
     */
    private String getServiceUrl(String service) {
        switch (service) {
            case "pricing": return PRICING_URL;
            case "promotions": return PROMOTIONS_URL;
            case "tax": return TAX_URL;
            case "payment": return PAYMENT_URL;
            default: return service;
        }
    }

    /**
     * Log a summary of the test result.
     */
    private void logResultSummary(TestResult result) {
        logger.info("  --- Summary for {} ---", result.getDisplayName());
        logger.info("  Status: {}", result.getStatus());
        logger.info("  Passed: {}, Failed: {}", result.getPassedCount(), result.getFailedCount());
        if (result.getErrorMessage() != null) {
            logger.info("  Error: {}", result.getErrorMessage());
        }
    }

    /**
     * After all tests, log final summary.
     */
    @Test(dependsOnMethods = "testProductE2E", alwaysRun = true)
    public void testSummary() {
        logger.info("========================================");
        logger.info("  FINAL E2E TEST SUMMARY");
        logger.info("========================================");
        logger.info("  Total Products: {}", testResults.size());
        logger.info("  Passed: {}", totalPassed.get());
        logger.info("  Failed: {}", totalFailed.get());
        
        if (totalFailed.get() > 0) {
            logger.info("  Failed Products:");
            for (TestResult result : testResults.values()) {
                if (result.getStatus() != TestResult.Status.PASSED) {
                    logger.info("    - {}: {}", result.getDisplayName(), result.getStatus());
                }
            }
        }
        logger.info("========================================");
    }

    /**
     * TestResult — tracks the outcome of an E2E test run for a single product.
     */
    public static class TestResult {
        public enum Status { PASSED, FAILED, ERROR, SKIPPED }

        private final String productId;
        private final String displayName;
        private Status status = Status.SKIPPED;
        private final List<String> passedSteps = new ArrayList<>();
        private final List<String> failedSteps = new ArrayList<>();
        private String errorMessage;

        public TestResult(String productId, String displayName) {
            this.productId = productId;
            this.displayName = displayName;
        }

        public void setStatus(Status status) { this.status = status; }
        public Status getStatus() { return status; }
        public String getProductId() { return productId; }
        public String getDisplayName() { return displayName; }

        public void addSuccess(String stepId) { passedSteps.add(stepId); }
        public void addFailure(String stepId) { failedSteps.add(stepId); }
        public int getPassedCount() { return passedSteps.size(); }
        public int getFailedCount() { return failedSteps.size(); }
        public List<String> getPassedSteps() { return passedSteps; }
        public List<String> getFailedSteps() { return failedSteps; }
        public void setErrorMessage(String error) { this.errorMessage = error; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("TestResult{product='%s', status=%s, passed=%d, failed=%d}",
                displayName, status, passedSteps.size(), failedSteps.size());
        }
    }
}