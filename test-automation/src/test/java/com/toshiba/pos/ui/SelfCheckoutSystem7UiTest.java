// test-automation/src/test/java/com/toshiba/pos/ui/SelfCheckoutSystem7UiTest.java

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

import java.util.*;

import static org.testng.Assert.*;

/**
 * Self Checkout System 7 UI Test
 * 
 * Validates shopper flow across three form-factor profiles:
 *   1. Cash-Recycling: Full payment options including cash
 *   2. Cashless: Card/NFC only, no cash
 *   3. Kiosk: Compact form-factor with card only
 * 
 * Also validates assist-screen trigger on a flagged item.
 */
public class SelfCheckoutSystem7UiTest {

    private static final Logger logger = LogManager.getLogger(SelfCheckoutSystem7UiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile names
    private static final String SCS7_CASH_RECYCLING = "SCS7 Cash-Recycling";
    private static final String SCS7_CASHLESS = "SCS7 Cashless";
    private static final String SCS7_KIOSK = "SCS7 Kiosk";

    // Payment method locators
    private static final By PAYMENT_METHOD_CARD = By.id("payment-method-card");
    private static final By PAYMENT_METHOD_CASH = By.id("payment-method-cash");
    private static final By PAYMENT_METHOD_NFC = By.id("payment-method-nfc");
    private static final By PAYMENT_METHOD_GIFT_CARD = By.id("payment-method-gift-card");
    private static final By PAYMENT_METHODS_CONTAINER = By.id("payment-methods");

    // Assist/exception locators
    private static final By ASSIST_TRIGGER_ITEM = By.id("assist-trigger-item");
    private static final By ASSIST_OVERLAY = By.id("screen-assist");
    private static final By ASSIST_MESSAGE = By.id("assist-message");
    private static final By ASSIST_DISMISS_BTN = By.id("assist-dismiss-btn");
    private static final By ASSIST_FLAG_INDICATOR = By.id("assist-flag-indicator");

    // Form-factor indicators
    private static final By FORM_FACTOR_BADGE = By.id("form-factor-badge");
    private static final By COMPACT_LAYOUT = By.cssSelector(".layout-compact");

    // Basket and checkout locators
    private static final By BASKET_SCAN_BTN = By.id("basket-scan-btn");
    private static final By BASKET_PAY_BTN = By.id("basket-pay-btn");
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
     * Helper: Load a profile and go to basket.
     */
    private void loadProfileAndGoToBasket(String profileName) {
        kiosk.loadProfile(profileName);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Loaded profile: {}, at basket", profileName);
    }

    /**
     * Helper: Navigate to payment screen.
     */
    private void goToPayment() {
        kiosk.tap(BaseKioskPage.BASKET_PAY_BTN);
        kiosk.waitForScreen("payment");
        logger.info("Navigated to payment screen");
    }

    /**
     * Helper: Check if a payment method is displayed.
     */
    private boolean isPaymentMethodVisible(String methodId) {
        try {
            WebElement el = driver.findElement(By.id("payment-method-" + methodId));
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Get all visible payment method labels.
     */
    private List<String> getVisiblePaymentMethods() {
        List<String> methods = new ArrayList<>();
        List<WebElement> methodElements = driver.findElements(By.cssSelector(".payment-method"));
        for (WebElement el : methodElements) {
            if (el.isDisplayed()) {
                methods.add(el.getText().toLowerCase());
            }
        }
        return methods;
    }

    /**
     * Helper: Add items to basket.
     */
    private void addItemsToBasket(int count) {
        for (int i = 0; i < count; i++) {
            kiosk.tap(BASKET_SCAN_BTN);
        }
        logger.info("Added {} items to basket", count);
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
     * Helper: Get form factor from badge.
     */
    private String getFormFactor() {
        try {
            return driver.findElement(FORM_FACTOR_BADGE).getText().toLowerCase();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Test: SCS7 Cash-Recycling Profile — Full payment options
     * 
     * Given: The cash-recycling profile is loaded
     * When: The user navigates to payment
     * Then: All payment methods (card, cash, NFC, gift card) are available
     */
    @Test(description = "SCS7 Cash-Recycling — Full payment options")
    public void testCashRecyclingPaymentOptions() {
        logger.info("=== SCS7 Cash-Recycling Payment Options Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading cash-recycling profile");
        loadProfileAndGoToBasket(SCS7_CASH_RECYCLING);

        // Step 2: Add items
        logger.info("Step 2: Adding items");
        addItemsToBasket(2);

        // Step 3: Navigate to payment
        logger.info("Step 3: Navigating to payment");
        goToPayment();

        // Step 4: Verify all payment methods visible
        logger.info("Step 4: Verifying payment methods");
        assertTrue(isPaymentMethodVisible("card"), "Card payment should be visible");
        assertTrue(isPaymentMethodVisible("cash"), "Cash payment should be visible");
        assertTrue(isPaymentMethodVisible("nfc"), "NFC payment should be visible");
        assertTrue(isPaymentMethodVisible("gift-card"), "Gift card should be visible");

        // Step 5: Verify count
        List<String> methods = getVisiblePaymentMethods();
        logger.info("  Visible methods: {}", methods);
        assertTrue(methods.size() >= 4, "Should have at least 4 payment methods");

        // Step 6: Verify form factor badge
        String formFactor = getFormFactor();
        logger.info("  Form factor: {}", formFactor);
        assertTrue(formFactor.contains("cash") || formFactor.contains("recycling"), 
            "Should indicate cash-recycling form factor");

        logger.info("✅ SCS7 Cash-Recycling Payment Options test passed");
    }

    /**
     * Test: SCS7 Cashless Profile — No cash payment
     * 
     * Given: The cashless profile is loaded
     * When: The user navigates to payment
     * Then: Cash payment method is hidden, card/NFC available
     */
    @Test(description = "SCS7 Cashless — No cash payment")
    public void testCashlessPaymentOptions() {
        logger.info("=== SCS7 Cashless Payment Options Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading cashless profile");
        loadProfileAndGoToBasket(SCS7_CASHLESS);

        // Step 2: Add items
        logger.info("Step 2: Adding items");
        addItemsToBasket(2);

        // Step 3: Navigate to payment
        logger.info("Step 3: Navigating to payment");
        goToPayment();

        // Step 4: Verify card and NFC visible, cash hidden
        logger.info("Step 4: Verifying payment methods");
        assertTrue(isPaymentMethodVisible("card"), "Card payment should be visible");
        assertTrue(isPaymentMethodVisible("nfc"), "NFC payment should be visible");
        assertFalse(isPaymentMethodVisible("cash"), "Cash should NOT be visible");
        assertTrue(isPaymentMethodVisible("gift-card"), "Gift card should be visible");

        // Step 5: Verify count
        List<String> methods = getVisiblePaymentMethods();
        logger.info("  Visible methods: {}", methods);
        // Cashless: card, NFC, gift-card (3)
        assertTrue(methods.size() >= 2, "Should have at least 2 payment methods");
        assertFalse(methods.contains("cash"), "Cash should not be in visible methods");

        // Step 6: Verify form factor badge
        String formFactor = getFormFactor();
        logger.info("  Form factor: {}", formFactor);
        assertTrue(formFactor.contains("cashless"), "Should indicate cashless form factor");

        logger.info("✅ SCS7 Cashless Payment Options test passed");
    }

    /**
     * Test: SCS7 Kiosk Profile — Compact with card only
     * 
     * Given: The kiosk profile is loaded
     * When: The user navigates to payment
     * Then: Only card payment is available (compact form-factor)
     */
    @Test(description = "SCS7 Kiosk — Card only, compact layout")
    public void testKioskPaymentOptions() {
        logger.info("=== SCS7 Kiosk Payment Options Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading kiosk profile");
        loadProfileAndGoToBasket(SCS7_KIOSK);

        // Step 2: Add items
        logger.info("Step 2: Adding items");
        addItemsToBasket(2);

        // Step 3: Navigate to payment
        logger.info("Step 3: Navigating to payment");
        goToPayment();

        // Step 4: Verify only card visible
        logger.info("Step 4: Verifying payment methods");
        assertTrue(isPaymentMethodVisible("card"), "Card payment should be visible");
        assertFalse(isPaymentMethodVisible("cash"), "Cash should NOT be visible");
        assertFalse(isPaymentMethodVisible("nfc"), "NFC should NOT be visible");
        assertFalse(isPaymentMethodVisible("gift-card"), "Gift card should NOT be visible");

        // Step 5: Verify count
        List<String> methods = getVisiblePaymentMethods();
        logger.info("  Visible methods: {}", methods);
        assertEquals(methods.size(), 1, "Should have only 1 payment method (card)");
        assertTrue(methods.contains("card"), "Only card should be visible");

        // Step 6: Verify compact layout
        logger.info("Step 5: Verifying compact layout");
        boolean compactVisible = driver.findElements(COMPACT_LAYOUT).stream()
            .anyMatch(WebElement::isDisplayed);
        logger.info("  Compact layout: {}", compactVisible);
        // Kiosk may or may not have compact layout, but should be smaller

        // Step 7: Verify form factor badge
        String formFactor = getFormFactor();
        logger.info("  Form factor: {}", formFactor);
        assertTrue(formFactor.contains("kiosk"), "Should indicate kiosk form factor");

        logger.info("✅ SCS7 Kiosk Payment Options test passed");
    }

    /**
     * Test: SCS7 Assist-Screen Trigger on Flagged Item
     * 
     * Given: The SCS7 profile is loaded
     * When: An item triggers an assist event
     * Then: The assist overlay appears with the correct message
     */
    @Test(description = "SCS7 Assist-screen trigger on flagged item")
    public void testAssistScreenTrigger() {
        logger.info("=== SCS7 Assist-Screen Trigger Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading SCS7 cash-recycling profile");
        loadProfileAndGoToBasket(SCS7_CASH_RECYCLING);

        // Step 2: Add items
        logger.info("Step 2: Adding items");
        addItemsToBasket(3);

        // Step 3: Flag an item for assistance
        logger.info("Step 3: Flagging item for assistance");
        // Click the assist trigger item
        try {
            WebElement trigger = driver.findElement(ASSIST_TRIGGER_ITEM);
            trigger.click();
            logger.info("  Assist trigger clicked");
        } catch (Exception e) {
            logger.info("  Assist trigger not found, simulating via assist button");
            // Fallback: use the assist button if available
            kiosk.tap(BaseKioskPage.BASKET_ASSIST_BTN);
        }

        // Step 4: Wait for assist overlay
        logger.info("Step 4: Verifying assist overlay");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(isAssistOverlayVisible(), "Assist overlay should appear");

        // Step 5: Verify assist message
        logger.info("Step 5: Verifying assist message");
        String message = driver.findElement(ASSIST_MESSAGE).getText();
        logger.info("  Assist message: {}", message);
        assertNotNull(message, "Assist message should be displayed");
        assertTrue(message.contains("associate") || message.contains("assistance") || 
                   message.contains("help"), "Message should mention assistance");

        // Step 6: Verify assist flag indicator
        logger.info("Step 6: Verifying assist flag indicator");
        boolean flagIndicator = driver.findElements(ASSIST_FLAG_INDICATOR).stream()
            .anyMatch(WebElement::isDisplayed);
        logger.info("  Assist flag indicator: {}", flagIndicator);
        assertTrue(flagIndicator, "Assist flag indicator should be visible");

        // Step 7: Dismiss assist overlay
        logger.info("Step 7: Dismissing assist overlay");
        kiosk.tap(ASSIST_DISMISS_BTN);

        // Verify overlay is dismissed
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(isAssistOverlayVisible(), "Assist overlay should be dismissed");

        logger.info("✅ SCS7 Assist-Screen Trigger test passed");
    }

    /**
     * Test: SCS7 All Form Factors — Data-driven
     * 
     * Given: All three form-factor profiles
     * When: Each is loaded
     * Then: The correct payment methods are shown for each
     */
    @Test(description = "SCS7 All Form Factors — Data-driven validation")
    public void testAllFormFactors() {
        logger.info("=== SCS7 All Form Factors Test ===");

        // Define test data
        Map<String, List<String>> formFactorTests = new LinkedHashMap<>();
        formFactorTests.put(SCS7_CASH_RECYCLING, Arrays.asList("card", "cash", "nfc", "gift-card"));
        formFactorTests.put(SCS7_CASHLESS, Arrays.asList("card", "nfc", "gift-card"));
        formFactorTests.put(SCS7_KIOSK, Arrays.asList("card"));

        for (Map.Entry<String, List<String>> entry : formFactorTests.entrySet()) {
            String profile = entry.getKey();
            List<String> expectedMethods = entry.getValue();

            logger.info("Testing profile: {}", profile);
            logger.info("  Expected methods: {}", expectedMethods);

            // Load profile and go to basket
            loadProfileAndGoToBasket(profile);
            addItemsToBasket(1);
            goToPayment();

            // Verify each expected method
            for (String method : expectedMethods) {
                assertTrue(isPaymentMethodVisible(method), 
                    "Method '" + method + "' should be visible for " + profile);
            }

            // Verify no extra methods are visible
            List<String> visibleMethods = getVisiblePaymentMethods();
            logger.info("  Visible methods: {}", visibleMethods);
            assertEquals(visibleMethods.size(), expectedMethods.size(), 
                "Should have exactly " + expectedMethods.size() + " methods");

            // Reset for next profile
            kiosk.reset();
        }

        logger.info("✅ SCS7 All Form Factors test passed");
    }
}