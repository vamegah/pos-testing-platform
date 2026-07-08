// framework-core/src/main/java/com/toshiba/pos/registry/AdapterRegistry.java

package com.toshiba.pos.registry;

import com.toshiba.pos.adapter.ProductAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for ProductAdapter implementations.
 * 
 * <p>This registry discovers and manages all available product adapters.
 * Adapters can be registered programmatically or discovered via service loader.
 */
public class AdapterRegistry {

    private static final Logger logger = LogManager.getLogger(AdapterRegistry.class);
    private static AdapterRegistry instance;

    private final Map<String, ProductAdapter> adapters = new ConcurrentHashMap<>();
    private boolean initialized = false;

    private AdapterRegistry() {}

    public static synchronized AdapterRegistry getInstance() {
        if (instance == null) {
            instance = new AdapterRegistry();
        }
        return instance;
    }

    /**
     * Register a product adapter.
     */
    public AdapterRegistry register(ProductAdapter adapter) {
        try {
            adapter.load();
            adapter.validate();
            adapters.put(adapter.getProductId(), adapter);
            logger.info("Registered adapter: {} ({})", adapter.getDisplayName(), adapter.getProductId());
        } catch (Exception e) {
            logger.error("Failed to register adapter: {}", adapter.getClass().getSimpleName(), e);
        }
        return this;
    }

    /**
     * Register multiple adapters.
     */
    public AdapterRegistry registerAll(ProductAdapter... adapters) {
        for (ProductAdapter adapter : adapters) {
            register(adapter);
        }
        return this;
    }

    /**
     * Get an adapter by product ID.
     */
    public Optional<ProductAdapter> getAdapter(String productId) {
        return Optional.ofNullable(adapters.get(productId));
    }

    /**
     * Get all registered adapters.
     */
    public Collection<ProductAdapter> getAllAdapters() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    /**
     * Get all product IDs.
     */
    public Set<String> getProductIds() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    /**
     * Check if an adapter is registered for a product.
     */
    public boolean hasAdapter(String productId) {
        return adapters.containsKey(productId);
    }

    /**
     * Initialize the registry with default adapters.
     * 
     * <p>This method discovers and registers all available adapters.
     * In a production implementation, this would use service loader.
     */
    public synchronized AdapterRegistry initialize() {
        if (initialized) {
            return this;
        }

        logger.info("Initializing AdapterRegistry...");
        
        // Register adapters manually (in production, use service loader)
        // These imports would be present in a real implementation
        // For now, we list them as strings to avoid compilation issues
        String[] adapterClassNames = {
            "com.toshiba.pos.adapter.elera.EleraAdapter",
            "com.toshiba.pos.adapter.mxp.MxpVisionKioskAdapter",
            "com.toshiba.pos.adapter.mxp.MxpSmartHybridAdapter",
            "com.toshiba.pos.adapter.mxp.MxpSmartWallAdapter",
            "com.toshiba.pos.adapter.mxp.MxpSmartWingAdapter",
            "com.toshiba.pos.adapter.scs.SelfCheckoutSystem7Adapter",
            "com.toshiba.pos.adapter.tcx.TcxDisplayAdapter",
            "com.toshiba.pos.adapter.tcx.TcxPrinterSingleAdapter",
            "com.toshiba.pos.adapter.tcx.TcxPrinterDualAdapter"
        };

        for (String className : adapterClassNames) {
            try {
                @SuppressWarnings("unchecked")
                Class<ProductAdapter> clazz = (Class<ProductAdapter>) Class.forName(className);
                ProductAdapter adapter = clazz.getDeclaredConstructor().newInstance();
                register(adapter);
            } catch (Exception e) {
                logger.warn("Failed to load adapter: {}", className, e);
            }
        }

        initialized = true;
        logger.info("AdapterRegistry initialized with {} adapters", adapters.size());
        return this;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Clear all registered adapters.
     */
    public void clear() {
        adapters.clear();
        initialized = false;
        logger.info("AdapterRegistry cleared");
    }
}