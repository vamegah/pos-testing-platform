// test-automation/src/test/java/com/toshiba/pos/e2e/TcxPrinterDualE2ETest.java

package com.toshiba.pos.e2e;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * TCx® Dual Station Printer E2E Test
 * 
 * Validates the complete flow for the TCx Dual Station Printer:
 *   1. Sale prints to both stations (customer receipt + merchant journal)
 *   2. One station's simulated jam doesn't block the other station's output
 * 
 * Verifies independence of the two stations explicitly.
 */
public class TcxPrinterDualE2ETest extends BaseTest {

    // TCx Dual Station Printer simulator URL
    private static String printerUrl;

    // Test data
    private static final String TEST_REGION = "CA";
    private static final String TEST_CARD = "4111111111111111";
    private static final String[] TEST_SKUS = {"SKU-1001", "SKU-1002", "SKU-1005"};

    @BeforeClass
    public void setUpClass() {
        printerUrl = getEnv("TCX_PRINTER_DUAL_URL", "http://localhost:5008");
        logger.info("=== TCx Dual Station Printer E2E Test Initialized ===");
        logger.info("  Printer URL: {}", printerUrl);
        logger.info("======================================================");
    }

    /**
     * Helper: Build a test basket.
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
     * Helper: Get all station statuses.
     */
    private Response getStations() {
        return given()
            .spec(requestSpec)
            .when()
            .get(printerUrl + "/tcx-printer-dual/stations")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Get specific station status.
     */
    private Response getStationStatus(String station) {
        return given()
            .spec(requestSpec)
            .when()
            .get(printerUrl + "/tcx-printer-dual/station/status/" + station)
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Simulate a station condition.
     */
    private Response simulateStation(String station, String condition, boolean simulate, int durationSeconds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("station", station);
        payload.put("condition", condition);
        payload.put("simulate", simulate);
        if (durationSeconds > 0) {
            payload.put("duration_seconds", durationSeconds);
        }
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(printerUrl + "/tcx-printer-dual/station/simulate")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Print customer receipt only.
     */
    private Response printCustomer(Map<String, Object> payload) {
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(printerUrl + "/tcx-printer-dual/print/customer")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Print merchant journal only.
     */
    private Response printMerchant(Map<String, Object> payload) {
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(printerUrl + "/tcx-printer-dual/print/merchant")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Print to both stations.
     */
    private Response printBoth(Map<String, Object> payload) {
        return given()
            .spec(requestSpec)
            .body(payload)
            .when()
            .post(printerUrl + "/tcx-printer-dual/print/both")
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
            .post(printerUrl + "/tcx-printer-dual/transaction")
            .then()
            .extract()
            .response();
    }

    /**
     * Helper: Reset printer state.
     */
    private void resetPrinterState() {
        simulateStation("customer", "out", false, 0);
        simulateStation("customer", "jam", false, 0);
        simulateStation("merchant", "out", false, 0);
        simulateStation("merchant", "jam", false, 0);
    }

    /**
     * Test: TCx Dual Station Printer — Happy Path Sale to Both Stations
     * 
     * Given: A sale is processed
     * When: Both stations have paper
     * Then: Both stations print successfully
     */
    @Test(description = "TCx Dual Station Printer — Happy path sale to both stations")
    public void testHappyPathBothStations() {
        logger.info("=== TCx Dual Station Printer: Happy Path Both Stations ===");
        
        // Step 1: Reset printer state
        logger.info("Step 1: Resetting printer state");
        resetPrinterState();
        
        // Step 2: Verify both stations
        logger.info("Step 2: Verifying both stations");
        Response stationsResponse = getStations();
        assertEquals(stationsResponse.statusCode(), 200, "Should get stations");
        
        Map<String, Object> stations = stationsResponse.jsonPath().getMap("stations");
        Map<String, Object> customer = (Map<String, Object>) stations.get("customer");
        Map<String, Object> merchant = (Map<String, Object>) stations.get("merchant");
        
        logger.info("  Customer station status: {}", customer.get("status"));
        logger.info("  Merchant station status: {}", merchant.get("status"));
        
        assertEquals(customer.get("status"), "available", "Customer station should be available");
        assertEquals(merchant.get("status"), "available", "Merchant station should be available");
        
        // Step 3: Build basket
        logger.info("Step 3: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        logger.info("  Basket items: {}", items.size());
        
        // Step 4: Process transaction
        logger.info("Step 4: Processing transaction");
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        Response transactionResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        Double total = transactionResponse.jsonPath().getDouble("total");
        String paymentStatus = transactionResponse.jsonPath().getString("payment.status");
        
        // Verify printing results
        Map<String, Object> printing = transactionResponse.jsonPath().getMap("printing");
        Map<String, Object> customerResult = (Map<String, Object>) printing.get("customer");
        Map<String, Object> merchantResult = (Map<String, Object>) printing.get("merchant");
        
        String customerStatus = (String) customerResult.get("status");
        String merchantStatus = (String) merchantResult.get("status");
        Boolean customerPrinted = (Boolean) customerResult.get("virtual_printed");
        Boolean merchantPrinted = (Boolean) merchantResult.get("virtual_printed");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Total: ${}", total);
        logger.info("  Payment status: {}", paymentStatus);
        logger.info("  Customer receipt status: {}", customerStatus);
        logger.info("  Merchant receipt status: {}", merchantStatus);
        logger.info("  Customer virtual printed: {}", customerPrinted);
        logger.info("  Merchant virtual printed: {}", merchantPrinted);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        assertEquals(customerStatus, "printed", "Customer receipt should be printed");
        assertEquals(merchantStatus, "printed", "Merchant receipt should be printed");
        assertTrue(customerPrinted, "Customer receipt should be virtual printed");
        assertTrue(merchantPrinted, "Merchant receipt should be virtual printed");
        assertTrue(total > 0, "Total should be greater than 0");
        
        // Step 5: Verify station print counts increased
        logger.info("Step 5: Verifying print counts increased");
        Response finalStations = getStations();
        Map<String, Object> finalCustomer = (Map<String, Object>) finalStations.jsonPath().getMap("stations.customer");
        Map<String, Object> finalMerchant = (Map<String, Object>) finalStations.jsonPath().getMap("stations.merchant");
        
        logger.info("  Customer print count: {}", finalCustomer.get("print_count"));
        logger.info("  Merchant print count: {}", finalMerchant.get("print_count"));
        
        assertTrue((Integer) finalCustomer.get("print_count") > 0, "Customer print count should be > 0");
        assertTrue((Integer) finalMerchant.get("print_count") > 0, "Merchant print count should be > 0");
        
        logger.info("✅ TCx Dual Station Printer: Happy path both stations completed successfully");
        logger.info("  Total: ${}, Both stations printed", total);
    }

    /**
     * Test: TCx Dual Station Printer — Customer Station Jam, Merchant Works
     * 
     * Given: Customer station has a jam
     * When: Printing to both stations
     * Then: Customer station fails but merchant station works
     */
    @Test(description = "TCx Dual Station Printer — Customer station jam, merchant works")
    public void testCustomerJamMerchantWorks() {
        logger.info("=== TCx Dual Station Printer: Customer Jam, Merchant Works ===");
        
        // Step 1: Reset printer state
        logger.info("Step 1: Resetting printer state");
        resetPrinterState();
        
        // Step 2: Simulate jam on customer station
        logger.info("Step 2: Simulating jam on customer station");
        Response jamResponse = simulateStation("customer", "jam", true, 0);
        assertEquals(jamResponse.statusCode(), 200, "Jam simulation should succeed");
        String jamStatus = jamResponse.jsonPath().getString("paper_status");
        logger.info("  Customer station status: {}", jamStatus);
        assertEquals(jamStatus, "jam", "Customer station should be jammed");
        
        // Step 3: Verify station statuses
        logger.info("Step 3: Verifying station statuses");
        Response statusResponse = getStations();
        Map<String, Object> customer = (Map<String, Object>) statusResponse.jsonPath().getMap("stations.customer");
        Map<String, Object> merchant = (Map<String, Object>) statusResponse.jsonPath().getMap("stations.merchant");
        
        logger.info("  Customer status: {}", customer.get("status"));
        logger.info("  Merchant status: {}", merchant.get("status"));
        
        assertEquals(customer.get("status"), "jam", "Customer should be jammed");
        assertEquals(merchant.get("status"), "available", "Merchant should be available");
        
        // Step 4: Build print payload
        logger.info("Step 4: Building print payload");
        List<Map<String, Object>> items = buildTestBasket();
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "txn-jam-" + System.currentTimeMillis());
        payload.put("items", items);
        payload.put("total", 10.00);
        payload.put("tax", 0.72);
        payload.put("subtotal", 9.28);
        
        // Step 5: Print to both stations
        logger.info("Step 5: Printing to both stations with customer jam");
        Response bothResponse = printBoth(payload);
        assertEquals(bothResponse.statusCode(), 200, "Print both should complete");
        
        Map<String, Object> customerResult = bothResponse.jsonPath().getMap("customer");
        Map<String, Object> merchantResult = bothResponse.jsonPath().getMap("merchant");
        
        String customerStatus = (String) customerResult.get("status");
        String merchantStatus = (String) merchantResult.get("status");
        
        logger.info("  Customer result status: {}", customerStatus);
        logger.info("  Merchant result status: {}", merchantStatus);
        
        // Customer should fail with jam, merchant should succeed
        assertEquals(customerStatus, "error", "Customer should fail with jam");
        assertEquals(merchantStatus, "printed", "Merchant should print successfully");
        
        // Verify error details
        String error = (String) customerResult.get("error");
        String paperStatus = (String) customerResult.get("paper_status");
        logger.info("  Customer error: {}", error);
        logger.info("  Customer paper status: {}", paperStatus);
        
        assertNotNull(error, "Error should be provided");
        assertTrue(error.contains("jam"), "Error should mention jam");
        assertEquals(paperStatus, "jam", "Paper status should be jam");
        
        // Step 6: Verify merchant printed even with customer jam
        logger.info("Step 6: Verifying merchant printed successfully");
        assertTrue((Boolean) merchantResult.get("virtual_printed"), "Merchant should be virtual printed");
        
        // Step 7: Verify print counts
        logger.info("Step 7: Verifying print counts");
        Response finalStations = getStations();
        Map<String, Object> finalCustomer = (Map<String, Object>) finalStations.jsonPath().getMap("stations.customer");
        Map<String, Object> finalMerchant = (Map<String, Object>) finalStations.jsonPath().getMap("stations.merchant");
        
        logger.info("  Customer error count: {}", finalCustomer.get("error_count"));
        logger.info("  Merchant print count: {}", finalMerchant.get("print_count"));
        
        assertTrue((Integer) finalCustomer.get("error_count") > 0, "Customer error count should increase");
        assertTrue((Integer) finalMerchant.get("print_count") > 0, "Merchant print count should increase");
        
        logger.info("✅ TCx Dual Station Printer: Customer jam, merchant works verified");
        logger.info("  One station's jam did NOT block the other station's output");
    }

    /**
     * Test: TCx Dual Station Printer — Merchant Station Jam, Customer Works
     * 
     * Given: Merchant station has a jam
     * When: Printing to both stations
     * Then: Merchant station fails but customer station works
     */
    @Test(description = "TCx Dual Station Printer — Merchant station jam, customer works")
    public void testMerchantJamCustomerWorks() {
        logger.info("=== TCx Dual Station Printer: Merchant Jam, Customer Works ===");
        
        // Step 1: Reset printer state
        logger.info("Step 1: Resetting printer state");
        resetPrinterState();
        
        // Step 2: Simulate jam on merchant station
        logger.info("Step 2: Simulating jam on merchant station");
        Response jamResponse = simulateStation("merchant", "jam", true, 0);
        assertEquals(jamResponse.statusCode(), 200, "Jam simulation should succeed");
        String jamStatus = jamResponse.jsonPath().getString("paper_status");
        logger.info("  Merchant station status: {}", jamStatus);
        assertEquals(jamStatus, "jam", "Merchant station should be jammed");
        
        // Step 3: Verify station statuses
        logger.info("Step 3: Verifying station statuses");
        Response statusResponse = getStations();
        Map<String, Object> customer = (Map<String, Object>) statusResponse.jsonPath().getMap("stations.customer");
        Map<String, Object> merchant = (Map<String, Object>) statusResponse.jsonPath().getMap("stations.merchant");
        
        logger.info("  Customer status: {}", customer.get("status"));
        logger.info("  Merchant status: {}", merchant.get("status"));
        
        assertEquals(customer.get("status"), "available", "Customer should be available");
        assertEquals(merchant.get("status"), "jam", "Merchant should be jammed");
        
        // Step 4: Build print payload
        logger.info("Step 4: Building print payload");
        List<Map<String, Object>> items = buildTestBasket();
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "txn-merchant-jam-" + System.currentTimeMillis());
        payload.put("items", items);
        payload.put("total", 10.00);
        payload.put("tax", 0.72);
        payload.put("subtotal", 9.28);
        
        // Step 5: Print to both stations
        logger.info("Step 5: Printing to both stations with merchant jam");
        Response bothResponse = printBoth(payload);
        assertEquals(bothResponse.statusCode(), 200, "Print both should complete");
        
        Map<String, Object> customerResult = bothResponse.jsonPath().getMap("customer");
        Map<String, Object> merchantResult = bothResponse.jsonPath().getMap("merchant");
        
        String customerStatus = (String) customerResult.get("status");
        String merchantStatus = (String) merchantResult.get("status");
        
        logger.info("  Customer result status: {}", customerStatus);
        logger.info("  Merchant result status: {}", merchantStatus);
        
        // Customer should succeed, merchant should fail with jam
        assertEquals(customerStatus, "printed", "Customer should print successfully");
        assertEquals(merchantStatus, "error", "Merchant should fail with jam");
        
        // Verify error details
        String error = (String) merchantResult.get("error");
        String paperStatus = (String) merchantResult.get("paper_status");
        logger.info("  Merchant error: {}", error);
        logger.info("  Merchant paper status: {}", paperStatus);
        
        assertNotNull(error, "Error should be provided");
        assertTrue(error.contains("jam"), "Error should mention jam");
        assertEquals(paperStatus, "jam", "Paper status should be jam");
        
        // Step 6: Verify customer printed even with merchant jam
        logger.info("Step 6: Verifying customer printed successfully");
        assertTrue((Boolean) customerResult.get("virtual_printed"), "Customer should be virtual printed");
        
        logger.info("✅ TCx Dual Station Printer: Merchant jam, customer works verified");
        logger.info("  One station's jam did NOT block the other station's output");
    }

    /**
     * Test: TCx Dual Station Printer — One Station Fault Doesn't Block Transaction
     * 
     * Given: A full transaction with one station jammed
     * When: The transaction is processed
     * Then: The transaction completes, one station prints successfully
     */
    @Test(description = "TCx Dual Station Printer — One station fault doesn't block transaction")
    public void testTransactionWithOneStationJammed() {
        logger.info("=== TCx Dual Station Printer: Transaction with One Station Jammed ===");
        
        // Step 1: Reset printer state
        logger.info("Step 1: Resetting printer state");
        resetPrinterState();
        
        // Step 2: Simulate jam on customer station
        logger.info("Step 2: Simulating jam on customer station");
        simulateStation("customer", "jam", true, 0);
        
        // Step 3: Build basket
        logger.info("Step 3: Building basket");
        List<Map<String, Object>> items = buildTestBasket();
        Map<String, Object> payment = new HashMap<>();
        payment.put("card_number", TEST_CARD);
        
        // Step 4: Process transaction (prints to both stations)
        logger.info("Step 4: Processing transaction with customer station jammed");
        Response transactionResponse = processTransaction(items, TEST_REGION, payment);
        assertEquals(transactionResponse.statusCode(), 200, "Transaction should succeed");
        
        String status = transactionResponse.jsonPath().getString("status");
        String paymentStatus = transactionResponse.jsonPath().getString("payment.status");
        Map<String, Object> printing = transactionResponse.jsonPath().getMap("printing");
        
        logger.info("  Transaction status: {}", status);
        logger.info("  Payment status: {}", paymentStatus);
        
        assertEquals(status, "completed", "Transaction should be completed");
        assertEquals(paymentStatus, "approved", "Payment should be approved");
        
        // Verify print results
        Map<String, Object> customerResult = (Map<String, Object>) printing.get("customer");
        Map<String, Object> merchantResult = (Map<String, Object>) printing.get("merchant");
        
        String customerStatus = (String) customerResult.get("status");
        String merchantStatus = (String) merchantResult.get("status");
        
        logger.info("  Customer print: {}", customerStatus);
        logger.info("  Merchant print: {}", merchantStatus);
        
        // Customer should fail, merchant should succeed
        assertEquals(customerStatus, "error", "Customer should fail with jam");
        assertEquals(merchantStatus, "printed", "Merchant should print successfully");
        
        // Step 5: Verify transaction was not lost
        logger.info("Step 5: Verifying transaction completed successfully");
        Double total = transactionResponse.jsonPath().getDouble("total");
        logger.info("  Total: ${}", total);
        assertTrue(total > 0, "Total should be greater than 0");
        
        // Step 6: Clear jam and retry
        logger.info("Step 6: Clearing customer jam");
        simulateStation("customer", "jam", false, 0);
        
        // Step 7: Print only customer receipt (retry)
        logger.info("Step 7: Retrying customer receipt");
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", "txn-retry-" + System.currentTimeMillis());
        payload.put("items", items);
        payload.put("total", total);
        payload.put("tax", transactionResponse.jsonPath().getDouble("tax"));
        payload.put("subtotal", transactionResponse.jsonPath().getDouble("subtotal"));
        
        Response retryResponse = printCustomer(payload);
        assertEquals(retryResponse.statusCode(), 200, "Retry should succeed");
        String retryStatus = retryResponse.jsonPath().getString("status");
        logger.info("  Retry status: {}", retryStatus);
        assertEquals(retryStatus, "printed", "Customer receipt should print after jam cleared");
        
        logger.info("✅ TCx Dual Station Printer: One station fault doesn't block transaction verified");
        logger.info("  Transaction completed, one station printed, retry succeeded after jam cleared");
    }
}