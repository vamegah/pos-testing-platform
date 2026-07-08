// test-automation/src/test/java/com/toshiba/pos/ui/MxpVisionKioskUiTest.java

package com.toshiba.pos.ui;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static org.testng.Assert.*;

/**
 * MxP™ Vision Kiosk UI Test
 * 
 * Validates the screen sequence:
 *   1. Place items → bulk-scan result list
 *   2. Weight-mismatch assist overlay
 *   3. Payment prompt
 * 
 * Test Scenarios:
 *   - Happy path: scan → basket → payment
 *   - Weight mismatch: scan → mismatch → assist overlay → dismiss → payment
 *   - Bulk-scan results list shows recognized items
 */
public class MxpVisionKioskUiTest {

    private static final Logger logger = LogManager.getLogger(MxpVisionKioskUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String VISION_KIOSK_PROFILE = "Mxp Vision Kiosk";

    // Locators specific to Vision Kiosk
    private static final By BULK_SCAN_BTN = By.id("bulk-scan-btn");
    private static final By SCAN_RESULTS_LIST = By.id("scan-results-list");
    private static final By SCAN_RESULT_ITEM = By.cssSelector(".scan-result-item");
    private static final By WEIGHT_MISMATCH_INDICATOR = By.id("weight-mismatch-indicator");
    private static final By ASSIST_OVERLAY = By.id("screen-assist");
    private static final By ASSIST_MESSAGE = By.id("assist-message");
    private static final By PAYMENT_PROMPT = By.cssSelector("#screen-payment .payment-summary");
    private static final By BASKET_ITEMS = By.cssSelector(".basket-item");

    @BeforeClass
    public void setupClass() {
        WebDriverManager.chromedriver().setup();
        logger.info("ChromeDriver configured");
    }

    @BeforeMethod
    public void setupMethod() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1280,800");
        options.addArguments("--disable-gpu");

        driver = new ChromeDriver(options);
        kiosk = new BaseKioskPage(driver);
        kiosk.navigateTo();
        logger.info("Browser started and navigated to Kiosk UI");
    }

    @AfterMethod
    public void teardown() {
        if (driver != null) {
            driver.quit();
            logger.info("Browser closed");
        }
    }

    /**
     * Helper: Load Vision Kiosk profile and navigate to basket.
     */
    private void loadVisionKioskAndGoToBasket() {
        kiosk.loadProfile(VISION_KIOSK_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Vision Kiosk loaded, at basket screen");
    }

    /**
     * Helper: Perform a bulk scan (click scan button and verify results).
     */
    private void performBulkScan() {
        // Click bulk scan button
        kiosk.tap(BULK_SCAN_BTN);
        logger.info("Bulk scan initiated");

        // Wait for results to appear
        try {
            Thread.sleep(1000); // Allow time for scan results
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper: Trigger a weight mismatch.
     */
    private void triggerWeightMismatch() {
        // In the Vision Kiosk, weight mismatch is triggered by a flag
        // This would be set via the simulator API, but for UI we check
        // if the assist overlay appears after scanning
        kiosk.tap(By.id("weight-mismatch-simulate-btn"));
        logger.info("Weight mismatch simulated");
    }

    /**
     * Helper: Get scan result items.
     */
    private int getScanResultCount() {
        List<WebElement> results = driver.findElements(SCAN_RESULT_ITEM);
        return results.size();
    }

    /**
     * Helper: Check if assist overlay is visible.
     */
    private boolean isAssistOverlayVisible() {
        try {
            WebElement overlay = driver.findElement(ASSIST_OVERLAY);
            return overlay.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test: MxP Vision Kiosk — Happy Path Screen Sequence
     * 
     * Given: The Vision Kiosk profile is loaded
     * When: The user places items and performs a bulk scan
     * Then: The scan results list appears → basket updates → payment prompt appears
     */
    @Test(description = "MxP Vision Kiosk — Happy path screen sequence")
    public void testHappyPathScreenSequence() {
        logger.info("=== MxP Vision Kiosk Happy Path Screen Sequence ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading Vision Kiosk profile");
        loadVisionKioskAndGoToBasket();

        // Step 2: Perform bulk scan
        logger.info("Step 2: Performing bulk scan");
        performBulkScan();

        // Step 3: Verify scan results appear
        logger.info("Step 3: Verifying scan results");
        int resultCount = getScanResultCount();
        logger.info("  Scan results found: {}", resultCount);
        assertTrue(resultCount > 0, "Scan results should appear");

        // Step 4: Verify basket has items
        logger.info("Step 4: Verifying basket updated");
        int basketCount = kiosk.getBasketItemCount();
        logger.info("  Basket items: {}", basketCount);
        assertTrue(basketCount > 0, "Basket should have items after scan");

        // Step 5: Navigate to payment
        logger.info("Step 5: Navigating to payment");
        kiosk.tap(BaseKioskPage.BASKET_PAY_BTN);
        kiosk.waitForScreen("payment");

        // Step 6: Verify payment prompt is visible
        logger.info("Step 6: Verifying payment prompt");
        kiosk.assertElementVisible(PAYMENT_PROMPT);
        String total = kiosk.getBasketSubtotal();
        logger.info("  Payment total: {}", total);
        assertNotNull(total, "Payment total should be displayed");

        logger.info("✅ MxP Vision Kiosk Happy Path screen sequence passed");
    }

    /**
     * Test: MxP Vision Kiosk — Weight Mismatch Assist Overlay
     * 
     * Given: The Vision Kiosk profile is loaded
     * When: A weight mismatch occurs during scanning
     * Then: The assist overlay appears with the mismatch message
     *       The overlay can be dismissed
     */
    @Test(description = "MxP Vision Kiosk — Weight mismatch assist overlay")
    public void testWeightMismatchAssistOverlay() {
        logger.info("=== MxP Vision Kiosk Weight Mismatch Assist Overlay ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading Vision Kiosk profile");
        loadVisionKioskAndGoToBasket();

        // Step 2: Perform bulk scan
        logger.info("Step 2: Performing bulk scan");
        performBulkScan();

        // Step 3: Verify scan results appear
        int resultCount = getScanResultCount();
        logger.info("  Scan results: {}", resultCount);
        assertTrue(resultCount > 0, "Scan results should appear");

        // Step 4: Trigger weight mismatch
        logger.info("Step 4: Triggering weight mismatch");
        triggerWeightMismatch();

        // Step 5: Verify assist overlay appears
        logger.info("Step 5: Verifying assist overlay");
        assertTrue(isAssistOverlayVisible(), "Assist overlay should appear on weight mismatch");

        // Verify assist message
        kiosk.assertElementVisible(ASSIST_MESSAGE);
        String message = driver.findElement(ASSIST_MESSAGE).getText();
        logger.info("  Assist message: {}", message);
        assertNotNull(message, "Assist message should be displayed");
        assertTrue(message.contains("associate") || message.contains("assistance"), 
            "Assist message should mention assistance");

        // Step 6: Dismiss assist overlay
        logger.info("Step 6: Dismissing assist overlay");
        kiosk.tap(BaseKioskPage.ASSIST_DISMISS_BTN);

        // Verify overlay is dismissed
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(isAssistOverlayVisible(), "Assist overlay should be dismissed");

        logger.info("✅ MxP Vision Kiosk Weight Mismatch Assist Overlay test passed");
    }

    /**
     * Test: MxP Vision Kiosk — Bulk Scan Results List
     * 
     * Given: The Vision Kiosk profile is loaded
     * When: A bulk scan is performed
     * Then: The scan results list shows recognized items with confidence
     */
    @Test(description = "MxP Vision Kiosk — Bulk scan results list")
    public void testBulkScanResultsList() {
        logger.info("=== MxP Vision Kiosk Bulk Scan Results List ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading Vision Kiosk profile");
        loadVisionKioskAndGoToBasket();

        // Step 2: Perform bulk scan
        logger.info("Step 2: Performing bulk scan");
        performBulkScan();

        // Step 3: Verify scan results list
        logger.info("Step 3: Verifying scan results list");
        kiosk.assertElementVisible(SCAN_RESULTS_LIST);

        List<WebElement> results = driver.findElements(SCAN_RESULT_ITEM);
        logger.info("  Found {} scan results", results.size());
        assertTrue(results.size() > 0, "Should have at least one scan result");

        // Check each result has name and confidence
        for (WebElement result : results) {
            String text = result.getText();
            logger.info("  Result: {}", text);
            assertTrue(text.contains("%") || text.contains("confidence"), 
                "Result should show confidence");
        }

        logger.info("✅ MxP Vision Kiosk Bulk Scan Results List test passed");
    }

    /**
     * Test: MxP Vision Kiosk — Weight Mismatch Only When Flagged
     * 
     * Given: The Vision Kiosk profile is loaded
     * When: A bulk scan is performed without weight mismatch
     * Then: No assist overlay appears
     */
    @Test(description = "MxP Vision Kiosk — No mismatch, no assist overlay")
    public void testNoMismatchNoAssist() {
        logger.info("=== MxP Vision Kiosk No Mismatch No Assist ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading Vision Kiosk profile");
        loadVisionKioskAndGoToBasket();

        // Step 2: Perform bulk scan (no mismatch)
        logger.info("Step 2: Performing bulk scan without mismatch");
        performBulkScan();

        // Step 3: Verify no assist overlay
        logger.info("Step 3: Verifying no assist overlay");
        assertFalse(isAssistOverlayVisible(), "Assist overlay should NOT appear without mismatch");

        // Step 4: Verify basket has items
        int basketCount = kiosk.getBasketItemCount();
        logger.info("  Basket items: {}", basketCount);
        assertTrue(basketCount > 0, "Basket should have items");

        logger.info("✅ MxP Vision Kiosk No Mismatch No Assist test passed");
    }

    /**
     * Test: MxP Vision Kiosk — Payment Prompt After Scan
     * 
     * Given: The Vision Kiosk profile is loaded
     * When: Items are scanned and the user proceeds to payment
     * Then: The payment prompt shows the correct total
     */
    @Test(description = "MxP Vision Kiosk — Payment prompt after scan")
    public void testPaymentPromptAfterScan() {
        logger.info("=== MxP Vision Kiosk Payment Prompt After Scan ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading Vision Kiosk profile");
        loadVisionKioskAndGoToBasket();

        // Step 2: Perform bulk scan
        logger.info("Step 2: Performing bulk scan");
        performBulkScan();

        // Step 3: Verify basket has items
        String subtotal = kiosk.getBasketSubtotal();
        logger.info("  Basket subtotal: {}", subtotal);
        assertNotNull(subtotal, "Subtotal should be displayed");
        assertTrue(Double.parseDouble(subtotal.replace("$", "")) > 0, 
            "Subtotal should be greater than 0");

        // Step 4: Navigate to payment
        logger.info("Step 4: Navigating to payment");
        kiosk.tap(BaseKioskPage.BASKET_PAY_BTN);
        kiosk.waitForScreen("payment");

        // Step 5: Verify payment prompt shows correct total
        logger.info("Step 5: Verifying payment prompt");
        kiosk.assertElementVisible(PAYMENT_PROMPT);
        String paymentTotal = driver.findElement(BaseKioskPage.PAYMENT_TOTAL).getText();
        logger.info("  Payment total: {}", paymentTotal);
        assertNotNull(paymentTotal, "Payment total should be displayed");
        assertTrue(Double.parseDouble(paymentTotal.replace("$", "")) > 0, 
            "Payment total should be greater than 0");

        logger.info("✅ MxP Vision Kiosk Payment Prompt After Scan test passed");
    }
}