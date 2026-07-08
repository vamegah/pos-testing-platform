// test-automation/src/test/java/com/toshiba/pos/ui/BaseKioskPage.java

package com.toshiba.pos.ui;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.interactions.Actions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;

/**
 * Base Kiosk UI Page Object.
 * 
 * Provides common functionality for all kiosk UI tests:
 *   - WebDriver management
 *   - "Tap" helper (touch-first click)
 *   - Screen-state assertions
 *   - Waiting for elements
 *   - Profile loading
 */
public class BaseKioskPage {

    protected static final Logger logger = LogManager.getLogger(BaseKioskPage.class);

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected final WebDriverWait shortWait;
    protected final Actions actions;

    // Default timeouts
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration SHORT_TIMEOUT = Duration.ofSeconds(3);

    // Base URL (configurable via environment)
    protected static final String BASE_URL = System.getProperty("KIOSK_UI_URL", "http://localhost:8080");

    // ============================================================
    // Locators (shared across all kiosk screens)
    // ============================================================

    // Profile selector
    protected static final By PROFILE_SELECT = By.id("profile-select");
    protected static final By LOAD_PROFILE_BTN = By.id("load-profile-btn");
    protected static final By RESET_BTN = By.id("reset-btn");
    protected static final By PROFILE_NAME = By.id("profile-name");

    // Screen containers
    protected static final By SCREEN_WELCOME = By.id("screen-welcome");
    protected static final By SCREEN_BASKET = By.id("screen-basket");
    protected static final By SCREEN_PAYMENT = By.id("screen-payment");
    protected static final By SCREEN_RECEIPT = By.id("screen-receipt");
    protected static final By SCREEN_ASSIST = By.id("screen-assist");

    // Welcome screen
    protected static final By WELCOME_TITLE = By.id("welcome-title");
    protected static final By WELCOME_START_BTN = By.id("welcome-start-btn");

    // Basket screen
    protected static final By BASKET_ITEMS = By.id("basket-items");
    protected static final By BASKET_ITEM_COUNT = By.id("item-count");
    protected static final By BASKET_SUBTOTAL = By.id("basket-subtotal");
    protected static final By BASKET_PAY_BTN = By.id("basket-pay-btn");
    protected static final By BASKET_SCAN_BTN = By.id("basket-scan-btn");
    protected static final By BASKET_ASSIST_BTN = By.id("basket-assist-btn");
    protected static final By BASKET_EMPTY_MESSAGE = By.cssSelector(".empty-message");
    protected static final By BASKET_ITEMS_LIST = By.cssSelector(".basket-item");
    protected static final By PERIPHERAL_INDICATORS = By.id("peripheral-indicators");

    // Payment screen
    protected static final By PAYMENT_TOTAL = By.id("payment-total");
    protected static final By PAYMENT_METHODS = By.id("payment-methods");
    protected static final By PAYMENT_METHOD_BTN = By.cssSelector(".payment-method");
    protected static final By PAYMENT_AUTHORIZE_BTN = By.id("payment-authorize-btn");
    protected static final By PAYMENT_CANCEL_BTN = By.id("payment-cancel-btn");
    protected static final By PAYMENT_STATUS = By.id("payment-status");

    // Receipt screen
    protected static final By RECEIPT_TITLE = By.id("receipt-title");
    protected static final By RECEIPT_DETAILS = By.id("receipt-details");
    protected static final By RECEIPT_NEW_BTN = By.id("receipt-new-btn");

    // Assist overlay
    protected static final By ASSIST_TITLE = By.id("assist-title");
    protected static final By ASSIST_MESSAGE = By.id("assist-message");
    protected static final By ASSIST_DETAILS = By.id("assist-details");
    protected static final By ASSIST_DISMISS_BTN = By.id("assist-dismiss-btn");

    // Footer
    protected static final By FOOTER_MODE = By.id("footer-mode");
    protected static final By FOOTER_PERIPHERALS = By.id("footer-peripherals");
    protected static final By FOOTER_SCREEN = By.id("footer-screen");

    // ============================================================
    // Constructor
    // ============================================================

    public BaseKioskPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        this.shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
        this.actions = new Actions(driver);
    }

    // ============================================================
    // Navigation
    // ============================================================

    /**
     * Navigate to the kiosk UI harness.
     */
    public BaseKioskPage navigateTo() {
        logger.info("Navigating to Kiosk UI: {}", BASE_URL);
        driver.get(BASE_URL);
        waitForPageLoad();
        return this;
    }

    /**
     * Wait for the page to be fully loaded.
     */
    public void waitForPageLoad() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(PROFILE_SELECT));
        logger.info("Kiosk UI loaded");
    }

    // ============================================================
    // Profile Management
    // ============================================================

    /**
     * Load a product profile by its display name.
     */
    public BaseKioskPage loadProfile(String profileDisplayName) {
        logger.info("Loading profile: {}", profileDisplayName);

        // Select the profile from dropdown
        WebElement select = wait.until(ExpectedConditions.elementToBeClickable(PROFILE_SELECT));
        select.sendKeys(profileDisplayName);

        // Click the Load button
        tap(LOAD_PROFILE_BTN);

        // Wait for profile name to update
        wait.until(ExpectedConditions.textToBePresentInElementLocated(PROFILE_NAME, profileDisplayName));
        logger.info("Profile loaded: {}", profileDisplayName);

        return this;
    }

    /**
     * Load the first available profile from the dropdown.
     */
    public BaseKioskPage loadFirstProfile() {
        WebElement select = wait.until(ExpectedConditions.elementToBeClickable(PROFILE_SELECT));
        // Get first option (skip the placeholder)
        List<WebElement> options = select.findElements(By.tagName("option"));
        for (WebElement opt : options) {
            String value = opt.getAttribute("value");
            if (value != null && !value.isEmpty()) {
                return loadProfile(opt.getText());
            }
        }
        throw new RuntimeException("No profile available to load");
    }

    /**
     * Reset the UI to the welcome screen.
     */
    public BaseKioskPage reset() {
        logger.info("Resetting UI");
        tap(RESET_BTN);
        waitForScreen("welcome");
        return this;
    }

    // ============================================================
    // Screen State Assertions
    // ============================================================

    /**
     * Assert that a specific screen is active.
     */
    public BaseKioskPage assertScreen(String screenName) {
        By screenLocator = getScreenLocator(screenName);
        wait.until(ExpectedConditions.visibilityOfElementLocated(screenLocator));
        logger.info("Screen verified: {}", screenName);
        return this;
    }

    /**
     * Assert that the screen title matches expected text.
     */
    public BaseKioskPage assertScreenTitle(String expectedTitle) {
        By titleLocator = getTitleLocator();
        String actual = wait.until(ExpectedConditions.visibilityOfElementLocated(titleLocator)).getText();
        assert actual.contains(expectedTitle) : "Expected title to contain '" + expectedTitle + "', got '" + actual + "'";
        logger.info("Screen title verified: {}", expectedTitle);
        return this;
    }

    /**
     * Assert that the footer shows the expected screen name.
     */
    public BaseKioskPage assertFooterScreen(String expectedScreen) {
        String actual = wait.until(ExpectedConditions.visibilityOfElementLocated(FOOTER_SCREEN)).getText();
        assert actual.contains(expectedScreen) : "Expected footer to contain '" + expectedScreen + "', got '" + actual + "'";
        logger.info("Footer screen verified: {}", expectedScreen);
        return this;
    }

    /**
     * Assert that an element is visible.
     */
    public BaseKioskPage assertElementVisible(By locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        logger.info("Element visible: {}", locator);
        return this;
    }

    /**
     * Assert that an element is NOT visible.
     */
    public BaseKioskPage assertElementNotVisible(By locator) {
        try {
            WebElement el = driver.findElement(locator);
            assert !el.isDisplayed() : "Element should not be visible: " + locator;
        } catch (NoSuchElementException e) {
            // Element not present — that's fine
        }
        logger.info("Element not visible: {}", locator);
        return this;
    }

    /**
     * Assert that an element's text matches expected.
     */
    public BaseKioskPage assertElementText(By locator, String expectedText) {
        String actual = wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).getText();
        assert actual.contains(expectedText) : "Expected text to contain '" + expectedText + "', got '" + actual + "'";
        logger.info("Element text verified: {} -> {}", locator, expectedText);
        return this;
    }

    /**
     * Get the current active screen name from the footer.
     */
    public String getCurrentScreen() {
        String text = wait.until(ExpectedConditions.visibilityOfElementLocated(FOOTER_SCREEN)).getText();
        // Extract screen name from "Screen: welcome"
        String[] parts = text.split(":");
        return parts.length > 1 ? parts[1].trim() : text;
    }

    // ============================================================
    // "Tap" Helper (Touch-First Click)
    // ============================================================

    /**
     * Tap (click) an element with touch-first behavior.
     * Uses Actions for more reliable touch simulation.
     */
    public BaseKioskPage tap(WebElement element) {
        logger.debug("Tapping element: {}", element);
        wait.until(ExpectedConditions.elementToBeClickable(element));
        // Use Actions to simulate a tap (works better for touch-first UIs)
        actions.moveToElement(element).click().perform();
        return this;
    }

    /**
     * Tap (click) an element by locator.
     */
    public BaseKioskPage tap(By locator) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        return tap(element);
    }

    /**
     * Tap (click) an element by its ID.
     */
    public BaseKioskPage tapById(String id) {
        return tap(By.id(id));
    }

    // ============================================================
    // Waiting Helpers
    // ============================================================

    /**
     * Wait for a specific screen to become active.
     */
    public BaseKioskPage waitForScreen(String screenName) {
        By screenLocator = getScreenLocator(screenName);
        wait.until(ExpectedConditions.visibilityOfElementLocated(screenLocator));
        // Also verify footer
        wait.until(ExpectedConditions.textToBePresentInElementLocated(FOOTER_SCREEN, screenName));
        logger.info("Screen active: {}", screenName);
        return this;
    }

    /**
     * Wait for text to appear in an element.
     */
    public BaseKioskPage waitForText(By locator, String expectedText) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, expectedText));
        logger.info("Text present: {} -> {}", locator, expectedText);
        return this;
    }

    /**
     * Wait for an element to disappear.
     */
    public BaseKioskPage waitForElementToDisappear(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
        logger.info("Element disappeared: {}", locator);
        return this;
    }

    // ============================================================
    // Helper: Get Screen Locator
    // ============================================================

    private By getScreenLocator(String screenName) {
        switch (screenName) {
            case "welcome": return SCREEN_WELCOME;
            case "basket": return SCREEN_BASKET;
            case "payment": return SCREEN_PAYMENT;
            case "receipt": return SCREEN_RECEIPT;
            case "assist": return SCREEN_ASSIST;
            default: throw new IllegalArgumentException("Unknown screen: " + screenName);
        }
    }

    private By getTitleLocator() {
        // Determine which screen is active and return its title locator
        if (driver.findElements(SCREEN_WELCOME).stream().anyMatch(WebElement::isDisplayed)) {
            return WELCOME_TITLE;
        } else if (driver.findElements(SCREEN_BASKET).stream().anyMatch(WebElement::isDisplayed)) {
            return By.cssSelector(".basket-header h2");
        } else if (driver.findElements(SCREEN_PAYMENT).stream().anyMatch(WebElement::isDisplayed)) {
            return By.cssSelector("#screen-payment h2");
        } else if (driver.findElements(SCREEN_RECEIPT).stream().anyMatch(WebElement::isDisplayed)) {
            return RECEIPT_TITLE;
        } else if (driver.findElements(SCREEN_ASSIST).stream().anyMatch(WebElement::isDisplayed)) {
            return ASSIST_TITLE;
        }
        return WELCOME_TITLE;
    }

    // ============================================================
    // Utility: Get Element Count
    // ============================================================

    /**
     * Get the number of items in the basket.
     */
    public int getBasketItemCount() {
        List<WebElement> items = driver.findElements(BASKET_ITEMS_LIST);
        return items.size();
    }

    /**
     * Get the basket subtotal text.
     */
    public String getBasketSubtotal() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(BASKET_SUBTOTAL)).getText();
    }

    /**
     * Check if a peripheral indicator is active.
     */
    public boolean isPeripheralActive(String peripheralName) {
        List<WebElement> indicators = driver.findElements(By.cssSelector(".peripheral-indicator"));
        for (WebElement el : indicators) {
            if (el.getText().contains(peripheralName)) {
                return el.getAttribute("class").contains("active");
            }
        }
        return false;
    }
}