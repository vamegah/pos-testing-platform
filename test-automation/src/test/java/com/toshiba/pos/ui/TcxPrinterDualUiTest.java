// test-automation/src/test/java/com/toshiba/pos/ui/TcxPrinterDualUiTest.java

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
 * TCx® Dual Station Printer UI Test
 * 
 * Validates two independent status indicators:
 *   - Customer station status
 *   - Merchant station status
 *   - One can show a fault while the other shows success simultaneously
 * 
 * Test Scenarios:
 *   1. Both stations success
 *   2. Customer station jam, merchant success
 *   3. Merchant station jam, customer success
 *   4. Both stations jam
 *   5. Status indicators update independently
 */
public class TcxPrinterDualUiTest {

    private static final Logger logger = LogManager.getLogger(TcxPrinterDualUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String DUAL_PRINTER_PROFILE = "TCx Dual Station Printer";

    // Locators for dual printer UI
    private static final By CUSTOMER_STATUS = By.id("customer-station-status");
    private static final By CUSTOMER_STATUS_TEXT = By.id("customer-station-status-text");
    private static final By CUSTOMER_STATUS_ICON = By.id("customer-station-status-icon");
    
    private static final By MERCHANT_STATUS = By.id("merchant-station-status");
    private static final By MERCHANT_STATUS_TEXT = By.id("merchant-station-status-text");
    private static final By MERCHANT_STATUS_ICON = By.id("merchant-station-status-icon");

    // Simulator controls
    private static final By SIMULATE_CUSTOMER_JAM = By.id("simulate-customer-jam-btn");
    private static final By SIMULATE_MERCHANT_JAM = By.id("simulate-merchant-jam-btn");
    private static final By SIMULATE_CUSTOMER_CLEAR = By.id("simulate-customer-clear-btn");
    private static final By SIMULATE_MERCHANT_CLEAR = By.id("simulate-merchant-clear-btn");
    
    private static final By PRINT_BOTH_BTN = By.id("print-both-btn");
    private static final By PRINT_CUSTOMER_BTN = By.id("print-customer-btn");
    private static final By PRINT_MERCHANT_BTN = By.id("print-merchant-btn");

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
     * Helper: Load dual printer profile and go to basket.
     */
    private void loadPrinterAndGoToBasket() {
        kiosk.loadProfile(DUAL_PRINTER_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Dual printer profile loaded, at basket screen");
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
     * Helper: Get status text for a station.
     */
    private String getStatusText(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.isDisplayed() ? el.getText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Helper: Get status class for a station.
     */
    private String getStatusClass(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.getAttribute("class");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Helper: Check if status indicates success.
     */
    private boolean isStatusSuccess(String statusText) {
        if (statusText == null) return false;
        String lower = statusText.toLowerCase();
        return lower.contains("success") || lower.contains("ready") || 
               lower.contains("available") || lower.contains("ok");
    }

    /**
     * Helper: Check if status indicates fault.
     */
    private boolean isStatusFault(String statusText) {
        if (statusText == null) return false;
        String lower = statusText.toLowerCase();
        return lower.contains("fault") || lower.contains("error") || 
               lower.contains("jam") || lower.contains("out");
    }

    /**
     * Helper: Simulate a jam on a specific station.
     */
    private void simulateStationJam(String station) {
        By btn = "customer".equals(station) ? SIMULATE_CUSTOMER_JAM : SIMULATE_MERCHANT_JAM;
        try {
            kiosk.tap(btn);
            logger.info("Simulated jam on {} station", station);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.warn("Could not simulate jam on {} station: {}", station, e.getMessage());
        }
    }

    /**
     * Helper: Clear a jam on a specific station.
     */
    private void clearStationJam(String station) {
        By btn = "customer".equals(station) ? SIMULATE_CUSTOMER_CLEAR : SIMULATE_MERCHANT_CLEAR;
        try {
            kiosk.tap(btn);
            logger.info("Cleared jam on {} station", station);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.warn("Could not clear jam on {} station: {}", station, e.getMessage());
        }
    }

    /**
     * Helper: Check if an element has a specific class.
     */
    private boolean hasClass(By locator, String className) {
        try {
            String classes = driver.findElement(locator).getAttribute("class");
            return classes != null && classes.contains(className);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test: TCx Dual Station Printer — Both Stations Success
     * 
     * Given: Both stations have no faults
     * When: Printing is attempted
     * Then: Both status indicators show success
     */
    @Test(description = "TCx Dual Station Printer — Both stations success")
    public void testBothStationsSuccess() {
        logger.info("=== TCx Dual Station Printer Both Stations Success Test ===");

        loadPrinterAndGoToBasket();
        addItemsAndGoToPayment(2);

        // Check initial status
        logger.info("Checking initial statuses");
        String customerStatus = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantStatus = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer status: {}", customerStatus);
        logger.info("  Merchant status: {}", merchantStatus);

        assertNotNull(customerStatus, "Customer status should be visible");
        assertNotNull(merchantStatus, "Merchant status should be visible");
        assertTrue(isStatusSuccess(customerStatus), "Customer should show success");
        assertTrue(isStatusSuccess(merchantStatus), "Merchant should show success");

        // Print to both
        logger.info("Printing to both stations");
        kiosk.tap(PRINT_BOTH_BTN);

        // Check statuses after printing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String customerAfter = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantAfter = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  After printing - Customer: {}", customerAfter);
        logger.info("  After printing - Merchant: {}", merchantAfter);

        assertTrue(isStatusSuccess(customerAfter), "Customer should still show success");
        assertTrue(isStatusSuccess(merchantAfter), "Merchant should still show success");

        logger.info("✅ TCx Dual Station Printer Both Stations Success test passed");
    }

    /**
     * Test: TCx Dual Station Printer — Customer Jam, Merchant Success
     * 
     * Given: Customer station has a jam
     * When: Printing is attempted
     * Then: Customer shows fault, Merchant shows success simultaneously
     */
    @Test(description = "TCx Dual Station Printer — Customer jam, merchant success")
    public void testCustomerJamMerchantSuccess() {
        logger.info("=== TCx Dual Station Printer Customer Jam, Merchant Success Test ===");

        loadPrinterAndGoToBasket();
        addItemsAndGoToPayment(2);

        // Simulate customer jam
        logger.info("Simulating customer station jam");
        simulateStationJam("customer");

        // Check statuses
        String customerStatus = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantStatus = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer status: {}", customerStatus);
        logger.info("  Merchant status: {}", merchantStatus);

        assertNotNull(customerStatus, "Customer status should be visible");
        assertNotNull(merchantStatus, "Merchant status should be visible");

        // Customer should show fault, Merchant should show success
        assertTrue(isStatusFault(customerStatus), "Customer should show fault (jam)");
        assertTrue(isStatusSuccess(merchantStatus), "Merchant should show success");

        // Verify status icons (if available)
        boolean customerHasFaultClass = hasClass(CUSTOMER_STATUS, "fault") || 
                                        hasClass(CUSTOMER_STATUS, "error") ||
                                        hasClass(CUSTOMER_STATUS, "warning");
        boolean merchantHasSuccessClass = hasClass(MERCHANT_STATUS, "success") || 
                                          hasClass(MERCHANT_STATUS, "ready");

        logger.info("  Customer fault class: {}", customerHasFaultClass);
        logger.info("  Merchant success class: {}", merchantHasSuccessClass);

        // Clear jam for cleanup
        clearStationJam("customer");

        logger.info("✅ TCx Dual Station Printer Customer Jam, Merchant Success test passed");
        logger.info("  One station shows fault while the other shows success simultaneously");
    }

    /**
     * Test: TCx Dual Station Printer — Merchant Jam, Customer Success
     * 
     * Given: Merchant station has a jam
     * When: Printing is attempted
     * Then: Merchant shows fault, Customer shows success simultaneously
     */
    @Test(description = "TCx Dual Station Printer — Merchant jam, customer success")
    public void testMerchantJamCustomerSuccess() {
        logger.info("=== TCx Dual Station Printer Merchant Jam, Customer Success Test ===");

        loadPrinterAndGoToBasket();
        addItemsAndGoToPayment(2);

        // Simulate merchant jam
        logger.info("Simulating merchant station jam");
        simulateStationJam("merchant");

        // Check statuses
        String customerStatus = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantStatus = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer status: {}", customerStatus);
        logger.info("  Merchant status: {}", merchantStatus);

        assertNotNull(customerStatus, "Customer status should be visible");
        assertNotNull(merchantStatus, "Merchant status should be visible");

        // Customer should show success, Merchant should show fault
        assertTrue(isStatusSuccess(customerStatus), "Customer should show success");
        assertTrue(isStatusFault(merchantStatus), "Merchant should show fault (jam)");

        // Verify status icons (if available)
        boolean customerHasSuccessClass = hasClass(CUSTOMER_STATUS, "success") || 
                                          hasClass(CUSTOMER_STATUS, "ready");
        boolean merchantHasFaultClass = hasClass(MERCHANT_STATUS, "fault") || 
                                        hasClass(MERCHANT_STATUS, "error") ||
                                        hasClass(MERCHANT_STATUS, "warning");

        logger.info("  Customer success class: {}", customerHasSuccessClass);
        logger.info("  Merchant fault class: {}", merchantHasFaultClass);

        // Clear jam for cleanup
        clearStationJam("merchant");

        logger.info("✅ TCx Dual Station Printer Merchant Jam, Customer Success test passed");
        logger.info("  One station shows fault while the other shows success simultaneously");
    }

    /**
     * Test: TCx Dual Station Printer — Both Stations Jam
     * 
     * Given: Both stations have jams
     * When: Printing is attempted
     * Then: Both status indicators show fault
     */
    @Test(description = "TCx Dual Station Printer — Both stations jam")
    public void testBothStationsJam() {
        logger.info("=== TCx Dual Station Printer Both Stations Jam Test ===");

        loadPrinterAndGoToBasket();
        addItemsAndGoToPayment(2);

        // Simulate both jams
        logger.info("Simulating both stations jam");
        simulateStationJam("customer");
        simulateStationJam("merchant");

        // Check statuses
        String customerStatus = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantStatus = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer status: {}", customerStatus);
        logger.info("  Merchant status: {}", merchantStatus);

        assertNotNull(customerStatus, "Customer status should be visible");
        assertNotNull(merchantStatus, "Merchant status should be visible");

        // Both should show fault
        assertTrue(isStatusFault(customerStatus), "Customer should show fault (jam)");
        assertTrue(isStatusFault(merchantStatus), "Merchant should show fault (jam)");

        // Clear both jams for cleanup
        clearStationJam("customer");
        clearStationJam("merchant");

        logger.info("✅ TCx Dual Station Printer Both Stations Jam test passed");
    }

    /**
     * Test: TCx Dual Station Printer — Status Indicators Update Independently
     * 
     * Given: Status indicators are visible
     * When: Faults are toggled independently
     * Then: Each indicator updates independently
     */
    @Test(description = "TCx Dual Station Printer — Status indicators update independently")
    public void testStatusIndicatorsIndependent() {
        logger.info("=== TCx Dual Station Printer Status Indicators Independent Test ===");

        loadPrinterAndGoToBasket();

        // Step 1: Check initial state (both success)
        logger.info("Step 1: Initial state");
        String initialCustomer = getStatusText(CUSTOMER_STATUS_TEXT);
        String initialMerchant = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", initialCustomer);
        logger.info("  Merchant: {}", initialMerchant);
        assertTrue(isStatusSuccess(initialCustomer), "Customer should start success");
        assertTrue(isStatusSuccess(initialMerchant), "Merchant should start success");

        // Step 2: Simulate customer jam
        logger.info("Step 2: Customer jam");
        simulateStationJam("customer");
        String afterCustomerJam = getStatusText(CUSTOMER_STATUS_TEXT);
        String afterCustomerJamMerchant = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", afterCustomerJam);
        logger.info("  Merchant: {}", afterCustomerJamMerchant);
        assertTrue(isStatusFault(afterCustomerJam), "Customer should show fault");
        assertTrue(isStatusSuccess(afterCustomerJamMerchant), "Merchant should still show success");

        // Step 3: Clear customer jam
        logger.info("Step 3: Clear customer jam");
        clearStationJam("customer");
        String afterClearCustomer = getStatusText(CUSTOMER_STATUS_TEXT);
        String afterClearMerchant = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", afterClearCustomer);
        logger.info("  Merchant: {}", afterClearMerchant);
        assertTrue(isStatusSuccess(afterClearCustomer), "Customer should show success again");
        assertTrue(isStatusSuccess(afterClearMerchant), "Merchant should still show success");

        // Step 4: Simulate merchant jam
        logger.info("Step 4: Merchant jam");
        simulateStationJam("merchant");
        String afterMerchantJamCustomer = getStatusText(CUSTOMER_STATUS_TEXT);
        String afterMerchantJam = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", afterMerchantJamCustomer);
        logger.info("  Merchant: {}", afterMerchantJam);
        assertTrue(isStatusSuccess(afterMerchantJamCustomer), "Customer should still show success");
        assertTrue(isStatusFault(afterMerchantJam), "Merchant should show fault");

        // Step 5: Clear merchant jam
        logger.info("Step 5: Clear merchant jam");
        clearStationJam("merchant");
        String finalCustomer = getStatusText(CUSTOMER_STATUS_TEXT);
        String finalMerchant = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", finalCustomer);
        logger.info("  Merchant: {}", finalMerchant);
        assertTrue(isStatusSuccess(finalCustomer), "Customer should show success");
        assertTrue(isStatusSuccess(finalMerchant), "Merchant should show success again");

        logger.info("✅ TCx Dual Station Printer Status Indicators Independent test passed");
        logger.info("  Each indicator updated independently without affecting the other");
    }

    /**
     * Test: TCx Dual Station Printer — One Faulty, One Working Simultaneously
     * 
     * Given: One station faulty, one working
     * When: Both are displayed
     * Then: Both statuses show their respective states simultaneously
     */
    @Test(description = "TCx Dual Station Printer — One faulty, one working simultaneously")
    public void testOneFaultyOneWorkingSimultaneously() {
        logger.info("=== TCx Dual Station Printer One Faulty, One Working Simultaneously Test ===");

        loadPrinterAndGoToBasket();

        // Step 1: Simulate customer jam, leave merchant working
        logger.info("Step 1: Customer jam, merchant working");
        simulateStationJam("customer");

        // Step 2: Verify both statuses are visible simultaneously
        logger.info("Step 2: Verifying both statuses visible");
        assertTrue(isElementDisplayed(CUSTOMER_STATUS), "Customer status should be visible");
        assertTrue(isElementDisplayed(MERCHANT_STATUS), "Merchant status should be visible");

        // Step 3: Verify both have different states
        logger.info("Step 3: Verifying different states");
        String customerStatus = getStatusText(CUSTOMER_STATUS_TEXT);
        String merchantStatus = getStatusText(MERCHANT_STATUS_TEXT);
        logger.info("  Customer: {}", customerStatus);
        logger.info("  Merchant: {}", merchantStatus);

        assertTrue(isStatusFault(customerStatus), "Customer should show fault");
        assertTrue(isStatusSuccess(merchantStatus), "Merchant should show success");
        assertNotEquals(customerStatus, merchantStatus, "Statuses should be different");

        // Step 4: Verify both indicators are displayed at the same time
        logger.info("Step 4: Verifying simultaneous display");
        WebElement customerEl = driver.findElement(CUSTOMER_STATUS);
        WebElement merchantEl = driver.findElement(MERCHANT_STATUS);
        assertTrue(customerEl.isDisplayed() && merchantEl.isDisplayed(), 
            "Both status indicators should be displayed simultaneously");

        // Step 5: Clear for cleanup
        clearStationJam("customer");

        logger.info("✅ TCx Dual Station Printer One Faulty, One Working Simultaneously test passed");
        logger.info("  Both indicators displayed simultaneously with different states");
    }

    /**
     * Helper: Check if an element is displayed.
     */
    private boolean isElementDisplayed(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
}