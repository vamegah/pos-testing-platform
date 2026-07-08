// test-automation/src/test/java/com/toshiba/pos/ui/MxpSmartWingUiTest.java

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
 * MxP™ SMART | wing UI Test
 * 
 * Validates that peripheral panel icons appear/disappear to match
 * each of the ≥3 combination fixtures from Phase 12.5.
 * 
 * Test Scenarios (data-driven over combinations):
 *   1. Full Configuration: all peripherals visible
 *   2. Minimal Configuration: scanner + printer only
 *   3. Self-Service Configuration: scanner + PIN pad only
 */
public class MxpSmartWingUiTest {

    private static final Logger logger = LogManager.getLogger(MxpSmartWingUiTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String WING_PROFILE = "Mxp SMART Wing";

    // Locators
    private static final By PERIPHERAL_PANEL = By.id("peripheral-panel");
    private static final By PERIPHERAL_ICON_SCANNER = By.id("peripheral-icon-scanner");
    private static final By PERIPHERAL_ICON_SCALE = By.id("peripheral-icon-scale");
    private static final By PERIPHERAL_ICON_PRINTER = By.id("peripheral-icon-printer");
    private static final By PERIPHERAL_ICON_PIN_PAD = By.id("peripheral-icon-pin-pad");
    private static final By PERIPHERAL_ICONS = By.cssSelector(".peripheral-icon");

    // Combination configuration data provider
    @DataProvider(name = "peripheralCombinations")
    public Object[][] peripheralCombinations() {
        return new Object[][] {
            // Combination 1: Full Configuration (all peripherals)
            {
                "Full Configuration",
                "full",
                Map.of(
                    "scanner", true,
                    "scale", true,
                    "printer", true,
                    "pin_pad", true
                )
            },
            // Combination 2: Minimal Configuration (scanner + printer only)
            {
                "Minimal Configuration",
                "minimal",
                Map.of(
                    "scanner", true,
                    "scale", false,
                    "printer", true,
                    "pin_pad", false
                )
            },
            // Combination 3: Self-Service Configuration (scanner + PIN pad only)
            {
                "Self-Service Configuration",
                "self-service",
                Map.of(
                    "scanner", true,
                    "scale", false,
                    "printer", false,
                    "pin_pad", true
                )
            }
        };
    }

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
     * Helper: Load wing profile and go to basket.
     */
    private void loadWingAndGoToBasket() {
        kiosk.loadProfile(WING_PROFILE);
        kiosk.waitForScreen("welcome");
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");
        logger.info("Wing profile loaded, at basket screen");
    }

    /**
     * Helper: Apply a peripheral combination via UI controls.
     */
    private void applyCombination(String combinationId) {
        // Click the combination preset button
        By comboBtn = By.id("combo-" + combinationId);
        kiosk.tap(comboBtn);
        logger.info("Applied combination: {}", combinationId);
        // Allow time for UI update
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper: Check if a peripheral icon is visible.
     */
    private boolean isIconVisible(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            return el.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Get all visible peripheral icon labels.
     */
    private List<String> getVisibleIconLabels() {
        List<String> labels = new ArrayList<>();
        List<WebElement> icons = driver.findElements(PERIPHERAL_ICONS);
        for (WebElement icon : icons) {
            if (icon.isDisplayed()) {
                labels.add(icon.getText().toLowerCase());
            }
        }
        return labels;
    }

    /**
     * Test: MxP SMART Wing — Peripheral icons match combination
     * 
     * Given: A wing profile is loaded
     * When: A combination is applied
     * Then: The peripheral panel shows only the enabled peripherals
     */
    @Test(description = "MxP SMART Wing — Peripheral icons match combination",
          dataProvider = "peripheralCombinations")
    public void testPeripheralIconsMatchCombination(String name, String comboId, 
                                                    Map<String, Boolean> expected) {
        logger.info("=== MxP SMART Wing Peripheral Icons Test ===");
        logger.info("  Combination: {}", name);
        logger.info("  Expected: {}", expected);

        // Step 1: Load profile and go to basket
        logger.info("Step 1: Loading wing profile");
        loadWingAndGoToBasket();

        // Step 2: Apply the combination
        logger.info("Step 2: Applying combination: {}", comboId);
        applyCombination(comboId);

        // Step 3: Verify peripheral panel is visible
        logger.info("Step 3: Verifying peripheral panel");
        kiosk.assertElementVisible(PERIPHERAL_PANEL);

        // Step 4: Verify each peripheral icon state
        logger.info("Step 4: Verifying individual icons");

        // Scanner
        boolean scannerVisible = isIconVisible(PERIPHERAL_ICON_SCANNER);
        assertEquals(scannerVisible, expected.get("scanner"), 
            "Scanner icon visibility should match expected");
        logger.info("  Scanner: visible={}, expected={}", scannerVisible, expected.get("scanner"));

        // Scale
        boolean scaleVisible = isIconVisible(PERIPHERAL_ICON_SCALE);
        assertEquals(scaleVisible, expected.get("scale"), 
            "Scale icon visibility should match expected");
        logger.info("  Scale: visible={}, expected={}", scaleVisible, expected.get("scale"));

        // Printer
        boolean printerVisible = isIconVisible(PERIPHERAL_ICON_PRINTER);
        assertEquals(printerVisible, expected.get("printer"), 
            "Printer icon visibility should match expected");
        logger.info("  Printer: visible={}, expected={}", printerVisible, expected.get("printer"));

        // PIN Pad
        boolean pinPadVisible = isIconVisible(PERIPHERAL_ICON_PIN_PAD);
        assertEquals(pinPadVisible, expected.get("pin_pad"), 
            "PIN Pad icon visibility should match expected");
        logger.info("  PIN Pad: visible={}, expected={}", pinPadVisible, expected.get("pin_pad"));

        // Step 5: Verify only expected peripherals are visible
        logger.info("Step 5: Verifying only expected peripherals visible");
        List<String> visibleIcons = getVisibleIconLabels();
        logger.info("  Visible icons: {}", visibleIcons);

        // Count visible icons
        int expectedCount = 0;
        for (boolean v : expected.values()) {
            if (v) expectedCount++;
        }
        assertEquals(visibleIcons.size(), expectedCount, 
            "Number of visible icons should match expected count");
        logger.info("  Icon count: {} (expected: {})", visibleIcons.size(), expectedCount);

        logger.info("✅ MxP SMART Wing Peripheral Icons test passed for: {}", name);
    }

    /**
     * Test: MxP SMART Wing — Full Configuration (all peripherals)
     * 
     * Given: The wing profile is loaded
     * When: The full configuration is applied
     * Then: All four peripheral icons are visible
     */
    @Test(description = "MxP SMART Wing — Full configuration all peripherals visible")
    public void testFullConfiguration() {
        logger.info("=== MxP SMART Wing Full Configuration Test ===");

        loadWingAndGoToBasket();
        applyCombination("full");

        // Verify all peripherals visible
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner should be visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale should be visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer should be visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad should be visible");

        // Verify count
        List<String> icons = getVisibleIconLabels();
        assertEquals(icons.size(), 4, "All 4 peripherals should be visible");

        logger.info("✅ MxP SMART Wing Full Configuration test passed");
    }

    /**
     * Test: MxP SMART Wing — Minimal Configuration (scanner + printer only)
     * 
     * Given: The wing profile is loaded
     * When: The minimal configuration is applied
     * Then: Only scanner and printer icons are visible
     */
    @Test(description = "MxP SMART Wing — Minimal configuration scanner + printer only")
    public void testMinimalConfiguration() {
        logger.info("=== MxP SMART Wing Minimal Configuration Test ===");

        loadWingAndGoToBasket();
        applyCombination("minimal");

        // Verify only scanner and printer visible
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner should be visible");
        assertFalse(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale should NOT be visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer should be visible");
        assertFalse(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad should NOT be visible");

        // Verify count
        List<String> icons = getVisibleIconLabels();
        assertEquals(icons.size(), 2, "Only 2 peripherals should be visible");

        // Verify the right ones are visible
        assertTrue(icons.contains("scanner"), "Scanner should be in visible list");
        assertTrue(icons.contains("printer"), "Printer should be in visible list");
        assertFalse(icons.contains("scale"), "Scale should NOT be in visible list");
        assertFalse(icons.contains("pin pad"), "PIN Pad should NOT be in visible list");

        logger.info("✅ MxP SMART Wing Minimal Configuration test passed");
    }

    /**
     * Test: MxP SMART Wing — Self-Service Configuration (scanner + PIN pad only)
     * 
     * Given: The wing profile is loaded
     * When: The self-service configuration is applied
     * Then: Only scanner and PIN pad icons are visible
     */
    @Test(description = "MxP SMART Wing — Self-Service configuration scanner + PIN pad only")
    public void testSelfServiceConfiguration() {
        logger.info("=== MxP SMART Wing Self-Service Configuration Test ===");

        loadWingAndGoToBasket();
        applyCombination("self-service");

        // Verify only scanner and PIN pad visible
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner should be visible");
        assertFalse(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale should NOT be visible");
        assertFalse(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer should NOT be visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad should be visible");

        // Verify count
        List<String> icons = getVisibleIconLabels();
        assertEquals(icons.size(), 2, "Only 2 peripherals should be visible");

        // Verify the right ones are visible
        assertTrue(icons.contains("scanner"), "Scanner should be in visible list");
        assertTrue(icons.contains("pin pad"), "PIN Pad should be in visible list");
        assertFalse(icons.contains("scale"), "Scale should NOT be in visible list");
        assertFalse(icons.contains("printer"), "Printer should NOT be in visible list");

        logger.info("✅ MxP SMART Wing Self-Service Configuration test passed");
    }

    /**
     * Test: MxP SMART Wing — Switch between combinations
     * 
     * Given: The wing profile is loaded
     * When: Switching between combinations
     * Then: Peripheral icons update correctly
     */
    @Test(description = "MxP SMART Wing — Switch between combinations")
    public void testSwitchCombinations() {
        logger.info("=== MxP SMART Wing Switch Combinations Test ===");

        loadWingAndGoToBasket();

        // Step 1: Apply full configuration
        logger.info("Step 1: Applying full configuration");
        applyCombination("full");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner visible (full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale visible (full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer visible (full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad visible (full)");

        // Step 2: Switch to minimal
        logger.info("Step 2: Switching to minimal configuration");
        applyCombination("minimal");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner visible (minimal)");
        assertFalse(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale hidden (minimal)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer visible (minimal)");
        assertFalse(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad hidden (minimal)");

        // Step 3: Switch to self-service
        logger.info("Step 3: Switching to self-service configuration");
        applyCombination("self-service");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner visible (self-service)");
        assertFalse(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale hidden (self-service)");
        assertFalse(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer hidden (self-service)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad visible (self-service)");

        // Step 4: Switch back to full
        logger.info("Step 4: Switching back to full configuration");
        applyCombination("full");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner visible (back to full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale visible (back to full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer visible (back to full)");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad visible (back to full)");

        logger.info("✅ MxP SMART Wing Switch Combinations test passed");
    }

    /**
     * Test: MxP SMART Wing — Disabled peripherals are never called
     * 
     * Given: A combination with disabled peripherals
     * When: The user interacts with the UI
     * Then: Disabled peripheral icons are not clickable
     */
    @Test(description = "MxP SMART Wing — Disabled peripherals are not interactive")
    public void testDisabledPeripheralsNotInteractive() {
        logger.info("=== MxP SMART Wing Disabled Peripherals Not Interactive Test ===");

        loadWingAndGoToBasket();

        // Apply minimal configuration (scale and PIN pad disabled)
        applyCombination("minimal");

        // Verify disabled peripherals are not visible
        assertFalse(isIconVisible(PERIPHERAL_ICON_SCALE), "Scale icon hidden");
        assertFalse(isIconVisible(PERIPHERAL_ICON_PIN_PAD), "PIN Pad icon hidden");

        // Verify enabled peripherals are visible
        assertTrue(isIconVisible(PERIPHERAL_ICON_SCANNER), "Scanner visible");
        assertTrue(isIconVisible(PERIPHERAL_ICON_PRINTER), "Printer visible");

        // Check that disabled peripherals are not in the DOM or are hidden
        // This prevents them from being accidentally clicked
        List<WebElement> allIcons = driver.findElements(PERIPHERAL_ICONS);
        for (WebElement icon : allIcons) {
            String label = icon.getText().toLowerCase();
            if (label.contains("scale") || label.contains("pin pad")) {
                assertFalse(icon.isDisplayed(), "Disabled peripheral should not be displayed: " + label);
            }
        }

        logger.info("✅ MxP SMART Wing Disabled Peripherals Not Interactive test passed");
    }
}