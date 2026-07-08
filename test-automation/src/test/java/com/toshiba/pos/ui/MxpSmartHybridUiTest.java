// test-automation/src/test/java/com/toshiba/pos/ui/MxpSmartHybridUiTest.java

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
 * MxP™ SMART | hybrid UI Test
 * 
 * Validates that the mode-switch control flips the screen between
 * self-service and assisted layouts.
 * 
 * Test Scenarios:
 *   1. Load hybrid profile → verify default mode (self-service)
 *   2. Switch to assisted mode → verify layout changes
 *   3. Switch back to self-service mode → verify layout reverts
 *   4. Basket state is preserved during mode switch
 */
public class MxpSmartHybridUiTest {

    private static final Logger logger = LogManager.getLogger(MxpSmartHybridUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String HYBRID_PROFILE = "Mxp SMART Hybrid";

    // Locators for mode-specific elements
    private static final By MODE_SWITCH_BTN = By.id("mode-switch-btn");
    private static final By MODE_INDICATOR = By.id("mode-indicator");
    private static final By SELF_SERVICE_LAYOUT = By.cssSelector(".layout-self-service");
    private static final By ASSISTED_LAYOUT = By.cssSelector(".layout-assisted");
    private static final By ASSISTED_INDICATOR = By.cssSelector(".assisted-indicator");
    private static final By SELF_SERVICE_INDICATOR = By.cssSelector(".self-service-indicator");
    private static final By BASKET_CONTAINER = By.id("basket-items");
    private static final By MODE_LABEL = By.id("mode-label");
    private static final By ROTATION_INDICATOR = By.id("rotation-indicator");

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
     * Helper: Load hybrid profile and go to basket.
     */
    private void loadHybridAndGoToBasket() {
        kiosk.loadProfile(HYBRID_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Hybrid profile loaded, at basket screen");
    }

    /**
     * Helper: Get current mode from UI indicator.
     */
    private String getCurrentMode() {
        try {
            return driver.findElement(MODE_LABEL).getText().toLowerCase();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Helper: Check if self-service layout is active.
     */
    private boolean isSelfServiceLayout() {
        try {
            WebElement el = driver.findElement(SELF_SERVICE_LAYOUT);
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Check if assisted layout is active.
     */
    private boolean isAssistedLayout() {
        try {
            WebElement el = driver.findElement(ASSISTED_LAYOUT);
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Toggle mode via UI switch.
     */
    private void toggleMode() {
        kiosk.tap(MODE_SWITCH_BTN);
        logger.info("Mode toggled");
        // Allow time for layout transition
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper: Get rotation indicator text.
     */
    private String getRotationIndicator() {
        try {
            return driver.findElement(ROTATION_INDICATOR).getText();
        } catch (Exception e) {
            return "";
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
     * Helper: Get basket item count.
     */
    private int getBasketCount() {
        return kiosk.getBasketItemCount();
    }

    /**
     * Test: MxP SMART Hybrid — Default Mode is Self-Service
     * 
     * Given: The hybrid profile is loaded
     * When: The basket screen is displayed
     * Then: The default mode is self-service
     */
    @Test(description = "MxP SMART Hybrid — Default mode is self-service")
    public void testDefaultModeIsSelfService() {
        logger.info("=== MxP SMART Hybrid Default Mode Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading hybrid profile");
        loadHybridAndGoToBasket();

        // Step 2: Verify default mode
        logger.info("Step 2: Verifying default mode");
        String mode = getCurrentMode();
        logger.info("  Current mode: {}", mode);
        assertTrue(mode.contains("self") || mode.contains("self-service"), 
            "Default mode should be self-service");

        // Step 3: Verify self-service layout is active
        logger.info("Step 3: Verifying self-service layout");
        assertTrue(isSelfServiceLayout(), "Self-service layout should be active");
        assertFalse(isAssistedLayout(), "Assisted layout should not be active");

        // Step 4: Verify rotation indicator (0° for self-service)
        String rotation = getRotationIndicator();
        logger.info("  Rotation indicator: {}", rotation);
        assertTrue(rotation.contains("0") || rotation.contains("self"), 
            "Self-service should show 0° rotation");

        logger.info("✅ MxP SMART Hybrid Default Mode test passed");
    }

    /**
     * Test: MxP SMART Hybrid — Switch to Assisted Mode
     * 
     * Given: Self-service mode is active
     * When: The user toggles to assisted mode
     * Then: The layout changes to assisted layout
     *       Rotation indicator shows 180°
     */
    @Test(description = "MxP SMART Hybrid — Switch to assisted mode")
    public void testSwitchToAssistedMode() {
        logger.info("=== MxP SMART Hybrid Switch to Assisted Mode Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading hybrid profile");
        loadHybridAndGoToBasket();

        // Step 2: Verify initial state is self-service
        logger.info("Step 2: Verifying initial self-service state");
        assertTrue(isSelfServiceLayout(), "Should start in self-service");
        String initialMode = getCurrentMode();
        logger.info("  Initial mode: {}", initialMode);

        // Step 3: Toggle to assisted mode
        logger.info("Step 3: Toggling to assisted mode");
        toggleMode();

        // Step 4: Verify assisted layout is active
        logger.info("Step 4: Verifying assisted layout");
        assertTrue(isAssistedLayout(), "Assisted layout should be active");
        assertFalse(isSelfServiceLayout(), "Self-service layout should not be active");

        // Step 5: Verify rotation indicator (180° for assisted)
        String rotation = getRotationIndicator();
        logger.info("  Rotation indicator: {}", rotation);
        assertTrue(rotation.contains("180"), "Assisted mode should show 180° rotation");

        // Step 6: Verify mode label updated
        String newMode = getCurrentMode();
        logger.info("  New mode: {}", newMode);
        assertTrue(newMode.contains("assisted"), "Mode should be assisted");

        logger.info("✅ MxP SMART Hybrid Switch to Assisted Mode test passed");
    }

    /**
     * Test: MxP SMART Hybrid — Switch Back to Self-Service Mode
     * 
     * Given: Assisted mode is active
     * When: The user toggles back to self-service mode
     * Then: The layout reverts to self-service layout
     *       Rotation indicator shows 0°
     */
    @Test(description = "MxP SMART Hybrid — Switch back to self-service mode")
    public void testSwitchBackToSelfService() {
        logger.info("=== MxP SMART Hybrid Switch Back to Self-Service Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading hybrid profile");
        loadHybridAndGoToBasket();

        // Step 2: Toggle to assisted mode
        logger.info("Step 2: Switching to assisted mode");
        toggleMode();
        assertTrue(isAssistedLayout(), "Should now be in assisted mode");

        // Step 3: Toggle back to self-service mode
        logger.info("Step 3: Switching back to self-service mode");
        toggleMode();

        // Step 4: Verify self-service layout is active again
        logger.info("Step 4: Verifying self-service layout restored");
        assertTrue(isSelfServiceLayout(), "Self-service layout should be active");
        assertFalse(isAssistedLayout(), "Assisted layout should not be active");

        // Step 5: Verify rotation indicator (0° for self-service)
        String rotation = getRotationIndicator();
        logger.info("  Rotation indicator: {}", rotation);
        assertTrue(rotation.contains("0") || rotation.contains("self"), 
            "Self-service should show 0° rotation");

        // Step 6: Verify mode label updated
        String mode = getCurrentMode();
        logger.info("  Mode: {}", mode);
        assertTrue(mode.contains("self"), "Mode should be self-service");

        logger.info("✅ MxP SMART Hybrid Switch Back to Self-Service test passed");
    }

    /**
     * Test: MxP SMART Hybrid — Basket State Preserved During Mode Switch
     * 
     * Given: Items are in the basket
     * When: The mode is toggled
     * Then: The basket state is preserved
     */
    @Test(description = "MxP SMART Hybrid — Basket state preserved during mode switch")
    public void testBasketStatePreserved() {
        logger.info("=== MxP SMART Hybrid Basket State Preservation Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading hybrid profile");
        loadHybridAndGoToBasket();

        // Step 2: Add items to basket
        logger.info("Step 2: Adding items to basket");
        addItemsToBasket(3);
        int initialCount = getBasketCount();
        String initialSubtotal = kiosk.getBasketSubtotal();
        logger.info("  Initial basket: {} items, {}", initialCount, initialSubtotal);
        assertTrue(initialCount > 0, "Basket should have items");

        // Step 3: Toggle to assisted mode
        logger.info("Step 3: Switching to assisted mode");
        toggleMode();
        assertTrue(isAssistedLayout(), "Should be in assisted mode");

        // Step 4: Verify basket state preserved
        logger.info("Step 4: Verifying basket state preserved");
        int afterCount = getBasketCount();
        String afterSubtotal = kiosk.getBasketSubtotal();
        logger.info("  After mode switch: {} items, {}", afterCount, afterSubtotal);
        assertEquals(afterCount, initialCount, "Basket count should be preserved");
        assertEquals(afterSubtotal, initialSubtotal, "Basket subtotal should be preserved");

        // Step 5: Toggle back to self-service
        logger.info("Step 5: Switching back to self-service");
        toggleMode();
        assertTrue(isSelfServiceLayout(), "Should be back in self-service");

        // Step 6: Verify basket state still preserved
        logger.info("Step 6: Verifying basket state still preserved");
        int finalCount = getBasketCount();
        String finalSubtotal = kiosk.getBasketSubtotal();
        logger.info("  Final: {} items, {}", finalCount, finalSubtotal);
        assertEquals(finalCount, initialCount, "Basket count should still be preserved");
        assertEquals(finalSubtotal, initialSubtotal, "Basket subtotal should still be preserved");

        logger.info("✅ MxP SMART Hybrid Basket State Preservation test passed");
    }

    /**
     * Test: MxP SMART Hybrid — Mode Indicator Shows Correct State
     * 
     * Given: The hybrid profile is loaded
     * When: The mode is toggled
     * Then: The mode indicator updates correctly
     */
    @Test(description = "MxP SMART Hybrid — Mode indicator shows correct state")
    public void testModeIndicator() {
        logger.info("=== MxP SMART Hybrid Mode Indicator Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading hybrid profile");
        loadHybridAndGoToBasket();

        // Step 2: Check initial mode indicator
        logger.info("Step 2: Checking initial mode indicator");
        kiosk.assertElementVisible(MODE_INDICATOR);
        String initialIndicator = driver.findElement(MODE_INDICATOR).getText();
        logger.info("  Initial indicator: {}", initialIndicator);
        assertTrue(initialIndicator.contains("Self") || initialIndicator.contains("self"), 
            "Indicator should show self-service");

        // Step 3: Toggle to assisted mode
        logger.info("Step 3: Toggling to assisted mode");
        toggleMode();

        // Step 4: Check assisted mode indicator
        logger.info("Step 4: Checking assisted mode indicator");
        String assistedIndicator = driver.findElement(MODE_INDICATOR).getText();
        logger.info("  Assisted indicator: {}", assistedIndicator);
        assertTrue(assistedIndicator.contains("Assisted") || assistedIndicator.contains("assisted"), 
            "Indicator should show assisted");

        // Step 5: Toggle back
        logger.info("Step 5: Toggling back to self-service");
        toggleMode();

        // Step 6: Check self-service indicator again
        logger.info("Step 6: Checking self-service indicator again");
        String finalIndicator = driver.findElement(MODE_INDICATOR).getText();
        logger.info("  Final indicator: {}", finalIndicator);
        assertTrue(finalIndicator.contains("Self") || finalIndicator.contains("self"), 
            "Indicator should show self-service again");

        logger.info("✅ MxP SMART Hybrid Mode Indicator test passed");
    }
}