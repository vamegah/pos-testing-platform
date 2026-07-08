// framework-core/src/main/java/com/toshiba/pos/adapter/elera/EleraAdapter.java

package com.toshiba.pos.adapter.elera;

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

/**
 * Adapter for ELERA® platform profile.
 * 
 * Reads the ELERA manifest and provides capability access.
 */
public class EleraAdapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/elera.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        Map<String, Object> caps = (Map<String, Object>) manifest.getOrDefault("capabilities", Collections.emptyMap());
        Map<String, Object> peripherals = (Map<String, Object>) caps.getOrDefault("peripherals", Collections.emptyMap());
        
        PeripheralCapabilities.Builder builder = new PeripheralCapabilities.Builder();
        builder.set("scanner", (Boolean) peripherals.getOrDefault("scanner", false));
        builder.set("scale", (Boolean) peripherals.getOrDefault("scale", false));
        builder.set("printer", (Boolean) peripherals.getOrDefault("printer", false));
        builder.set("pin_pad", (Boolean) peripherals.getOrDefault("pin_pad", false));
        
        return builder.build();
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        return new UiScreenSet.Builder()
            .addScreen("welcome", "title", "subtitle", "start_button")
            .addScreen("basket", "items", "subtotal", "pay_button", "scan_button", "assist_button")
            .addScreen("payment", "total", "methods", "authorize_button", "cancel_button")
            .addScreen("receipt", "title", "details", "new_button")
            .addScreen("assist", "title", "message", "details", "dismiss_button")
            .build();
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        return new E2EFlowSteps.Builder()
            .addStep("scan_item", "api_call", "pricing", "/price/{sku}", Map.of("sku", "SKU-1001"))
            .addStep("apply_promotions", "api_call", "promotions", "/promotions/sku/{sku}", Map.of("sku", "SKU-1001"))
            .addStep("calculate_tax", "api_call", "tax", "/tax", Map.of("region", "CA"))
            .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
            .addStep("generate_receipt", "api_call", "payment", "/payment/transaction/{id}", Collections.emptyMap())
            .build();
    }
}