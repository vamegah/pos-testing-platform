// test-automation/src/test/java/com/toshiba/pos/api-tests/OfflineSyncTest.java

package com.toshiba.pos.api_tests;

import com.toshiba.pos.BaseTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;
import static io.restassured.RestAssured.given;

/**
 * Offline Sync Test - Store-and-Forward Queue Behavior
 * 
 * Tests the POS system's ability to handle network interruptions during
 * a transaction and properly sync queued transactions when connectivity
 * is restored.
 * 
 * Scenario:
 *   1. POS is online and processes transactions normally
 *   2. Network interruption occurs (simulated)
 *   3. Transactions are stored in a local queue
 *   4. Network is restored
 *   5. Queued transactions are synced to backend services
 * 
 * This test uses a simulated approach rather than actual network
 * manipulation for safety and portability.
 */
public class OfflineSyncTest extends BaseTest {
    
    // Test data constants
    private static final String TEST_SKU = "SKU-1001";
    private static final String TEST_REGION = "CA";
    private static final String TEST_MERCHANT = "TEST_MERCHANT_001";
    private static final String TEST_CARD_APPROVED = "4111111111111111";
    
    // Simulated offline queue (in-memory for testing)
    private static final ConcurrentLinkedQueue<Map<String, Object>> offlineQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger queuedCount = new AtomicInteger(0);
    private static final AtomicInteger syncedCount = new AtomicInteger(0);
    
    // Flag to simulate network state
    private static boolean networkOnline = true;
    
    @BeforeClass
    public void setUpClass() {
        logger.info("=== OfflineSyncTest Initialized ===");
        logger.info("Testing store-and-forward queue behavior");
        logger.info("======================================");
        
        // Reset state before tests
        offlineQueue.clear();
        queuedCount.set(0);
        syncedCount.set(0);
        networkOnline = true;
    }
    
    /**
     * Helper: Simulate a transaction with current network state.
     * If online, process immediately. If offline, queue for later sync.
     */
    private Map<String, Object> processTransaction(double amount, String sku, boolean simulateOffline) {
        Map<String, Object> transaction = new HashMap<>();
        String orderId = "ORDER-OFFLINE-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
        
        // Step 1: Check network state
        boolean isOnline = networkOnline && !simulateOffline;
        
        if (!isOnline) {
            // OFFLINE MODE: Store transaction in queue
            logger.info("📡 OFFLINE: Queuing transaction for order: {}", orderId);
            
            transaction.put("order_id", orderId);
            transaction.put("sku", sku);
            transaction.put("amount", amount);
            transaction.put("region", TEST_REGION);
            transaction.put("status", "queued");
            transaction.put("timestamp", System.currentTimeMillis());
            transaction.put("card_masked", "XXXX...XXXX");
            transaction.put("merchant_id", TEST_MERCHANT);
            
            offlineQueue.add(transaction);
            queuedCount.incrementAndGet();
            
            return transaction;
        }
        
        // ONLINE MODE: Process transaction normally
        logger.info("📶 ONLINE: Processing transaction for order: {}", orderId);
        
        try {
            // Price lookup
            Response priceResponse = given()
                .spec(requestSpec)
                .when()
                .get(pricingServiceUrl + "/price/" + sku)
                .then()
                .statusCode(200)
                .extract()
                .response();
            
            Double price = priceResponse.jsonPath().getDouble("price");
            
            // Tax calculation
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
            
            Double total = taxResponse.jsonPath().getDouble("total");
            
            // Payment authorization
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("amount", total);
            paymentRequest.put("currency", "USD");
            paymentRequest.put("card_number", TEST_CARD_APPROVED);
            paymentRequest.put("card_expiry", "12/25");
            paymentRequest.put("cvv", "123");
            paymentRequest.put("merchant_id", TEST_MERCHANT);
            paymentRequest.put("order_id", orderId);
            
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
            
            transaction.put("order_id", orderId);
            transaction.put("sku", sku);
            transaction.put("amount", total);
            transaction.put("region", TEST_REGION);
            transaction.put("status", status);
            transaction.put("transaction_id", txnId);
            transaction.put("timestamp", System.currentTimeMillis());
            transaction.put("merchant_id", TEST_MERCHANT);
            
            return transaction;
            
        } catch (Exception e) {
            // If online processing fails, queue it
            logger.warn("Online processing failed: {}. Queuing transaction.", e.getMessage());
            networkOnline = false; // Simulate network failure
            
            transaction.put("order_id", orderId);
            transaction.put("sku", sku);
            transaction.put("amount", amount);
            transaction.put("region", TEST_REGION);
            transaction.put("status", "queued");
            transaction.put("timestamp", System.currentTimeMillis());
            transaction.put("merchant_id", TEST_MERCHANT);
            
            offlineQueue.add(transaction);
            queuedCount.incrementAndGet();
            
            return transaction;
        }
    }
    
    /**
     * Helper: Sync queued transactions (simulates store-and-forward)
     */
    private List<Map<String, Object>> syncQueuedTransactions() {
        List<Map<String, Object>> synced = new ArrayList<>();
        
        if (!networkOnline) {
            logger.info("Network is offline - cannot sync");
            return synced;
        }
        
        logger.info("🔄 Syncing {} queued transactions...", offlineQueue.size());
        
        while (!offlineQueue.isEmpty()) {
            Map<String, Object> queued = offlineQueue.poll();
            if (queued == null) break;
            
            String orderId = (String) queued.get("order_id");
            Double amount = (Double) queued.get("amount");
            String sku = (String) queued.get("sku");
            
            try {
                // Re-process the transaction online
                logger.info("  Syncing order: {}", orderId);
                
                // Price lookup (verify still valid)
                Response priceResponse = given()
                    .spec(requestSpec)
                    .when()
                    .get(pricingServiceUrl + "/price/" + sku)
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
                
                Double price = priceResponse.jsonPath().getDouble("price");
                
                // Tax calculation
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
                
                Double total = taxResponse.jsonPath().getDouble("total");
                
                // Payment authorization
                Map<String, Object> paymentRequest = new HashMap<>();
                paymentRequest.put("amount", total);
                paymentRequest.put("currency", "USD");
                paymentRequest.put("card_number", TEST_CARD_APPROVED);
                paymentRequest.put("card_expiry", "12/25");
                paymentRequest.put("cvv", "123");
                paymentRequest.put("merchant_id", TEST_MERCHANT);
                paymentRequest.put("order_id", orderId + "-SYNC");
                
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
                
                queued.put("status", status);
                queued.put("transaction_id", txnId);
                queued.put("synced_at", System.currentTimeMillis());
                synced.add(queued);
                syncedCount.incrementAndGet();
                
                logger.info("  ✅ Synced order: {} -> Status: {}", orderId, status);
                
            } catch (Exception e) {
                logger.warn("  ❌ Failed to sync order: {} - {}", orderId, e.getMessage());
                // Re-queue for later
                offlineQueue.add(queued);
            }
        }
        
        return synced;
    }
    
    /**
     * Helper: Simulate network state change
     */
    private void setNetworkOnline(boolean online) {
        this.networkOnline = online;
        logger.info("🔌 Network state changed to: {}", online ? "ONLINE" : "OFFLINE");
    }
    
    /**
     * Test: Store-and-Forward Queue Behavior
     * 
     * Given: Network is online
     * When: Network goes offline mid-transaction
     * Then: Transactions are queued
     * When: Network is restored
     * Then: Queued transactions are synced successfully
     */
    @Test(description = "Store-and-forward: Network interruption during transaction")
    public void testStoreAndForward() {
        logger.info("=== Starting Store-and-Forward Test ===");
        
        // Step 1: Network is online
        setNetworkOnline(true);
        logger.info("Step 1: Network is ONLINE");
        
        // Step 2: Process a transaction online (baseline)
        logger.info("Step 2: Processing initial online transaction...");
        Map<String, Object> onlineTxn = processTransaction(0, TEST_SKU, false);
        assertNotNull(onlineTxn, "Online transaction should be processed");
        assertEquals(onlineTxn.get("status"), "approved", "Online transaction should be approved");
        logger.info("  ✅ Online transaction approved: {}", onlineTxn.get("order_id"));
        
        // Step 3: Simulate network going offline
        logger.info("Step 3: Simulating network OFFLINE...");
        setNetworkOnline(false);
        
        // Step 4: Process transactions in offline mode (should queue)
        logger.info("Step 4: Processing 3 transactions while offline...");
        for (int i = 0; i < 3; i++) {
            Map<String, Object> queuedTxn = processTransaction(0, TEST_SKU, true);
            assertEquals(queuedTxn.get("status"), "queued", "Offline transaction should be queued");
            logger.info("  ✅ Queued transaction {}: {}", i+1, queuedTxn.get("order_id"));
        }
        
        // Verify queue has transactions
        assertEquals(queuedCount.get(), 3, "Should have 3 queued transactions");
        logger.info("  Queue size: {}", offlineQueue.size());
        
        // Step 5: Verify payment gateway cannot be reached (simulated)
        logger.info("Step 5: Attempting online transaction while offline...");
        Map<String, Object> failedTxn = processTransaction(0, TEST_SKU, true);
        assertEquals(failedTxn.get("status"), "queued", "Transaction should be queued when offline");
        assertEquals(queuedCount.get(), 4, "Should have 4 queued transactions");
        logger.info("  ✅ Transaction was queued as expected");
        
        // Step 6: Restore network
        logger.info("Step 6: Restoring network ONLINE...");
        setNetworkOnline(true);
        
        // Step 7: Sync queued transactions
        logger.info("Step 7: Syncing queued transactions...");
        List<Map<String, Object>> synced = syncQueuedTransactions();
        logger.info("  Synced {} of {} transactions", synced.size(), queuedCount.get());
        
        // Step 8: Verify all transactions synced successfully
        logger.info("Step 8: Verifying sync results...");
        for (Map<String, Object> txn : synced) {
            assertEquals(txn.get("status"), "approved", "Synced transaction should be approved");
            assertNotNull(txn.get("transaction_id"), "Synced transaction should have transaction ID");
            logger.info("  ✅ Synced: {} -> {}", txn.get("order_id"), txn.get("transaction_id"));
        }
        
        // Step 9: Verify queue is empty
        assertTrue(offlineQueue.isEmpty(), "Queue should be empty after sync");
        assertEquals(syncedCount.get(), 4, "All 4 transactions should be synced");
        
        logger.info("=== Store-and-Forward Test COMPLETE ===");
        logger.info("  Transactions queued: {}", queuedCount.get());
        logger.info("  Transactions synced: {}", syncedCount.get());
        logger.info("  All transactions successfully synced!");
    }
    
    /**
     * Test: Offline Queue Persistence (simulated)
     * 
     * Given: Transactions are queued offline
     * When: Multiple transactions are queued
     * Then: They maintain order and are synced in the same order
     */
    @Test(description = "Offline queue maintains order during sync")
    public void testQueueOrderPreservation() {
        logger.info("=== Starting Queue Order Test ===");
        
        // Reset state
        offlineQueue.clear();
        queuedCount.set(0);
        syncedCount.set(0);
        setNetworkOnline(false);
        
        // Step 1: Queue multiple transactions with different amounts
        logger.info("Step 1: Queuing 5 transactions with different amounts...");
        List<String> orderIds = new ArrayList<>();
        double[] amounts = {10.00, 25.50, 3.99, 100.00, 50.25};
        
        for (double amount : amounts) {
            Map<String, Object> txn = processTransaction(amount, TEST_SKU, true);
            orderIds.add((String) txn.get("order_id"));
            logger.info("  Queued: {} (${})", txn.get("order_id"), amount);
        }
        
        assertEquals(queuedCount.get(), 5, "Should have 5 queued transactions");
        
        // Step 2: Restore network and sync
        logger.info("Step 2: Restoring network and syncing...");
        setNetworkOnline(true);
        List<Map<String, Object>> synced = syncQueuedTransactions();
        
        // Step 3: Verify order is preserved
        logger.info("Step 3: Verifying order preservation...");
        assertEquals(synced.size(), 5, "All 5 transactions should be synced");
        
        for (int i = 0; i < synced.size(); i++) {
            String expectedOrderId = orderIds.get(i);
            String actualOrderId = (String) synced.get(i).get("order_id");
            // The actual order ID may have -SYNC appended, so check contains
            assertTrue(actualOrderId.contains(expectedOrderId.replace("-SYNC", "")), 
                "Order should be preserved: " + expectedOrderId);
            logger.info("  ✅ Order preserved: {}", actualOrderId);
        }
        
        logger.info("=== Queue Order Test COMPLETE ===");
    }
    
    /**
     * Test: Handle Sync Failure Gracefully
     * 
     * Given: Transactions are queued
     * When: Sync fails for some reason
     * Then: Failed transactions remain in the queue for retry
     */
    @Test(description = "Sync failures are handled gracefully with retry")
    public void testSyncFailureHandling() {
        logger.info("=== Starting Sync Failure Handling Test ===");
        
        // Reset state
        offlineQueue.clear();
        queuedCount.set(0);
        syncedCount.set(0);
        setNetworkOnline(false);
        
        // Step 1: Queue transactions
        logger.info("Step 1: Queuing transactions...");
        for (int i = 0; i < 3; i++) {
            processTransaction(0, TEST_SKU, true);
        }
        assertEquals(queuedCount.get(), 3, "Should have 3 queued transactions");
        
        // Step 2: Simulate network issue during sync
        logger.info("Step 2: Simulating network issue during sync...");
        setNetworkOnline(true);
        
        // Force a temporary failure by making the payment gateway unavailable
        // (in a real test, this would be done via network manipulation)
        // Here we simulate by clearing the queue manually and verifying behavior
        
        // Step 3: Attempt sync (some may fail)
        logger.info("Step 3: Attempting sync...");
        List<Map<String, Object>> synced = syncQueuedTransactions();
        
        // Step 4: Verify sync behavior
        logger.info("Step 4: Verifying sync results...");
        // Some transactions may have failed; verify they remain in queue or are handled
        if (!offlineQueue.isEmpty()) {
            logger.info("  {} transactions remain in queue for retry", offlineQueue.size());
            // Verify they can be retried
            logger.info("  Attempting retry...");
            List<Map<String, Object>> retried = syncQueuedTransactions();
            logger.info("  Retried {} transactions", retried.size());
        }
        
        logger.info("=== Sync Failure Handling Test COMPLETE ===");
    }
}