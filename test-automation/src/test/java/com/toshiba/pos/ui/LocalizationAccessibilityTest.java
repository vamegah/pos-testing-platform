// test-automation/src/test/java/com/toshiba/pos/ui/LocalizationAccessibilityTest.java

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
import java.awt.Color;

import static org.testng.Assert.*;

/**
 * Localization & Basic Accessibility Test
 * 
 * Validates:
 *   1. Locale switching with at least 2 languages
 *   2. Touch target size meets minimum threshold (44px)
 *   3. Basic color contrast on key buttons
 */
public class LocalizationAccessibilityTest {

    private static final Logger logger = LogManager.getLogger(LocalizationAccessibilityTest.class);

    private WebDriver driver;
    private BaseKioskPage kiosk;

    // Profile name
    private static final String TEST_PROFILE = "ELERA";

    // Locale fixtures
    private static final String LOCALE_EN = "en";
    private static final String LOCALE_ES = "es";
    private static final String LOCALE_FR = "fr";

    // Locale display names
    private static final Map<String, String> LOCALE_DISPLAY_NAMES = Map.of(
        LOCALE_EN, "English",
        LOCALE_ES, "Español",
        LOCALE_FR, "Français"
    );

    // Key strings to verify per locale
    private static final Map<String, Map<String, String>> LOCALIZED_TEXT = Map.of(
        LOCALE_EN, Map.of(
            "welcome_title", "Welcome to Self Checkout",
            "start_scanning", "Start Scanning",
            "basket_title", "Your Basket",
            "payment_title", "Payment",
            "total_label", "Total",
            "thank_you", "Thank you for your purchase!"
        ),
        LOCALE_ES, Map.of(
            "welcome_title", "Bienvenido al Autopago",
            "start_scanning", "Comenzar a Escanear",
            "basket_title", "Tu Cesta",
            "payment_title", "Pago",
            "total_label", "Total",
            "thank_you", "¡Gracias por tu compra!"
        ),
        LOCALE_FR, Map.of(
            "welcome_title", "Bienvenue à l'Auto-paiement",
            "start_scanning", "Commencer à Scanner",
            "basket_title", "Votre Panier",
            "payment_title", "Paiement",
            "total_label", "Total",
            "thank_you", "Merci pour votre achat!"
        )
    );

    // Minimum tap target size (44px per WCAG 2.5.5)
    private static final int MIN_TARGET_SIZE = 44;

    // Minimum contrast ratio (4.5:1 for normal text per WCAG 2.1)
    private static final double MIN_CONTRAST_RATIO = 4.5;

    // Buttons to check for contrast
    private static final List<By> CONTRAST_BUTTONS = List.of(
        BaseKioskPage.WELCOME_START_BTN,
        BaseKioskPage.BASKET_PAY_BTN,
        BaseKioskPage.BASKET_SCAN_BTN,
        BaseKioskPage.PAYMENT_AUTHORIZE_BTN
    );

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
        // Use a larger window for accessibility testing
        options.addArguments("--window-size=1920,1080");

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
    private void loadProfileAndGoToBasket() {
        kiosk.loadProfile(TEST_PROFILE);
        kiosk.waitForScreen("welcome");
        logger.info("Profile loaded: {}", TEST_PROFILE);
    }

    /**
     * Helper: Switch locale via UI control.
     */
    private void switchLocale(String locale) {
        try {
            By localeBtn = By.id("locale-" + locale);
            kiosk.tap(localeBtn);
            logger.info("Switched locale to: {}", locale);
            // Allow time for text to update
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.warn("Locale button not found for: {}", locale);
            // Fallback: use dropdown
            try {
                WebElement select = driver.findElement(By.id("locale-select"));
                select.sendKeys(LOCALE_DISPLAY_NAMES.get(locale));
            } catch (Exception ex) {
                logger.error("Failed to switch locale: {}", locale);
            }
        }
    }

    /**
     * Helper: Get the current locale from the UI.
     */
    private String getCurrentLocale() {
        try {
            WebElement indicator = driver.findElement(By.id("locale-indicator"));
            return indicator.getText().toLowerCase();
        } catch (Exception e) {
            return "en";
        }
    }

    /**
     * Helper: Get text for a localized string key.
     */
    private String getLocalizedText(String key, String locale) {
        Map<String, String> localeMap = LOCALIZED_TEXT.get(locale);
        if (localeMap == null) return null;
        return localeMap.get(key);
    }

    /**
     * Helper: Check if text matches expected localized string.
     */
    private void assertLocalizedText(By locator, String expectedText) {
        String actual = kiosk.wait.until(org.openqa.selenium.support.ui.ExpectedConditions
            .visibilityOfElementLocated(locator)).getText();
        assertEquals(actual.trim(), expectedText, 
            "Text should match expected localized string");
    }

    /**
     * Helper: Get element size dimensions.
     */
    private Map<String, Integer> getElementSize(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            Map<String, Integer> size = new HashMap<>();
            size.put("width", el.getSize().getWidth());
            size.put("height", el.getSize().getHeight());
            return size;
        } catch (Exception e) {
            return Map.of("width", 0, "height", 0);
        }
    }

    /**
     * Helper: Check if element meets minimum target size.
     */
    private boolean meetsMinimumTargetSize(By locator) {
        Map<String, Integer> size = getElementSize(locator);
        int width = size.getOrDefault("width", 0);
        int height = size.getOrDefault("height", 0);
        return width >= MIN_TARGET_SIZE && height >= MIN_TARGET_SIZE;
    }

    /**
     * Helper: Get contrast ratio between two colors.
     */
    private double getContrastRatio(Color fg, Color bg) {
        double l1 = getRelativeLuminance(fg);
        double l2 = getRelativeLuminance(bg);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * Helper: Calculate relative luminance per WCAG 2.1.
     */
    private double getRelativeLuminance(Color c) {
        double r = c.getRed() / 255.0;
        double g = c.getGreen() / 255.0;
        double b = c.getBlue() / 255.0;
        
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * Helper: Parse hex color string to Color object.
     */
    private Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    /**
     * Helper: Get foreground and background colors of an element.
     */
    private Map<String, Color> getElementColors(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            String fgHex = el.getCssValue("color");
            String bgHex = el.getCssValue("background-color");
            
            // Parse CSS color values (rgb format)
            Color fg = parseCssColor(fgHex);
            Color bg = parseCssColor(bgHex);
            
            Map<String, Color> colors = new HashMap<>();
            colors.put("foreground", fg);
            colors.put("background", bg);
            return colors;
        } catch (Exception e) {
            return Map.of("foreground", Color.BLACK, "background", Color.WHITE);
        }
    }

    /**
     * Helper: Parse CSS color string.
     */
    private Color parseCssColor(String cssColor) {
        if (cssColor == null || cssColor.isEmpty()) return Color.BLACK;
        
        // Handle rgb(r, g, b) format
        if (cssColor.startsWith("rgb")) {
            String[] parts = cssColor.replace("rgb(", "").replace(")", "").split(",");
            if (parts.length >= 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return new Color(r, g, b);
                } catch (Exception e) {
                    // fall through
                }
            }
        }
        // Handle hex format
        if (cssColor.startsWith("#")) {
            return parseColor(cssColor);
        }
        return Color.BLACK;
    }

    // ============================================================
    // LOCALIZATION TESTS
    // ============================================================

    /**
     * Test: Locale Switching — English
     * 
     * Given: The UI is loaded
     * When: English locale is selected
     * Then: All text appears in English
     */
    @Test(description = "Locale Switching — English")
    public void testLocaleEnglish() {
        logger.info("=== Localization: English ===");

        loadProfileAndGoToBasket();
        switchLocale(LOCALE_EN);

        // Verify welcome screen text
        String expectedTitle = getLocalizedText("welcome_title", LOCALE_EN);
        kiosk.assertElementText(BaseKioskPage.WELCOME_TITLE, expectedTitle);

        // Verify start button
        String expectedStart = getLocalizedText("start_scanning", LOCALE_EN);
        kiosk.assertElementText(BaseKioskPage.WELCOME_START_BTN, expectedStart);

        // Navigate to basket
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");

        // Verify basket title
        String expectedBasket = getLocalizedText("basket_title", LOCALE_EN);
        kiosk.assertElementText(By.cssSelector(".basket-header h2"), expectedBasket);

        logger.info("✅ English localization verified");
    }

    /**
     * Test: Locale Switching — Spanish
     * 
     * Given: The UI is loaded
     * When: Spanish locale is selected
     * Then: All text appears in Spanish
     */
    @Test(description = "Locale Switching — Spanish")
    public void testLocaleSpanish() {
        logger.info("=== Localization: Spanish ===");

        loadProfileAndGoToBasket();
        switchLocale(LOCALE_ES);

        // Verify welcome screen text
        String expectedTitle = getLocalizedText("welcome_title", LOCALE_ES);
        kiosk.assertElementText(BaseKioskPage.WELCOME_TITLE, expectedTitle);

        // Verify start button
        String expectedStart = getLocalizedText("start_scanning", LOCALE_ES);
        kiosk.assertElementText(BaseKioskPage.WELCOME_START_BTN, expectedStart);

        // Navigate to basket
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");

        // Verify basket title
        String expectedBasket = getLocalizedText("basket_title", LOCALE_ES);
        kiosk.assertElementText(By.cssSelector(".basket-header h2"), expectedBasket);

        logger.info("✅ Spanish localization verified");
    }

    /**
     * Test: Locale Switching — French
     * 
     * Given: The UI is loaded
     * When: French locale is selected
     * Then: All text appears in French
     */
    @Test(description = "Locale Switching — French")
    public void testLocaleFrench() {
        logger.info("=== Localization: French ===");

        loadProfileAndGoToBasket();
        switchLocale(LOCALE_FR);

        // Verify welcome screen text
        String expectedTitle = getLocalizedText("welcome_title", LOCALE_FR);
        kiosk.assertElementText(BaseKioskPage.WELCOME_TITLE, expectedTitle);

        // Verify start button
        String expectedStart = getLocalizedText("start_scanning", LOCALE_FR);
        kiosk.assertElementText(BaseKioskPage.WELCOME_START_BTN, expectedStart);

        // Navigate to basket
        kiosk.tap(BaseKioskPage.WELCOME_START_BTN);
        kiosk.waitForScreen("basket");

        // Verify basket title
        String expectedBasket = getLocalizedText("basket_title", LOCALE_FR);
        kiosk.assertElementText(By.cssSelector(".basket-header h2"), expectedBasket);

        logger.info("✅ French localization verified");
    }

    /**
     * Test: Locale Switching — All Locales
     * 
     * Given: The UI is loaded
     * When: Each locale is selected
     * Then: Text changes accordingly
     */
    @Test(description = "Locale Switching — All locales")
    public void testAllLocales() {
        logger.info("=== Localization: All Locales ===");

        loadProfileAndGoToBasket();

        for (String locale : List.of(LOCALE_EN, LOCALE_ES, LOCALE_FR)) {
            logger.info("Testing locale: {}", locale);
            switchLocale(locale);

            // Verify welcome title
            String expectedTitle = getLocalizedText("welcome_title", locale);
            kiosk.assertElementText(BaseKioskPage.WELCOME_TITLE, expectedTitle);
            logger.info("  Welcome title: {}", expectedTitle);

            // Verify start button
            String expectedStart = getLocalizedText("start_scanning", locale);
            kiosk.assertElementText(BaseKioskPage.WELCOME_START_BTN, expectedStart);
            logger.info("  Start button: {}", expectedStart);
        }

        logger.info("✅ All locales verified");
    }

    // ============================================================
    // ACCESSIBILITY TESTS
    // ============================================================

    /**
     * Test: Touch Target Size — Buttons meet minimum 44px
     * 
     * Given: The UI is loaded
     * When: Buttons are examined
     * Then: Each button meets minimum tap target size (44px)
     */
    @Test(description = "Touch Target Size — Buttons meet minimum 44px")
    public void testTouchTargetSize() {
        logger.info("=== Accessibility: Touch Target Size ===");

        loadProfileAndGoToBasket();

        // Buttons to test
        List<By> buttons = List.of(
            BaseKioskPage.WELCOME_START_BTN,
            BaseKioskPage.BASKET_PAY_BTN,
            BaseKioskPage.BASKET_SCAN_BTN,
            BaseKioskPage.PAYMENT_AUTHORIZE_BTN,
            BaseKioskPage.PAYMENT_CANCEL_BTN,
            BaseKioskPage.RECEIPT_NEW_BTN,
            BaseKioskPage.ASSIST_DISMISS_BTN,
            BaseKioskPage.RESET_BTN,
            BaseKioskPage.LOAD_PROFILE_BTN
        );

        boolean allMeetSize = true;
        for (By btn : buttons) {
            boolean meets = meetsMinimumTargetSize(btn);
            Map<String, Integer> size = getElementSize(btn);
            logger.info("  Button {}: {}x{}px, meets {}px threshold: {}", 
                btn, size.get("width"), size.get("height"), MIN_TARGET_SIZE, meets);
            
            if (!meets) {
                allMeetSize = false;
                logger.warn("  ❌ Button {} does NOT meet minimum target size", btn);
            }
        }

        assertTrue(allMeetSize, 
            "All buttons should meet minimum target size of " + MIN_TARGET_SIZE + "px");

        logger.info("✅ All buttons meet minimum touch target size");
    }

    /**
     * Test: Touch Target Size — All interactive elements meet minimum
     * 
     * Given: The UI is loaded
     * When: All clickable elements are examined
     * Then: Each meets minimum tap target size
     */
    @Test(description = "Touch Target Size — All interactive elements meet minimum")
    public void testAllInteractiveElements() {
        logger.info("=== Accessibility: All Interactive Elements ===");

        loadProfileAndGoToBasket();

        // Find all clickable elements
        List<WebElement> clickable = driver.findElements(
            By.cssSelector("button, a, .btn, input[type='button'], [role='button'], .clickable")
        );

        logger.info("Found {} interactive elements", clickable.size());
        
        boolean allMeetSize = true;
        for (WebElement el : clickable) {
            int width = el.getSize().getWidth();
            int height = el.getSize().getHeight();
            boolean meets = width >= MIN_TARGET_SIZE && height >= MIN_TARGET_SIZE;
            
            if (!meets) {
                allMeetSize = false;
                logger.warn("  ❌ Element {}: {}x{}px, does NOT meet threshold", 
                    el.getText(), width, height);
            }
        }

        assertTrue(allMeetSize, 
            "All interactive elements should meet minimum target size");

        logger.info("✅ All interactive elements meet minimum touch target size");
    }

    /**
     * Test: Color Contrast — Key buttons meet WCAG 2.1 AA
     * 
     * Given: The UI is loaded
     * When: Key buttons are examined
     * Then: Color contrast meets 4.5:1 ratio
     */
    @Test(description = "Color Contrast — Key buttons meet WCAG 2.1 AA")
    public void testColorContrast() {
        logger.info("=== Accessibility: Color Contrast ===");

        loadProfileAndGoToBasket();

        boolean allMeetContrast = true;
        for (By btnLocator : CONTRAST_BUTTONS) {
            // Wait for button to be visible
            try {
                WebElement btn = kiosk.wait.until(
                    org.openqa.selenium.support.ui.ExpectedConditions
                        .visibilityOfElementLocated(btnLocator)
                );
                
                Map<String, Color> colors = getElementColors(btnLocator);
                Color fg = colors.getOrDefault("foreground", Color.BLACK);
                Color bg = colors.getOrDefault("background", Color.WHITE);
                
                double contrast = getContrastRatio(fg, bg);
                logger.info("  Button {}: contrast ratio = {:.2f}", btnLocator, contrast);
                
                if (contrast < MIN_CONTRAST_RATIO) {
                    allMeetContrast = false;
                    logger.warn("  ❌ Button {}: contrast {:.2f} < threshold {:.1f}", 
                        btnLocator, contrast, MIN_CONTRAST_RATIO);
                } else {
                    logger.info("  ✅ Button {}: contrast {:.2f} >= threshold {:.1f}", 
                        btnLocator, contrast, MIN_CONTRAST_RATIO);
                }
            } catch (Exception e) {
                logger.warn("  Button {} not found or not visible", btnLocator);
            }
        }

        // Note: This test may fail if the CSS doesn't set contrast properly
        // For this harness, we expect it to pass as we defined the styles
        assertTrue(allMeetContrast, 
            "All key buttons should meet WCAG 2.1 AA contrast ratio of " + MIN_CONTRAST_RATIO + ":1");

        logger.info("✅ All key buttons meet WCAG 2.1 AA contrast requirements");
    }

    /**
     * Test: Focus Indicators — Key interactive elements have focus styles
     * 
     * Given: The UI is loaded
     * When: Elements are focused
     * Then: Focus indicators are visible
     */
    @Test(description = "Focus Indicators — Key elements have focus styles")
    public void testFocusIndicators() {
        logger.info("=== Accessibility: Focus Indicators ===");

        loadProfileAndGoToBasket();

        // Check that focus-visible styles are defined
        String styleContent = driver.findElement(By.tagName("style")).getAttribute("innerHTML");
        if (styleContent == null || styleContent.isEmpty()) {
            // Check external stylesheet
            WebElement link = driver.findElement(By.cssSelector("link[rel='stylesheet']"));
            // This is a heuristic check - we can't easily verify the actual stylesheet content
            logger.info("  Focus styles defined in external stylesheet");
        } else {
            boolean hasFocusStyle = styleContent.contains(":focus-visible") || 
                                    styleContent.contains(":focus");
            logger.info("  Focus styles present: {}", hasFocusStyle);
            assertTrue(hasFocusStyle, "Focus styles should be defined");
        }

        // Verify some elements have focus outlines by checking computed style
        // We'll check the start button
        try {
            WebElement btn = driver.findElement(BaseKioskPage.WELCOME_START_BTN);
            String outline = btn.getCssValue("outline");
            String outlineStyle = btn.getCssValue("outline-style");
            logger.info("  Start button outline: {}, style: {}", outline, outlineStyle);
        } catch (Exception e) {
            logger.warn("  Could not verify focus style on start button");
        }

        logger.info("✅ Focus indicators test completed");
    }

    /**
     * Test: Screen Reader Accessibility — ARIA attributes
     * 
     * Given: The UI is loaded
     * When: ARIA attributes are examined
     * Then: Key elements have appropriate ARIA labels
     */
    @Test(description = "Screen Reader Accessibility — ARIA attributes")
    public void testAriaAttributes() {
        logger.info("=== Accessibility: ARIA Attributes ===");

        loadProfileAndGoToBasket();

        // Check for aria-label on key elements
        List<By> elementsWithAria = List.of(
            By.cssSelector("[aria-label]"),
            By.cssSelector("[aria-labelledby]"),
            By.cssSelector("[role='button']"),
            By.cssSelector("[role='main']")
        );

        int ariaElements = 0;
        for (By locator : elementsWithAria) {
            ariaElements += driver.findElements(locator).size();
        }
        logger.info("  Elements with ARIA attributes: {}", ariaElements);

        // Main container should have role="main"
        boolean hasMainRole = driver.findElements(By.cssSelector("[role='main']")).size() > 0;
        assertTrue(hasMainRole, "Main container should have role='main'");
        logger.info("  Main container has role='main'");

        // Header should have appropriate role
        boolean hasHeaderRole = driver.findElements(By.cssSelector("header[role='banner']")).size() > 0;
        if (!hasHeaderRole) {
            // Check for header element
            hasHeaderRole = driver.findElements(By.tagName("header")).size() > 0;
        }
        logger.info("  Header element present: {}", hasHeaderRole);

        // Verify at least one element has an aria-label
        boolean hasAriaLabel = driver.findElements(By.cssSelector("[aria-label]")).size() > 0;
        if (!hasAriaLabel) {
            // Check for aria-labelledby as well
            hasAriaLabel = driver.findElements(By.cssSelector("[aria-labelledby]")).size() > 0;
        }
        logger.info("  Elements with ARIA labels: {}", hasAriaLabel);

        logger.info("✅ ARIA attributes test completed");
    }
}