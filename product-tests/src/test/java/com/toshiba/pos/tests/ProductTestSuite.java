// product-tests/src/test/java/com/toshiba/pos/tests/ProductTestSuite.java

package com.toshiba.pos.tests;

import com.toshiba.pos.engine.ProductE2EEngine;
import com.toshiba.pos.registry.AdapterRegistry;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

/**
 * Product Test Suite.
 * 
 * <p>This is the main test suite for all product E2E tests.
 * It delegates to the generic ProductE2EEngine, which runs
 * tests against all registered product adapters.
 * 
 * <p>This module depends on framework-core and provides
 * the execution entry point for product-specific testing.
 */
public class ProductTestSuite {

    @BeforeSuite
    public void setup() {
        System.out.println("========================================");
        System.out.println("  Product Test Suite");
        System.out.println("  Framework Version: " + 
            getClass().getPackage().getImplementationVersion());
        System.out.println("========================================");
    }

    /**
     * Run E2E tests for all registered products.
     */
    @Test
    public void testAllProducts() {
        // This will be invoked by TestNG's suite
        // The actual test logic is in ProductE2EEngine
        // We instantiate it to ensure it's loaded
        ProductE2EEngine engine = new ProductE2EEngine();
        // The test is run via TestNG's @Test on the engine class
    }

    /**
     * Get information about registered adapters.
     */
    @Test
    public void testAdapterRegistryInfo() {
        AdapterRegistry registry = AdapterRegistry.getInstance();
        registry.initialize();
        
        System.out.println("Registered Adapters:");
        for (String productId : registry.getProductIds()) {
            System.out.println("  - " + productId);
        }
    }
}