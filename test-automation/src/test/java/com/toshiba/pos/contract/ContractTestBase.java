// test-automation/src/test/java/com/toshiba/pos/contract/ContractTestBase.java

package com.toshiba.pos.contract;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

/**
 * Base class for contract tests between dependent mock services.
 * 
 * Each contract test validates that one service correctly calls another
 * with the expected request/response schema.
 */
public abstract class ContractTestBase extends BaseTest {

    protected static final String TEST_SKU = "SKU-1001";
    protected static final String TEST_CUSTOMER_ID = "CUST-TEST-001";
    protected static final String TEST_ORDER_ID = "ORD-TEST-001";
    
    /**
     * Assert that a request returns a 200 OK response.
     */
    protected void assertSuccess(Response response) {
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
            "Expected 2xx, got " + response.statusCode() + ": " + response.getBody().asString());
    }
    
    /**
     * Assert that a request returns a 4xx error response.
     */
    protected void assertFailure(Response response) {
        assertTrue(response.statusCode() >= 400 && response.statusCode() < 500,
            "Expected 4xx, got " + response.statusCode() + ": " + response.getBody().asString());
    }
}