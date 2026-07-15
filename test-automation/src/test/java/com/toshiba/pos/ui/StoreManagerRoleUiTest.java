// test-automation/src/test/java/com/toshiba/pos/ui/StoreManagerRoleUiTest.java

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
 * Store Manager Role Coverage (A)
 * 
 * Manager-PIN override for:
 *   - Price change
 *   - Void approval
 *   - End-of-day close-out
 * 
 * Layered onto the existing Cashier/Customer UI coverage.
 */
public class StoreManagerRoleUiTest {

    private static final Logger logger = LogManager.getLogger(StoreManagerRoleUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String TEST_PROFILE = "ELERA";

    // Manager PIN (mock)
    private static final String MANAGER_PIN = "9999";
    private static final String INVALID_PIN = "0000";

    // Locators for manager-specific UI elements
    private static final By MANAGER_PIN_DIALOG = By.id("manager-pin-dialog");
    private static final By MANAGER_PIN_INPUT = By.id("manager-pin-input");
    private static final By MANAGER_PIN_SUBMIT = By.id("manager-pin-submit");
    private static final By MANAGER_PIN_CANCEL = By.id("manager-pin-cancel");
    private static final By MANAGER_PIN_ERROR = By.id("manager-pin-error");

    // Price change
    private static final By PRICE_CHANGE_BTN = By.id("price-change-btn");
    private static final By PRICE_CHANGE_DIALOG = By.id("price-change-dialog");
    private static final By PRICE_CHANGE_SKU = By.id("price-change-sku");
    private static final By PRICE_CHANGE_NEW_PRICE = By.id("price-change-new-price");
    private static final By PRICE_CHANGE_CONFIRM = By.id("price-change-confirm");
    private static final By PRICE_CHANGE_SUCCESS = By.id("price-change-success");

    // Void approval
    private static final By VOID_APPROVAL_BTN = By.id("void-approval-btn");
    private static final By VOID_APPROVAL_DIALOG = By.id("void-approval-dialog");
    private static final By VOID_APPROVAL_TRANSACTION = By.id("void-approval-transaction");
    private static final By VOID_APPROVAL_CONFIRM = By.id("void-approval-confirm");
    private static final By VOID_APPROVAL_SUCCESS = By.id("void-approval-success");
    private static final By VOID_BLOCKED_MSG = By.id("void-blocked-msg");

    // End-of-day close-out
    private static final By EOD_CLOSE_BTN = By.id("eod-close-btn");
    private static final By EOD_CONFIRM_DIALOG = By.id("eod-confirm-dialog");
    private static final By EOD_CONFIRM_BTN = By.id("eod-confirm-btn");
    private static final By EOD_CANCEL_BTN = By.id("eod-cancel-btn");
    private static final By EOD_SUCCESS_MSG = By.id("eod-success-msg");
    private static final By EOD_SUMMARY = By.id("eod-summary");

    // Cashier UI elements
    private static final By BASKET_ITEMS = By.cssSelector(".basket-item");
    private static final By BASKET_SUBTOTAL = By.id("basket-subtotal");
    private static final By BASKET_PAY_BTN = By.id("basket-pay-btn");
    private static final By BASKET_VOID_BTN = By.id("basket-void-btn");

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
        kiosk.loadProfile(TEST_PROFILE);
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Browser started, profile loaded, at basket screen");
    }

    @AfterMethod
    public void teardown() {
        if (driver != null) {
            driver.quit();
            logger.info("Browser closed");
        }
    }

    /**
     * Helper: Enter manager PIN.
     */
    private void enterManagerPin(String pin) {
        kiosk.tap(MANAGER_PIN_DIALOG);
        WebElement input = driver.findElement(MANAGER_PIN_INPUT);
        input.clear();
        input.sendKeys(pin);
        kiosk.tap(MANAGER_PIN_SUBMIT);
        logger.info("Manager PIN entered: {}", pin);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
     * Helper: Add items to basket.
     */
    private void addItemsToBasket(int count) {
        for (int i = 0; i < count; i++) {
            kiosk.tap(BaseKioskPage.BASKET_SCAN_BTN);
        }
        logger.info("Added {} items to basket", count);
    }

    /**
     * Test: Manager Price Change — Success with valid PIN
     * 
     * Given: A manager wants to change the price of an item
     * When: The manager enters the correct PIN
     * Then: The price change is applied
     */
    @Test(description = "Manager Price Change — Success with valid PIN")
    public void testManagerPriceChangeSuccess() {
        logger.info("=== Store Manager: Price Change (Valid PIN) ===");

        // Step 1: Add items to basket
        logger.info("Step 1: Adding items to basket");
        addItemsToBasket(2);
        String initialSubtotal = kiosk.getBasketSubtotal();
        logger.info("  Initial subtotal: {}", initialSubtotal);

        // Step 2: Click price change button
        logger.info("Step 2: Clicking price change button");
        assertTrue(isElementDisplayed(PRICE_CHANGE_BTN), "Price change button should be visible");
        kiosk.tap(PRICE_CHANGE_BTN);

        // Step 3: Verify manager PIN dialog appears
        logger.info("Step 3: Verifying manager PIN dialog");
        assertTrue(isElementDisplayed(MANAGER_PIN_DIALOG), "Manager PIN dialog should appear");
        assertTrue(isElementDisplayed(MANAGER_PIN_INPUT), "PIN input should be visible");

        // Step 4: Enter valid manager PIN
        logger.info("Step 4: Entering valid manager PIN");
        enterManagerPin(MANAGER_PIN);

        // Step 5: Verify price change dialog appears
        logger.info("Step 5: Verifying price change dialog");
        assertTrue(isElementDisplayed(PRICE_CHANGE_DIALOG), "Price change dialog should appear");

        // Step 6: Enter price change details
        logger.info("Step 6: Entering price change details");
        WebElement skuInput = driver.findElement(PRICE_CHANGE_SKU);
        skuInput.clear();
        skuInput.sendKeys("SKU-1001");
        WebElement priceInput = driver.findElement(PRICE_CHANGE_NEW_PRICE);
        priceInput.clear();
        priceInput.sendKeys("1.99");
        kiosk.tap(PRICE_CHANGE_CONFIRM);

        // Step 7: Verify success
        logger.info("Step 7: Verifying price change success");
        assertTrue(isElementDisplayed(PRICE_CHANGE_SUCCESS), "Price change success message should appear");

        // Step 8: Verify subtotal changed
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String newSubtotal = kiosk.getBasketSubtotal();
        logger.info("  New subtotal: {}", newSubtotal);
        assertNotEquals(newSubtotal, initialSubtotal, "Subtotal should change after price update");

        logger.info("✅ Manager Price Change (Valid PIN) test passed");
    }

    /**
     * Test: Manager Price Change — Rejected with invalid PIN
     * 
     * Given: A manager wants to change the price of an item
     * When: The manager enters an incorrect PIN
     * Then: The price change is rejected
     */
    @Test(description = "Manager Price Change — Rejected with invalid PIN")
    public void testManagerPriceChangeInvalidPin() {
        logger.info("=== Store Manager: Price Change (Invalid PIN) ===");

        // Step 1: Add items to basket
        logger.info("Step 1: Adding items to basket");
        addItemsToBasket(2);

        // Step 2: Click price change button
        logger.info("Step 2: Clicking price change button");
        assertTrue(isElementDisplayed(PRICE_CHANGE_BTN), "Price change button should be visible");
        kiosk.tap(PRICE_CHANGE_BTN);

        // Step 3: Enter invalid manager PIN
        logger.info("Step 3: Entering invalid manager PIN");
        enterManagerPin(INVALID_PIN);

        // Step 4: Verify error message
        logger.info("Step 4: Verifying error message");
        assertTrue(isElementDisplayed(MANAGER_PIN_ERROR), "PIN error message should appear");

        // Step 5: Verify price change dialog does NOT appear
        logger.info("Step 5: Verifying price change dialog NOT shown");
        assertFalse(isElementDisplayed(PRICE_CHANGE_DIALOG), "Price change dialog should NOT appear");

        logger.info("✅ Manager Price Change (Invalid PIN) test passed");
    }

    /**
     * Test: Void Approval — Manager override required
     * 
     * Given: A cashier attempts to void a transaction
     * When: Manager approval is required
     * Then: The void is blocked without manager PIN
     * And: Approved with valid manager PIN
     */
    @Test(description = "Store Manager — Void approval with manager PIN")
    public void testVoidApproval() {
        logger.info("=== Store Manager: Void Approval ===");

        // Step 1: Add items to basket
        logger.info("Step 1: Adding items to basket");
        addItemsToBasket(3);

        // Step 2: Attempt void without manager approval
        logger.info("Step 2: Attempting void without manager approval");
        assertTrue(isElementDisplayed(BASKET_VOID_BTN), "Void button should be visible");
        kiosk.tap(BASKET_VOID_BTN);

        // Step 3: Verify void is blocked without manager PIN
        logger.info("Step 3: Verifying void blocked");
        assertTrue(isElementDisplayed(VOID_BLOCKED_MSG), "Void blocked message should appear");
        assertTrue(isElementDisplayed(MANAGER_PIN_DIALOG), "Manager PIN dialog should appear");

        // Step 4: Enter valid manager PIN
        logger.info("Step 4: Entering valid manager PIN");
        enterManagerPin(MANAGER_PIN);

        // Step 5: Verify void approval dialog appears
        logger.info("Step 5: Verifying void approval dialog");
        assertTrue(isElementDisplayed(VOID_APPROVAL_DIALOG), "Void approval dialog should appear");

        // Step 6: Confirm void
        logger.info("Step 6: Confirming void");
        kiosk.tap(VOID_APPROVAL_CONFIRM);

        // Step 7: Verify void success
        logger.info("Step 7: Verifying void success");
        assertTrue(isElementDisplayed(VOID_APPROVAL_SUCCESS), "Void approval success message should appear");

        // Step 8: Verify basket is empty
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int itemCount = driver.findElements(BASKET_ITEMS).size();
        logger.info("  Items remaining: {}", itemCount);
        assertEquals(itemCount, 0, "Basket should be empty after void");

        logger.info("✅ Store Manager Void Approval test passed");
    }

    /**
     * Test: End-of-Day Close-Out — Manager only
     * 
     * Given: A cashier attempts to close out the day
     * When: Manager approval is required
     * Then: The close-out is blocked without manager PIN
     * And: Approved with valid manager PIN
     */
    @Test(description = "Store Manager — End-of-day close-out with manager PIN")
    public void testEndOfDayCloseOut() {
        logger.info("=== Store Manager: End-of-Day Close-Out ===");

        // Step 1: Verify EOD close button is visible
        logger.info("Step 1: Verifying EOD close button");
        assertTrue(isElementDisplayed(EOD_CLOSE_BTN), "EOD close button should be visible");

        // Step 2: Click EOD close button
        logger.info("Step 2: Clicking EOD close button");
        kiosk.tap(EOD_CLOSE_BTN);

        // Step 3: Verify manager PIN dialog appears
        logger.info("Step 3: Verifying manager PIN dialog");
        assertTrue(isElementDisplayed(MANAGER_PIN_DIALOG), "Manager PIN dialog should appear");

        // Step 4: Enter invalid PIN first (should fail)
        logger.info("Step 4: Entering invalid PIN");
        enterManagerPin(INVALID_PIN);
        assertTrue(isElementDisplayed(MANAGER_PIN_ERROR), "PIN error should appear");

        // Step 5: Enter valid manager PIN
        logger.info("Step 5: Entering valid manager PIN");
        // Clear the error by clicking cancel and retrying
        kiosk.tap(MANAGER_PIN_CANCEL);
        kiosk.tap(EOD_CLOSE_BTN);
        enterManagerPin(MANAGER_PIN);

        // Step 6: Verify EOD confirmation dialog appears
        logger.info("Step 6: Verifying EOD confirmation dialog");
        assertTrue(isElementDisplayed(EOD_CONFIRM_DIALOG), "EOD confirmation dialog should appear");

        // Step 7: Confirm EOD close-out
        logger.info("Step 7: Confirming EOD close-out");
        kiosk.tap(EOD_CONFIRM_BTN);

        // Step 8: Verify EOD success
        logger.info("Step 8: Verifying EOD success");
        assertTrue(isElementDisplayed(EOD_SUCCESS_MSG), "EOD success message should appear");

        // Step 9: Verify EOD summary is displayed
        logger.info("Step 9: Verifying EOD summary");
        assertTrue(isElementDisplayed(EOD_SUMMARY), "EOD summary should be displayed");

        logger.info("✅ Store Manager End-of-Day Close-Out test passed");
    }

    /**
     * Test: EOD Close-Out — Cancelled by manager
     * 
     * Given: A manager starts the EOD close-out process
     * When: The manager cancels the process
     * Then: The close-out is not completed
     */
    @Test(description = "Store Manager — End-of-day close-out cancelled")
    public void testEndOfDayCloseOutCancelled() {
        logger.info("=== Store Manager: End-of-Day Close-Out Cancelled ===");

        // Step 1: Click EOD close button
        logger.info("Step 1: Clicking EOD close button");
        kiosk.tap(EOD_CLOSE_BTN);

        // Step 2: Enter valid manager PIN
        logger.info("Step 2: Entering valid manager PIN");
        enterManagerPin(MANAGER_PIN);

        // Step 3: Verify EOD confirmation dialog appears
        logger.info("Step 3: Verifying EOD confirmation dialog");
        assertTrue(isElementDisplayed(EOD_CONFIRM_DIALOG), "EOD confirmation dialog should appear");

        // Step 4: Cancel EOD close-out
        logger.info("Step 4: Cancelling EOD close-out");
        kiosk.tap(EOD_CANCEL_BTN);

        // Step 5: Verify EOD was NOT completed
        logger.info("Step 5: Verifying EOD was not completed");
        assertFalse(isElementDisplayed(EOD_SUCCESS_MSG), "EOD success message should NOT appear");

        // Step 6: Verify EOD summary is NOT displayed
        assertFalse(isElementDisplayed(EOD_SUMMARY), "EOD summary should NOT be displayed");

        // Step 7: Verify we are back to the basket screen
        logger.info("Step 7: Verifying basket screen");
        kiosk.assertScreen("basket");

        logger.info("✅ Store Manager EOD Close-Out Cancelled test passed");
    }

    /**
     * Test: Store Manager Role — Complete workflow
     * 
     * Given: A manager performs a complete workflow
     * When: Price change → void approval → EOD close-out
     * Then: All operations succeed with manager PIN
     */
    @Test(description = "Store Manager — Complete workflow")
    public void testManagerCompleteWorkflow() {
        logger.info("=== Store Manager: Complete Workflow ===");

        // Step 1: Price change
        logger.info("Step 1: Performing price change");
        addItemsToBasket(1);
        kiosk.tap(PRICE_CHANGE_BTN);
        enterManagerPin(MANAGER_PIN);
        WebElement skuInput = driver.findElement(PRICE_CHANGE_SKU);
        skuInput.clear();
        skuInput.sendKeys("SKU-1001");
        WebElement priceInput = driver.findElement(PRICE_CHANGE_NEW_PRICE);
        priceInput.clear();
        priceInput.sendKeys("0.99");
        kiosk.tap(PRICE_CHANGE_CONFIRM);
        assertTrue(isElementDisplayed(PRICE_CHANGE_SUCCESS), "Price change should succeed");

        // Step 2: Void approval
        logger.info("Step 2: Performing void approval");
        kiosk.tap(BASKET_VOID_BTN);
        enterManagerPin(MANAGER_PIN);
        kiosk.tap(VOID_APPROVAL_CONFIRM);
        assertTrue(isElementDisplayed(VOID_APPROVAL_SUCCESS), "Void approval should succeed");

        // Step 3: EOD close-out
        logger.info("Step 3: Performing EOD close-out");
        kiosk.tap(EOD_CLOSE_BTN);
        enterManagerPin(MANAGER_PIN);
        kiosk.tap(EOD_CONFIRM_BTN);
        assertTrue(isElementDisplayed(EOD_SUCCESS_MSG), "EOD close-out should succeed");

        logger.info("✅ Store Manager Complete Workflow test passed");
    }
}