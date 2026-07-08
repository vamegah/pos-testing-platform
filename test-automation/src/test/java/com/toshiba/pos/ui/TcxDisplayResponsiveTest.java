// test-automation/src/test/java/com/toshiba/pos/ui/TcxDisplayResponsiveTest.java

package com.toshiba.pos.ui;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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
 * TCx® Display Responsive UI Test
 * 
 * Resizes the Selenium window across the supported size/orientation matrix
 * from Phase 12.7 and asserts the layout doesn't clip/overflow at each combo.
 * 
 * Test Scenarios:
 *   - Data-driven over the size/orientation matrix
 *   - Valid combinations: 10.1", 12.1", 15.6" (landscape/portrait), 21.5" (landscape only)
 *   - Known-bad combo: 21.5" portrait (should show graceful fallback)
 *   - Asserts no clipping or overflow in the UI
 */
public class TcxDisplayResponsiveTest {

    private static final Logger logger = LogManager.getLogger(TcxDisplayResponsiveTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String DISPLAY_PROFILE = "TCx Display";

    // Resolutions for testing (mapped from Phase 12.7 matrix)
    // Size: 10.1" → 1280x800 (landscape), 800x1280 (portrait)
    // Size: 12.1" → 1280x800 (landscape), 800x1280 (portrait)
    // Size: 15.6" → 1920x1080 (landscape), 1080x1920 (portrait)
    // Size: 21.5" → 1920x1080 (landscape only)
    
    private static final Object[][] DISPLAY_MATRIX = {
        // name, size, width, height, orientation, expectedValid
        {"10.1 Landscape", 10.1, 1280, 800, "landscape", true},
        {"10.1 Portrait", 10.1, 800, 1280, "portrait", true},
        {"12.1 Landscape", 12.1, 1280, 800, "landscape", true},
        {"12.1 Portrait", 12.1, 800, 1280, "portrait", true},
        {"15.6 Landscape", 15.6, 1920, 1080, "landscape", true},
        {"15.6 Portrait", 15.6, 1080, 1920, "portrait", true},
        {"21.5 Landscape", 21.5, 1920, 1080, "landscape", true},
        {"21.5 Portrait (Invalid)", 21.5, 1080, 1920, "portrait", false}
    };

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
     * Helper: Load display profile.
     */
    private void loadDisplayProfile() {
        kiosk.loadProfile(DISPLAY_PROFILE);
        kiosk.waitForScreen("welcome");
        logger.info("Display profile loaded");
    }

    /**
     * Helper: Resize window to specified dimensions.
     */
    private void resizeWindow(int width, int height) {
        driver.manage().window().setSize(new Dimension(width, height));
        logger.info("Window resized to {}x{}", width, height);
        // Allow time for layout to adjust
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Helper: Check if any element has overflow/clipping.
     * Looks for elements that are partially hidden or overflowing.
     */
    private boolean hasOverflowOrClipping() {
        // Check for horizontal overflow
        String bodyWidth = driver.findElement(By.tagName("body")).getAttribute("scrollWidth");
        String windowWidth = String.valueOf(driver.manage().window().getSize().getWidth());
        
        try {
            int scrollWidth = Integer.parseInt(bodyWidth);
            int viewportWidth = Integer.parseInt(windowWidth);
            if (scrollWidth > viewportWidth) {
                logger.warn("Horizontal overflow detected: scrollWidth={} > viewportWidth={}", 
                    scrollWidth, viewportWidth);
                return true;
            }
        } catch (NumberFormatException e) {
            // If numbers can't be parsed, continue with other checks
        }

        // Check for elements that are clipped
        List<WebElement> elements = driver.findElements(By.cssSelector("*"));
        for (WebElement el : elements) {
            try {
                String overflow = el.getCssValue("overflow");
                if ("hidden".equals(overflow) || "clip".equals(overflow)) {
                    // Check if content is actually overflowing
                    String scrollWidthStr = el.getAttribute("scrollWidth");
                    String clientWidthStr = el.getAttribute("clientWidth");
                    String scrollHeightStr = el.getAttribute("scrollHeight");
                    String clientHeightStr = el.getAttribute("clientHeight");
                    
                    if (scrollWidthStr != null && clientWidthStr != null && 
                        scrollHeightStr != null && clientHeightStr != null) {
                        try {
                            int scrollW = Integer.parseInt(scrollWidthStr);
                            int clientW = Integer.parseInt(clientWidthStr);
                            int scrollH = Integer.parseInt(scrollHeightStr);
                            int clientH = Integer.parseInt(clientHeightStr);
                            
                            if (scrollW > clientW || scrollH > clientH) {
                                // Element has clipped content
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            // Skip this element
                        }
                    }
                }
            } catch (Exception e) {
                // Skip elements that can't be checked
            }
        }
        return false;
    }

    /**
     * Helper: Check if layout elements are visible.
     */
    private boolean areLayoutElementsVisible() {
        try {
            // Check key elements are visible
            WebElement header = driver.findElement(By.id("app-header"));
            WebElement screenContainer = driver.findElement(By.id("screen-container"));
            WebElement footer = driver.findElement(By.id("app-footer"));
            return header.isDisplayed() && screenContainer.isDisplayed() && footer.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper: Check if there is a graceful fallback (error message or fallback layout).
     */
    private boolean hasGracefulFallback() {
        // Check for error message or fallback layout
        try {
            WebElement errorMsg = driver.findElement(By.cssSelector(".error-message, .fallback-layout"));
            return errorMsg.isDisplayed();
        } catch (Exception e) {
            // Also check for the assist overlay or a "not supported" message
            try {
                WebElement notSupported = driver.findElement(By.cssSelector(".not-supported, .config-error"));
                return notSupported.isDisplayed();
            } catch (Exception ex) {
                return false;
            }
        }
    }

    /**
     * Test: TCx Display — Responsive Layout Across Matrix
     * 
     * Given: The display profile is loaded
     * When: The window is resized to each matrix combination
     * Then: The layout doesn't clip/overflow for valid combos
     *       Invalid combo shows graceful fallback
     */
    @Test(description = "TCx Display — Responsive layout across matrix",
          dataProvider = "displayMatrix")
    public void testResponsiveLayout(String name, double size, int width, int height, 
                                    String orientation, boolean expectedValid) {
        logger.info("=== TCx Display Responsive Layout Test ===");
        logger.info("  Combo: {}", name);
        logger.info("  Size: {}in, {}x{} ({})", size, width, height, orientation);
        logger.info("  Expected valid: {}", expectedValid);

        // Step 1: Load profile
        logger.info("Step 1: Loading display profile");
        loadDisplayProfile();

        // Step 2: Resize window to target dimensions
        logger.info("Step 2: Resizing window to {}x{}", width, height);
        resizeWindow(width, height);

        // Step 3: Verify layout elements are visible
        logger.info("Step 3: Verifying layout elements visible");
        boolean layoutVisible = areLayoutElementsVisible();
        logger.info("  Layout visible: {}", layoutVisible);
        assertTrue(layoutVisible, "Layout elements should be visible");

        // Step 4: Check for overflow/clipping
        logger.info("Step 4: Checking for overflow/clipping");
        boolean hasOverflow = hasOverflowOrClipping();
        logger.info("  Has overflow/clipping: {}", hasOverflow);

        if (expectedValid) {
            // Valid combo: no clipping/overflow
            assertFalse(hasOverflow, "Valid combo should not have overflow or clipping");
            logger.info("  ✅ No overflow detected for valid combo");
        } else {
            // Invalid combo: should have graceful fallback, not a crash
            if (hasOverflow) {
                logger.info("  Overflow detected for invalid combo (may be acceptable with fallback)");
            }
            // Check for graceful fallback
            boolean hasFallback = hasGracefulFallback();
            logger.info("  Has graceful fallback: {}", hasFallback);
            // For invalid combo, either no overflow OR has fallback is acceptable
            boolean acceptable = !hasOverflow || hasFallback;
            assertTrue(acceptable, 
                "Invalid combo should either have no overflow or show graceful fallback");
        }

        // Step 5: Verify the screen is responsive (content adjusts)
        logger.info("Step 5: Verifying responsive behavior");
        // Check that the content area uses the available space
        WebElement screenContainer = driver.findElement(By.id("screen-container"));
        Dimension containerSize = screenContainer.getSize();
        logger.info("  Container size: {}x{}", containerSize.getWidth(), containerSize.getHeight());
        
        // Container should be at least 80% of the window width
        int windowWidth = driver.manage().window().getSize().getWidth();
        assertTrue(containerSize.getWidth() > windowWidth * 0.8, 
            "Container should use most of the available width");

        logger.info("✅ TCx Display Responsive Layout test passed for: {}", name);
    }

    /**
     * Test: TCx Display — Invalid Combo Shows Graceful Fallback
     * 
     * Given: The display profile is loaded
     * When: An invalid combination (21.5" portrait) is applied
     * Then: A graceful fallback is shown (not a crash or blank screen)
     */
    @Test(description = "TCx Display — Invalid combo shows graceful fallback")
    public void testInvalidComboGracefulFallback() {
        logger.info("=== TCx Display Invalid Combo Graceful Fallback Test ===");

        // Step 1: Load profile
        logger.info("Step 1: Loading display profile");
        loadDisplayProfile();

        // Step 2: Apply invalid combo (21.5" portrait)
        int width = 1080;
        int height = 1920;
        logger.info("Step 2: Applying invalid combo: {}x{} (21.5\" portrait)", width, height);
        resizeWindow(width, height);

        // Step 3: Verify layout elements visible
        logger.info("Step 3: Verifying layout elements visible");
        assertTrue(areLayoutElementsVisible(), "Layout should still be visible");

        // Step 4: Check for graceful fallback
        logger.info("Step 4: Checking for graceful fallback");
        boolean hasFallback = hasGracefulFallback();
        logger.info("  Has graceful fallback: {}", hasFallback);

        // Step 5: Check for error message
        boolean hasErrorMessage = false;
        try {
            WebElement errorEl = driver.findElement(By.cssSelector(".error-message, .config-error, .not-supported"));
            hasErrorMessage = errorEl.isDisplayed();
            String errorText = errorEl.getText();
            logger.info("  Error message: {}", errorText);
        } catch (Exception e) {
            // No error message found
        }

        // Either has fallback or error message
        assertTrue(hasFallback || hasErrorMessage, 
            "Invalid combo should show graceful fallback or error message");

        // Step 6: Verify the UI doesn't crash
        logger.info("Step 5: Verifying UI is still functional");
        // Try to click the reset button
        try {
            kiosk.tap(BaseKioskPage.RESET_BTN);
            logger.info("  Reset button clickable after invalid combo");
        } catch (Exception e) {
            fail("UI should remain functional after invalid combo: " + e.getMessage());
        }

        logger.info("✅ TCx Display Invalid Combo Graceful Fallback test passed");
    }

    /**
     * Test: TCx Display — All Valid Combos No Overflow
     * 
     * Given: The display profile is loaded
     * When: Each valid combo is applied
     * Then: No overflow/clipping is detected
     */
    @Test(description = "TCx Display — All valid combos no overflow")
    public void testAllValidCombosNoOverflow() {
        logger.info("=== TCx Display All Valid Combos No Overflow Test ===");

        loadDisplayProfile();

        // Test only valid combinations
        Object[][] validCombos = Arrays.stream(DISPLAY_MATRIX)
            .filter(row -> (boolean) row[5])
            .toArray(Object[][]::new);

        logger.info("Testing {} valid combinations", validCombos.length);

        for (Object[] combo : validCombos) {
            String name = (String) combo[0];
            int width = (int) combo[2];
            int height = (int) combo[3];
            boolean expectedValid = (boolean) combo[5];

            logger.info("Testing: {} ({}x{})", name, width, height);
            
            resizeWindow(width, height);
            
            // Check for overflow
            boolean hasOverflow = hasOverflowOrClipping();
            logger.info("  Has overflow: {}", hasOverflow);
            assertFalse(hasOverflow, "Valid combo '" + name + "' should not have overflow");

            // Verify layout visible
            assertTrue(areLayoutElementsVisible(), "Layout should be visible for " + name);

            // Reset for next combo
            kiosk.reset();
            loadDisplayProfile();
        }

        logger.info("✅ TCx Display All Valid Combos No Overflow test passed");
    }

    /**
     * Data Provider: Display matrix combinations.
     */
    @DataProvider(name = "displayMatrix")
    public Object[][] displayMatrix() {
        return DISPLAY_MATRIX;
    }
}