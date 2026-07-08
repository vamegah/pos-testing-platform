// framework-core/src/main/java/com/toshiba/pos/adapter/AbstractProductAdapter.java
// Abstract base class to reduce boilerplate in adapter implementations

package com.toshiba.pos.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.ProductProfile;
import com.toshiba.pos.model.UiScreenSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.*;

/**
 * Abstract base implementation of ProductAdapter.
 * 
 * Provides common functionality for loading JSON manifests and validating
 * adapter configurations. Subclasses should implement the specific
 * product-specific mappings.
 */
public abstract class AbstractProductAdapter implements ProductAdapter {

    protected static final Logger logger = LogManager.getLogger(AbstractProductAdapter.class);
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected ProductProfile profile;
    protected PeripheralCapabilities peripherals;
    protected UiScreenSet uiScreenSet;
    protected E2EFlowSteps flowSteps;
    protected String productId;
    protected String displayName;
    protected boolean loaded = false;

    /**
     * Get the path to the manifest JSON file for this product.
     */
    protected abstract String getManifestPath();

    /**
     * Build the PeripheralCapabilities from the manifest.
     */
    protected abstract PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest);

    /**
     * Build the UiScreenSet from the manifest.
     */
    protected abstract UiScreenSet buildUiScreenSet(Map<String, Object> manifest);

    /**
     * Build the E2EFlowSteps from the manifest.
     */
    protected abstract E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest);

    @Override
    public void load() {
        try (InputStream input = getClass().getResourceAsStream(getManifestPath())) {
            if (input == null) {
                throw new IllegalStateException("Manifest not found: " + getManifestPath());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = MAPPER.readValue(input, Map.class);
            
            // Build product profile
            this.productId = (String) manifest.getOrDefault("name", getClass().getSimpleName())
                .toString().toLowerCase().replace(" ", "-");
            this.displayName = (String) manifest.getOrDefault("name", "Unknown Product");
            
            this.profile = new ProductProfile.Builder()
                .id(productId)
                .name(displayName)
                .version((String) manifest.getOrDefault("version", "1.0.0"))
                .description((String) manifest.getOrDefault("description", ""))
                .capabilities((Map<String, Object>) manifest.getOrDefault("capabilities", Collections.emptyMap()))
                .services((Map<String, Object>) manifest.getOrDefault("services", Collections.emptyMap()))
                .build();

            // Build peripheral capabilities
            this.peripherals = buildPeripheralCapabilities(manifest);

            // Build UI screen set
            this.uiScreenSet = buildUiScreenSet(manifest);

            // Build E2E flow steps
            this.flowSteps = buildE2EFlowSteps(manifest);

            this.loaded = true;
            logger.info("Loaded adapter for: {}", displayName);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load manifest for adapter: " + getClass().getSimpleName(), e);
        }
    }

    @Override
    public void validate() {
        if (!loaded) {
            throw new IllegalStateException("Adapter not loaded. Call load() first.");
        }
        if (profile == null) {
            throw new IllegalStateException("Profile is null");
        }
        if (peripherals == null) {
            throw new IllegalStateException("PeripheralCapabilities is null");
        }
        if (uiScreenSet == null) {
            throw new IllegalStateException("UiScreenSet is null");
        }
        if (flowSteps == null) {
            throw new IllegalStateException("E2EFlowSteps is null");
        }
        if (productId == null || productId.isEmpty()) {
            throw new IllegalStateException("Product ID is null or empty");
        }
        if (flowSteps.getSteps().isEmpty()) {
            throw new IllegalStateException("E2E flow steps are empty");
        }
        logger.info("Validation passed for: {}", displayName);
    }

    @Override
    public ProductProfile getProfile() {
        return profile;
    }

    @Override
    public PeripheralCapabilities getPeripheralCapabilities() {
        return peripherals;
    }

    @Override
    public UiScreenSet getUiScreenSet() {
        return uiScreenSet;
    }

    @Override
    public E2EFlowSteps getE2EFlowSteps() {
        return flowSteps;
    }

    @Override
    public String getProductId() {
        return productId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}