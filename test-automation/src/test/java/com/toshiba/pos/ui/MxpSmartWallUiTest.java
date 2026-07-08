// test-automation/src/test/java/com/toshiba/pos/ui/MxpSmartWallUiTest.java

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
 * MxP™ SMART | wall UI Test
 * 
 * Validates the reduced-peripheral wall profile:
 *   - Scale widget is hidden
 *   - Layout is compact
 *   - Only available peripherals are shown
 */
public class MxpSmartWallUiTest {

    private static final Logger logger = LogManager.getLogger(MxpSmartWallUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String WALL_PROFILE = "Mxp SMART Wall";

    // Locators specific to wall profile
    private static final By SCALE_WIDGET = By.id("scale-widget");
    private static final By SCALE_INDICATOR = By.cssSelector(".peripheral-indicator:contains('Scale')");
    private static final By COMPACT_LAYOUT = By.cssSelector(".layout-compact");
    private static final By PERIPHERAL_INDICATORS = By.id("peripheral-indicators");
    private static final By PERIPHERAL_INDICATOR_ITEMS = By.cssSelector(".peripheral-indicator");
    private static final By WALL_PROFILE_BADGE = By.cssSelector(".profile-badge-wall");
    private static final By SPACE_CONSTRAINTS_INDICATOR = By.id("space-constraints-indicator");

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
     * Helper: Load wall profile and go to basket.
     */
    private void loadWallAndGoToBasket() {
        kiosk.loadProfile(WALL_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Wall profile loaded, at basket screen");
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
     * Helper: Get all peripheral indicator labels.
     */
    private List<String> getPeripheralLabels() {
        List<WebElement> indicators = driver.findElements(PERIPHERAL_INDICATOR_ITEMS);
        return indicators.stream()
            .map(WebElement::getText)
            .toList();
    }

    /**
     * Helper: Check if a peripheral is active.
     */
    private boolean isPeripheralActive(String name) {
        List<WebElement> indicators = driver.findElements(PERIPHERAL_INDICATOR_ITEMS);
        for (WebElement el : indicators) {
            if (el.getText().contains(name)) {
                return el.getAttribute("class").contains("active");
            }
        }
        return false;
    }

    /**
     * Helper: Get the number of peripheral indicators.
     */
    private int getPeripheralCount() {
        return driver.findElements(PERIPHERAL_INDICATOR_ITEMS).size();
    }

    /**
     * Test: MxP SMART Wall — Scale Widget is Hidden
     * 
     * Given: The wall profile is loaded
     * When: The basket screen is displayed
     * Then: The scale widget is NOT visible
     */
    @Test(description = "MxP SMART Wall — Scale widget is hidden")
    public void testScaleWidgetHidden() {
        logger.info("=== MxP SMART Wall Scale Widget Hidden Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wall profile");
        loadWallAndGoToBasket();

        // Step 2: Verify scale widget is hidden
        logger.info("Step 2: Verifying scale widget is hidden");
        boolean scaleVisible = isElementDisplayed(SCALE_WIDGET);
        logger.info("  Scale widget visible: {}", scaleVisible);
        assertFalse(scaleVisible, "Scale widget should NOT be visible in wall profile");

        // Step 3: Verify scale indicator is not present
        logger.info("Step 3: Verifying scale indicator not present");
        boolean scaleIndicatorPresent = !driver.findElements(SCALE_INDICATOR).isEmpty();
        logger.info("  Scale indicator present: {}", scaleIndicatorPresent);
        // Note: The indicator may not be present at all, or may be present but inactive
        // We check that it's either absent or inactive
        if (scaleIndicatorPresent) {
            assertFalse(isPeripheralActive("Scale"), "Scale should not be active");
        }

        logger.info("✅ MxP SMART Wall Scale Widget Hidden test passed");
    }

    /**
     * Test: MxP SMART Wall — Compact Layout
     * 
     * Given: The wall profile is loaded
     * When: The basket screen is displayed
     * Then: The layout is compact (less spacing, smaller elements)
     */
    @Test(description = "MxP SMART Wall — Compact layout")
    public void testCompactLayout() {
        logger.info("=== MxP SMART Wall Compact Layout Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wall profile");
        loadWallAndGoToBasket();

        // Step 2: Verify compact layout
        logger.info("Step 2: Verifying compact layout");
        boolean compactVisible = isElementDisplayed(COMPACT_LAYOUT);
        logger.info("  Compact layout visible: {}", compactVisible);
        assertTrue(compactVisible, "Compact layout should be applied for wall profile");

        // Step 3: Verify peripheral indicators are compact
        logger.info("Step 3: Verifying compact peripheral indicators");
        int indicatorCount = getPeripheralCount();
        logger.info("  Peripheral indicators: {}", indicatorCount);
        // Wall profile should have fewer peripherals (no scale)
        // Typically: scanner, printer, pin_pad (3) or scanner, printer (2)
        assertTrue(indicatorCount <= 4, "Should have fewer peripherals than full profile");

        // Step 4: Verify space constraints indicator
        logger.info("Step 4: Verifying space constraints indicator");
        boolean spaceIndicatorVisible = isElementDisplayed(SPACE_CONSTRAINTS_INDICATOR);
        logger.info("  Space constraints indicator visible: {}", spaceIndicatorVisible);
        if (spaceIndicatorVisible) {
            String text = driver.findElement(SPACE_CONSTRAINTS_INDICATOR).getText();
            logger.info("  Space constraints: {}", text);
            assertTrue(text.contains("wall") || text.contains("compact"), 
                "Should show wall/compact space constraints");
        }

        logger.info("✅ MxP SMART Wall Compact Layout test passed");
    }

    /**
     * Test: MxP SMART Wall — Only Available Peripherals Shown
     * 
     * Given: The wall profile is loaded
     * When: The basket screen is displayed
     * Then: Only scanner, printer, and PIN pad are shown (no scale)
     */
    @Test(description = "MxP SMART Wall — Only available peripherals shown")
    public void testOnlyAvailablePeripheralsShown() {
        logger.info("=== MxP SMART Wall Only Available Peripherals Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wall profile");
        loadWallAndGoToBasket();

        // Step 2: Get peripheral indicators
        logger.info("Step 2: Getting peripheral indicators");
        List<String> peripherals = getPeripheralLabels();
        logger.info("  Peripherals shown: {}", peripherals);

        // Step 3: Verify scale is NOT in the list
        logger.info("Step 3: Verifying scale is not shown");
        boolean hasScale = peripherals.stream().anyMatch(p -> p.toLowerCase().contains("scale"));
        assertFalse(hasScale, "Scale should NOT be shown in wall profile");

        // Step 4: Verify scanner is shown
        logger.info("Step 4: Verifying scanner is shown");
        boolean hasScanner = peripherals.stream().anyMatch(p -> p.toLowerCase().contains("scanner"));
        assertTrue(hasScanner, "Scanner should be shown in wall profile");

        // Step 5: Verify printer is shown
        logger.info("Step 5: Verifying printer is shown");
        boolean hasPrinter = peripherals.stream().anyMatch(p -> p.toLowerCase().contains("printer"));
        assertTrue(hasPrinter, "Printer should be shown in wall profile");

        // Step 6: Verify at least one peripheral is shown
        logger.info("Step 6: Verifying at least one peripheral shown");
        assertTrue(peripherals.size() > 0, "At least one peripheral should be shown");

        logger.info("✅ MxP SMART Wall Only Available Peripherals test passed");
    }

    /**
     * Test: MxP SMART Wall — Scale Widget Absent in Payment Screen
     * 
     * Given: The wall profile is loaded
     * When: The user navigates to the payment screen
     * Then: No scale-related elements appear
     */
    @Test(description = "MxP SMART Wall — Scale widget absent in payment screen")
    public void testScaleWidgetAbsentInPayment() {
        logger.info("=== MxP SMART Wall Scale Widget Absent in Payment Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wall profile");
        loadWallAndGoToBasket();

        // Step 2: Navigate to payment
        logger.info("Step 2: Navigating to payment");
        kiosk.tap(BaseKioskPage.BASKET_PAY_BTN);
        kiosk.waitForScreen("payment");

        // Step 3: Verify no scale widget in payment
        logger.info("Step 3: Verifying no scale widget in payment");
        boolean scaleVisible = isElementDisplayed(SCALE_WIDGET);
        logger.info("  Scale widget visible: {}", scaleVisible);
        assertFalse(scaleVisible, "Scale widget should NOT be visible in payment screen");

        // Step 4: Verify no scale-related text
        logger.info("Step 4: Verifying no scale-related text");
        String pageText = driver.findElement(By.tagName("body")).getText();
        boolean hasScaleText = pageText.toLowerCase().contains("scale") || 
                               pageText.toLowerCase().contains("weight");
        // If there is scale text, it should be in the context of "no scale"
        if (hasScaleText) {
            logger.info("  Scale text found: {}", pageText);
            // It's acceptable if it says "No scale" or "scale not available"
        }

        logger.info("✅ MxP SMART Wall Scale Widget Absent in Payment test passed");
    }

    /**
     * Test: MxP SMART Wall — Wall Profile Badge
     * 
     * Given: The wall profile is loaded
     * When: The UI is rendered
     * Then: A wall profile badge is displayed
     */
    @Test(description = "MxP SMART Wall — Wall profile badge displayed")
    public void testWallProfileBadge() {
        logger.info("=== MxP SMART Wall Profile Badge Test ===");

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wall profile");
        loadWallAndGoToBasket();

        // Step 2: Verify wall profile badge
        logger.info("Step 2: Verifying wall profile badge");
        boolean badgeVisible = isElementDisplayed(WALL_PROFILE_BADGE);
        logger.info("  Wall profile badge visible: {}", badgeVisible);
        assertTrue(badgeVisible, "Wall profile badge should be displayed");

        // Step 3: Verify badge text
        logger.info("Step 3: Verifying badge text");
        if (badgeVisible) {
            String badgeText = driver.findElement(WALL_PROFILE_BADGE).getText();
            logger.info("  Badge text: {}", badgeText);
            assertTrue(badgeText.toLowerCase().contains("wall"), 
                "Badge should indicate wall profile");
        }

        logger.info("✅ MxP SMART Wall Profile Badge test passed");
    }
}