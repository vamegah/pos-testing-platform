// framework-core/src/main/java/com/toshiba/pos/adapter/ProductAdapter.java

package com.toshiba.pos.adapter;

import com.toshiba.pos.model.ProductProfile;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;
import com.toshiba.pos.model.E2EFlowSteps;

/**
 * ProductAdapter Interface.
 * 
 * Every product profile (Phase 12) must implement this interface.
 * The adapter serves as the bridge between the product's capability manifest
 * and the generic test engine.
 * 
 * <p>Implementations should load their configuration from the profile's
 * manifest (JSON/YAML) and provide access to:
 * <ul>
 *   <li>Product profile metadata (name, version, capabilities)</li>
 *   <li>Peripheral capabilities (scanner, scale, printer, PIN pad, etc.)</li>
 *   <li>UI screen set definitions for the harness</li>
 *   <li>E2E flow steps for transaction testing</li>
 * </ul>
 * 
 * <p>This interface is designed to be implemented by one adapter per product.
 * The adapter registry loads all available adapters for the test engine.
 * 
 * @see ProductProfile
 * @see PeripheralCapabilities
 * @see UiScreenSet
 * @see E2EFlowSteps
 */
public interface ProductAdapter {

    /**
     * Get the product profile metadata.
     * 
     * @return ProductProfile containing name, version, and capabilities
     */
    ProductProfile getProfile();

    /**
     * Get the peripheral capabilities for this product.
     * 
     * @return PeripheralCapabilities describing which peripherals are available
     */
    PeripheralCapabilities getPeripheralCapabilities();

    /**
     * Get the UI screen set definitions for the harness.
     * 
     * @return UiScreenSet describing the screens and their widgets
     */
    UiScreenSet getUiScreenSet();

    /**
     * Get the E2E flow steps for transaction testing.
     * 
     * @return E2EFlowSteps describing the transaction flow
     */
    E2EFlowSteps getE2EFlowSteps();

    /**
     * Get the product identifier (unique key).
     * 
     * @return String product ID (e.g., "elera", "mxp-vision-kiosk")
     */
    String getProductId();

    /**
     * Get the display name for the product.
     * 
     * @return String display name (e.g., "ELERA® Platform")
     */
    String getDisplayName();

    /**
     * Validate that the adapter configuration is complete and valid.
     * 
     * <p>This method should check:
     * <ul>
     *   <li>Required fields are present</li>
     *   <li>Referenced peripherals exist</li>
     *   <li>UI screen definitions are consistent</li>
     *   <li>Flow steps are valid</li>
     * </ul>
     * 
     * @throws IllegalStateException if configuration is invalid
     */
    void validate();

    /**
     * Load the adapter configuration from its source.
     * 
     * <p>This method is called during initialization. It should load
     * the manifest and populate internal state.
     */
    void load();
}