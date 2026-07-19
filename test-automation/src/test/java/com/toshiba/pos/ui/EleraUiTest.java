// test-automation/src/test/java/com/toshiba/pos/ui/EleraUiTest.java

package com.toshiba.pos.ui;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.testng.Assert.*;

/**
 * ELERA® UI Test
 * 
 * Validates that POS-mode vs. Self-Service-mode profiles render different
 * toolbars/element sets on the same basket screen.
 * 
 * Test Scenarios:
 *   1. Load ELERA POS profile → assert POS toolbar elements are visible
 *   2. Load ELERA Self-Service profile → assert Self-Service toolbar elements are visible
 *   3. Assert differences between the two modes
 */
public class EleraUiTest {

    private static final Logger logger = LogManager.getLogger(EleraUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile names (must match dropdown options in the UI harness)
    private static final String ELERA_PROFILE = "ELERA";
    private static final String ELERA_SELF_SERVICE_PROFILE = "ELERA Self Service";
    private static final String PROFILE_PREFIX = "ELERA";

    // Element selectors that differ between modes
    private static final By MODE_POS_ELEMENTS = By.cssSelector(".mode-pos");
    private static final By MODE_SELF_SERVICE_ELEMENTS = By.cssSelector(".mode-self-service");
    private static final By ASSIST_BUTTON = By.id("basket-assist-btn");
    private static final By BASKET_TITLE = By.cssSelector(".basket-header h2");
    private static final By PERIPHERAL_INDICATORS = By.id("peripheral-indicators");

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
     * Helper: Load a profile and assert it loads correctly.
     */
    private void loadProfileAndAssert(String profileName) {
        kiosk.loadProfile(profileName);
        kiosk.waitForScreen("welcome");
        kiosk.assertElementText(BaseKioskPage.PROFILE_NAME, profileName);
        logger.info("Profile loaded: {}", profileName);
    }

    /**
     * Helper: Navigate to the basket screen.
     */
    private void goToBasket() {
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Navigated to basket screen");
    }

    /**
     * Helper: Assert that an element is present and visible.
     */
    private void assertElementPresent(By locator) {
        kiosk.assertElementVisible(locator);
        logger.info("  ✅ Element visible: {}", locator);
    }

    /**
     * Helper: Assert that an element is NOT present or hidden.
     */
    private void assertElementAbsent(By locator) {
        kiosk.assertElementNotVisible(locator);
        logger.info("  ❌ Element hidden: {}", locator);
    }

    /**
     * Test: ELERA POS Mode — POS toolbar elements are visible
     * 
     * Given: ELERA POS mode profile is loaded
     * When: The user navigates to the basket screen
     * Then: POS-specific toolbar elements are visible
     *       Self-Service elements are hidden
     */
    @Test(description = "ELERA POS Mode — POS toolbar elements visible", groups = {"ui", "regression", "product:elera"})
    public void testPosModeElements() {
        logger.info("=== ELERA POS Mode UI Test ===");

        // Step 1: Load ELERA POS profile
        logger.info("Step 1: Loading ELERA POS profile");
        loadProfileAndAssert(ELERA_PROFILE);

        // Step 2: Navigate to basket
        logger.info("Step 2: Navigating to basket");
        goToBasket();

        // Step 3: Assert POS elements are present
        logger.info("Step 3: Asserting POS elements");
        
        // In POS mode, the assist button may be hidden (attended checkout)
        assertElementAbsent(ASSIST_BUTTON);
        logger.info("  Assist button hidden (POS mode)");

        // Check that peripheral indicators show all peripherals
        kiosk.assertElementVisible(PERIPHERAL_INDICATORS);
        logger.info("  Peripheral indicators visible");

        // Check that the basket title is correct
        kiosk.assertElementText(BASKET_TITLE, "Your Basket");
        logger.info("  Basket title: 'Your Basket'");

        // Check footer shows correct profile
        kiosk.assertElementText(BaseKioskPage.FOOTER_MODE, "ELERA");
        logger.info("  Footer profile: ELERA");

        logger.info("✅ ELERA POS Mode UI test passed");
    }

    /**
     * Test: ELERA Self-Service Mode — Self-Service toolbar elements are visible
     * 
     * Given: ELERA Self-Service mode profile is loaded
     * When: The user navigates to the basket screen
     * Then: Self-Service-specific toolbar elements are visible
     *       POS elements are hidden
     */
    @Test(description = "ELERA Self-Service Mode — Self-Service elements visible", groups = {"ui", "regression", "product:elera"})
    public void testSelfServiceModeElements() {
        logger.info("=== ELERA Self-Service Mode UI Test ===");

        // Step 1: Load ELERA Self-Service profile
        logger.info("Step 1: Loading ELERA Self-Service profile");
        // Note: The dropdown may have "ELERA" and "ELERA Self Service" options
        // If "ELERA Self Service" doesn't exist, we load ELERA and toggle mode
        try {
            loadProfileAndAssert(ELERA_SELF_SERVICE_PROFILE);
        } catch (Exception e) {
            logger.info("  '{}' not found, loading '{}' and toggling mode", 
                ELERA_SELF_SERVICE_PROFILE, ELERA_PROFILE);
            loadProfileAndAssert(ELERA_PROFILE);
            // Toggle to self-service mode via UI toggle
            // In a real implementation, there would be a mode toggle button
            // For the test harness, we simulate by clicking a mode toggle button
            kiosk.tapById("mode-toggle");
        }

        // Step 2: Navigate to basket
        logger.info("Step 2: Navigating to basket");
        goToBasket();

        // Step 3: Assert Self-Service elements are present
        logger.info("Step 3: Asserting Self-Service elements");
        
        // In Self-Service mode, the assist button should be visible
        assertElementPresent(ASSIST_BUTTON);
        logger.info("  Assist button visible (Self-Service mode)");

        // Check that peripheral indicators show all peripherals
        kiosk.assertElementVisible(PERIPHERAL_INDICATORS);
        logger.info("  Peripheral indicators visible");

        // Check footer shows correct profile
        kiosk.assertElementText(BaseKioskPage.FOOTER_MODE, "ELERA");
        logger.info("  Footer profile: ELERA");

        // Check that basket has the self-service specific elements
        // In a real implementation, there would be a "Need Help" or "Assist" label
        assertElementPresent(ASSIST_BUTTON);
        logger.info("  ✅ Assist button present");

        logger.info("✅ ELERA Self-Service Mode UI test passed");
    }

    /**
     * Test: ELERA Mode Difference — POS vs Self-Service differ
     * 
     * @Test(description = "ELERA Mode Difference — POS vs Self-Service differ", groups = {"ui", "regression", "product:elera"})
     * 
     * Given: Both POS and Self-Service modes
     * When: The basket screen is rendered in each mode
     * Then: The two modes show different element sets
     */
    @Test(description = "ELERA Mode Difference — POS vs Self-Service differ", groups = {"ui", "regression", "product:elera"})
    public void testModeDifference() {
        logger.info("=== ELERA Mode Difference UI Test ===");

        // Step 1: Load ELERA POS profile and capture state
        logger.info("Step 1: Loading ELERA POS profile");
        loadProfileAndAssert(ELERA_PROFILE);
        goToBasket();

        // Capture POS state
        boolean posAssistVisible = isElementVisible(ASSIST_BUTTON);
        String posFooterMode = getFooterMode();
        logger.info("  POS: assistVisible={}, footerMode='{}'", posAssistVisible, posFooterMode);

        // Step 2: Switch to Self-Service mode
        logger.info("Step 2: Switching to Self-Service mode");
        // If there's a mode toggle button, click it
        // Otherwise, reload with self-service profile
        try {
            loadProfileAndAssert(ELERA_SELF_SERVICE_PROFILE);
        } catch (Exception e) {
            logger.info("  Toggling mode via UI");
            // Attempt to click a mode toggle if available
            try {
                kiosk.tapById("mode-toggle");
            } catch (Exception ex) {
                // If no toggle, reload with ELERA and switch via profile
                kiosk.reset();
                loadProfileAndAssert(ELERA_PROFILE);
                // Since the UI may not have a mode toggle, we simulate by
                // checking what elements are available after reset
            }
        }
        goToBasket();

        // Capture Self-Service state
        boolean ssAssistVisible = isElementVisible(ASSIST_BUTTON);
        String ssFooterMode = getFooterMode();
        logger.info("  Self-Service: assistVisible={}, footerMode='{}'", ssAssistVisible, ssFooterMode);

        // Step 3: Assert differences
        logger.info("Step 3: Asserting mode differences");
        
        // The assist button should differ between modes
        // In POS mode, it's usually hidden; in Self-Service it's visible
        assertNotEquals(posAssistVisible, ssAssistVisible, 
            "Assist button visibility should differ between modes");
        logger.info("  ✅ Assist button visibility differs: POS={}, SS={}", 
            posAssistVisible, ssAssistVisible);

        logger.info("✅ ELERA Mode Difference UI test passed");
    }

    /**
     * Test: ELERA Peripheral Indicators — Reflect profile capabilities
     * 
     * Given: ELERA profile is loaded
     * When: The basket screen is rendered
     * Then: Peripheral indicators show all enabled peripherals
     */
    @Test(description = "ELERA Peripheral Indicators — Reflect profile capabilities", groups = {"ui", "regression", "product:elera"})
    public void testPeripheralIndicators() {
        logger.info("=== ELERA Peripheral Indicators UI Test ===");

        // Step 1: Load ELERA profile
        logger.info("Step 1: Loading ELERA profile");
        loadProfileAndAssert(ELERA_PROFILE);

        // Step 2: Navigate to basket
        logger.info("Step 2: Navigating to basket");
        goToBasket();

        // Step 3: Assert peripheral indicators
        logger.info("Step 3: Asserting peripheral indicators");

        // ELERA should have scanner, printer, scale, pin_pad
        assertTrue(kiosk.isPeripheralActive("Scanner"), "Scanner should be active");
        assertTrue(kiosk.isPeripheralActive("Scale"), "Scale should be active");
        assertTrue(kiosk.isPeripheralActive("Printer"), "Printer should be active");
        assertTrue(kiosk.isPeripheralActive("PIN Pad"), "PIN Pad should be active");
        logger.info("  ✅ All peripherals active as expected");

        logger.info("✅ ELERA Peripheral Indicators UI test passed");
    }

    // ============================================================
    // Helper methods
    // ============================================================

    private boolean isElementVisible(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private String getFooterMode() {
        try {
            return driver.findElement(BaseKioskPage.FOOTER_MODE).getText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void addItemsToBasket(int count) {
        for (int i = 0; i < count; i++) {
            kiosk.tap(BaseKioskPage.BASKET_SCAN_BTN);
        }
        logger.info("Added {} items to basket", count);
    }
}