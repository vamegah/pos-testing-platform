// test-automation/src/test/java/com/toshiba/pos/ui/TcxPrinterSingleUiTest.java

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

import static org.testng.Assert.*;

/**
 * TCx® Single Station Printer UI Test
 * 
 * Validates the printer UI states:
 *   1. "Printing receipt…" status screen on success
 *   2. Paper-out alert screen when the simulator flag is set
 * 
 * Two scenarios: happy path + paper-out
 */
public class TcxPrinterSingleUiTest {

    private static final Logger logger = LogManager.getLogger(TcxPrinterSingleUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String PRINTER_PROFILE = "TCx Single Station Printer";

    // Locators for printer UI states
    private static final By PRINTING_STATUS = By.id("printing-status");
    private static final By PRINTING_SPINNER = By.cssSelector(".printing-spinner");
    private static final By PRINTING_MESSAGE = By.id("printing-message");
    private static final By PAPER_OUT_ALERT = By.id("paper-out-alert");
    private static final By PAPER_OUT_MESSAGE = By.id("paper-out-message");
    private static final By PAPER_OUT_ICON = By.cssSelector(".paper-out-icon");
    private static final By PAPER_OUT_DISMISS = By.id("paper-out-dismiss");
    private static final By RECEIPT_COMPLETE = By.id("receipt-complete");
    private static final By RECEIPT_COMPLETE_MESSAGE = By.id("receipt-complete-message");

    // Simulator control locators
    private static final By SIMULATE_PAPER_OUT_BTN = By.id("simulate-paper-out-btn");
    private static final By SIMULATE_CLEAR_PAPER_OUT_BTN = By.id("simulate-clear-paper-out-btn");
    private static final By PAPER_STATUS_INDICATOR = By.id("paper-status-indicator");

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
     * Helper: Load printer profile and go to basket.
     */
    private void loadPrinterAndGoToBasket() {
        kiosk.loadProfile(PRINTER_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Printer profile loaded, at basket screen");
    }

    /**
     * Helper: Add items and go to payment.
     */
    private void addItemsAndGoToPayment(int count) {
        for (int i = 0; i < count; i++) {
            kiosk.tap(BaseKioskPage.BASKET_SCAN_BTN);
        }
        kiosk.tap(BaseKioskPage.BASKET_PAY_BTN);
        kiosk.waitForScreen("payment");
        logger.info("Added {} items, at payment screen", count);
    }

    /**
     * Helper: Authorize payment to trigger printing.
     */
    private void authorizePayment() {
        kiosk.tap(BaseKioskPage.PAYMENT_AUTHORIZE_BTN);
        logger.info("Payment authorized, waiting for printer status");
        // Allow time for printer status to appear
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper: Simulate paper-out condition.
     */
    private void simulatePaperOut() {
        try {
            WebElement btn = driver.findElement(SIMULATE_PAPER_OUT_BTN);
            btn.click();
            logger.info("Paper-out simulated");
            // Allow time for alert to appear
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.warn("Paper-out button not found, using UI toggle");
            // Fallback: try to toggle via UI
            kiosk.tap(By.id("paper-out-toggle"));
        }
    }

    /**
     * Helper: Clear paper-out condition.
     */
    private void clearPaperOut() {
        try {
            WebElement btn = driver.findElement(SIMULATE_CLEAR_PAPER_OUT_BTN);
            btn.click();
            logger.info("Paper-out cleared");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.warn("Clear paper-out button not found");
        }
    }

    /**
     * Helper: Check if an element is displayed.
     */
    private boolean isElementDisplayed(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Get text from an element if displayed.
     */
    private String getElementText(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.isDisplayed() ? el.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Test: TCx Single Station Printer — Printing Status Screen on Success
     * 
     * Given: The printer profile is loaded with paper available
     * When: A sale is completed and payment is authorized
     * Then: The "printing receipt…" status screen appears
     *       Then: The receipt complete confirmation appears
     */
    @Test(description = "TCx Single Station Printer — Printing status screen on success")
    public void testPrintingStatusScreen() {
        logger.info("=== TCx Single Station Printer Printing Status Screen Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading printer profile");
        loadPrinterAndGoToBasket();

        // Step 2: Add items and go to payment
        logger.info("Step 2: Adding items and going to payment");
        addItemsAndGoToPayment(2);

        // Step 3: Authorize payment
        logger.info("Step 3: Authorizing payment");
        authorizePayment();

        // Step 4: Verify printing status appears
        logger.info("Step 4: Verifying printing status");
        boolean printingVisible = isElementDisplayed(PRINTING_STATUS);
        logger.info("  Printing status visible: {}", printingVisible);
        assertTrue(printingVisible, "Printing status should appear");

        // Step 5: Verify printing message
        logger.info("Step 5: Verifying printing message");
        String printingMessage = getElementText(PRINTING_MESSAGE);
        logger.info("  Printing message: {}", printingMessage);
        assertNotNull(printingMessage, "Printing message should be displayed");
        assertTrue(printingMessage.toLowerCase().contains("print") || 
                   printingMessage.toLowerCase().contains("receipt"),
            "Message should indicate printing");

        // Step 6: Verify spinner is visible
        logger.info("Step 6: Verifying spinner");
        boolean spinnerVisible = isElementDisplayed(PRINTING_SPINNER);
        logger.info("  Spinner visible: {}", spinnerVisible);
        assertTrue(spinnerVisible, "Spinner should be visible during printing");

        // Step 7: Wait for receipt complete
        logger.info("Step 7: Waiting for receipt complete");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 8: Verify receipt complete appears
        logger.info("Step 8: Verifying receipt complete");
        boolean completeVisible = isElementDisplayed(RECEIPT_COMPLETE);
        logger.info("  Receipt complete visible: {}", completeVisible);
        assertTrue(completeVisible, "Receipt complete confirmation should appear");

        String completeMessage = getElementText(RECEIPT_COMPLETE_MESSAGE);
        logger.info("  Complete message: {}", completeMessage);
        assertNotNull(completeMessage, "Complete message should be displayed");

        logger.info("✅ TCx Single Station Printer Printing Status Screen test passed");
    }

    /**
     * Test: TCx Single Station Printer — Paper-Out Alert Screen
     * 
     * Given: The printer profile is loaded
     * When: Paper-out is simulated
     * Then: The paper-out alert screen appears
     *       The alert shows the paper-out message
     *       The alert can be dismissed
     */
    @Test(description = "TCx Single Station Printer — Paper-out alert screen")
    public void testPaperOutAlertScreen() {
        logger.info("=== TCx Single Station Printer Paper-Out Alert Screen Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading printer profile");
        loadPrinterAndGoToBasket();

        // Step 2: Add items and go to payment
        logger.info("Step 2: Adding items and going to payment");
        addItemsAndGoToPayment(2);

        // Step 3: Simulate paper-out
        logger.info("Step 3: Simulating paper-out");
        simulatePaperOut();

        // Step 4: Authorize payment (should trigger paper-out alert)
        logger.info("Step 4: Authorizing payment with paper-out");
        authorizePayment();

        // Step 5: Verify paper-out alert appears
        logger.info("Step 5: Verifying paper-out alert");
        boolean alertVisible = isElementDisplayed(PAPER_OUT_ALERT);
        logger.info("  Paper-out alert visible: {}", alertVisible);
        assertTrue(alertVisible, "Paper-out alert should appear");

        // Step 6: Verify paper-out message
        logger.info("Step 6: Verifying paper-out message");
        String paperOutMessage = getElementText(PAPER_OUT_MESSAGE);
        logger.info("  Paper-out message: {}", paperOutMessage);
        assertNotNull(paperOutMessage, "Paper-out message should be displayed");
        assertTrue(paperOutMessage.toLowerCase().contains("paper") || 
                   paperOutMessage.toLowerCase().contains("out"),
            "Message should indicate paper out");

        // Step 7: Verify paper-out icon
        logger.info("Step 7: Verifying paper-out icon");
        boolean iconVisible = isElementDisplayed(PAPER_OUT_ICON);
        logger.info("  Paper-out icon visible: {}", iconVisible);
        assertTrue(iconVisible, "Paper-out icon should be visible");

        // Step 8: Verify dismiss button is present
        logger.info("Step 8: Verifying dismiss button");
        boolean dismissVisible = isElementDisplayed(PAPER_OUT_DISMISS);
        logger.info("  Dismiss button visible: {}", dismissVisible);
        assertTrue(dismissVisible, "Dismiss button should be present");

        // Step 9: Dismiss alert
        logger.info("Step 9: Dismissing alert");
        kiosk.tap(PAPER_OUT_DISMISS);

        // Step 10: Verify alert is dismissed
        logger.info("Step 10: Verifying alert dismissed");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean alertStillVisible = isElementDisplayed(PAPER_OUT_ALERT);
        logger.info("  Alert still visible: {}", alertStillVisible);
        assertFalse(alertStillVisible, "Paper-out alert should be dismissed");

        logger.info("✅ TCx Single Station Printer Paper-Out Alert Screen test passed");
    }

    /**
     * Test: TCx Single Station Printer — Status Indicator Shows Paper State
     * 
     * Given: The printer profile is loaded
     * When: Paper state changes
     * Then: The status indicator updates
     */
    @Test(description = "TCx Single Station Printer — Status indicator shows paper state")
    public void testStatusIndicator() {
        logger.info("=== TCx Single Station Printer Status Indicator Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading printer profile");
        loadPrinterAndGoToBasket();

        // Step 2: Verify initial paper status
        logger.info("Step 2: Verifying initial paper status");
        String initialStatus = getElementText(PAPER_STATUS_INDICATOR);
        logger.info("  Initial status: {}", initialStatus);
        assertNotNull(initialStatus, "Status indicator should be present");
        assertTrue(initialStatus.toLowerCase().contains("available") || 
                   initialStatus.toLowerCase().contains("ready"),
            "Initial status should indicate paper available");

        // Step 3: Simulate paper-out
        logger.info("Step 3: Simulating paper-out");
        simulatePaperOut();

        // Step 4: Verify status changed
        logger.info("Step 4: Verifying status changed");
        // Wait for status update
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String updatedStatus = getElementText(PAPER_STATUS_INDICATOR);
        logger.info("  Updated status: {}", updatedStatus);
        assertNotNull(updatedStatus, "Status should be updated");
        assertTrue(updatedStatus.toLowerCase().contains("out"), 
            "Status should indicate paper out");

        // Step 5: Clear paper-out
        logger.info("Step 5: Clearing paper-out");
        clearPaperOut();

        // Step 6: Verify status restored
        logger.info("Step 6: Verifying status restored");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String restoredStatus = getElementText(PAPER_STATUS_INDICATOR);
        logger.info("  Restored status: {}", restoredStatus);
        assertTrue(restoredStatus.toLowerCase().contains("available") || 
                   restoredStatus.toLowerCase().contains("ready"),
            "Status should indicate paper available");

        logger.info("✅ TCx Single Station Printer Status Indicator test passed");
    }

    /**
     * Test: TCx Single Station Printer — Transaction Not Lost on Paper-Out
     * 
     * Given: The printer profile is loaded
     * When: Paper-out occurs during a transaction
     * Then: The transaction is not lost (can retry after paper is restored)
     */
    @Test(description = "TCx Single Station Printer — Transaction not lost on paper-out")
    public void testTransactionNotLost() {
        logger.info("=== TCx Single Station Printer Transaction Not Lost Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading printer profile");
        loadPrinterAndGoToBasket();

        // Step 2: Add items and go to payment
        logger.info("Step 2: Adding items and going to payment");
        addItemsAndGoToPayment(2);

        // Step 3: Simulate paper-out
        logger.info("Step 3: Simulating paper-out");
        simulatePaperOut();

        // Step 4: Authorize payment (should show paper-out alert)
        logger.info("Step 4: Authorizing payment with paper-out");
        authorizePayment();

        // Step 5: Verify paper-out alert appears
        logger.info("Step 5: Verifying paper-out alert");
        assertTrue(isElementDisplayed(PAPER_OUT_ALERT), "Paper-out alert should appear");

        // Step 6: Dismiss alert
        logger.info("Step 6: Dismissing alert");
        kiosk.tap(PAPER_OUT_DISMISS);

        // Step 7: Clear paper-out
        logger.info("Step 7: Clearing paper-out");
        clearPaperOut();

        // Step 8: Retry printing
        logger.info("Step 8: Retrying printing");
        authorizePayment();

        // Step 9: Verify printing status appears (transaction not lost)
        logger.info("Step 9: Verifying printing status");
        boolean printingVisible = isElementDisplayed(PRINTING_STATUS);
        logger.info("  Printing status visible: {}", printingVisible);
        assertTrue(printingVisible, "Printing status should appear (transaction not lost)");

        // Step 10: Verify receipt complete
        logger.info("Step 10: Verifying receipt complete");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean completeVisible = isElementDisplayed(RECEIPT_COMPLETE);
        logger.info("  Receipt complete visible: {}", completeVisible);
        assertTrue(completeVisible, "Receipt complete should appear after retry");

        logger.info("✅ TCx Single Station Printer Transaction Not Lost test passed");
    }
}