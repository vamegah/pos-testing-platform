// framework-core/src/main/java/com/toshiba/pos/adapter/mxp/MxpVisionKioskAdapter.java

package com.toshiba.pos.adapter.mxp;

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

public class MxpVisionKioskAdapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/mxp-vision-kiosk.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        Map<String, Object> caps = (Map<String, Object>) manifest.getOrDefault("capabilities", Collections.emptyMap());
        Map<String, Object> peripherals = (Map<String, Object>) caps.getOrDefault("peripherals", Collections.emptyMap());
        
        PeripheralCapabilities.Builder builder = new PeripheralCapabilities.Builder();
        builder.set("scanner", true);
        builder.set("scale", true);
        builder.set("printer", true);
        builder.set("pin_pad", true);
        builder.set("camera", true);
        builder.set("nfc_reader", true);
        builder.set("biometric", true);
        
        // Override with manifest values if present
        for (Map.Entry<String, Object> entry : peripherals.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                builder.set(entry.getKey(), (Boolean) entry.getValue());
            }
        }
        
        return builder.build();
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        return new UiScreenSet.Builder()
            .addScreen("welcome", "title", "subtitle", "start_button")
            .addScreen("basket", "items", "subtotal", "bulk_scan_button", "pay_button", "assist_button")
            .addScreen("scan_results", "results_list", "weight_indicator", "recheck_button")
            .addScreen("payment", "total", "methods", "authorize_button")
            .addScreen("receipt", "title", "details", "new_button")
            .addScreen("assist", "title", "message", "details", "dismiss_button")
            .build();
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        return new E2EFlowSteps.Builder()
            .addStep("bulk_scan", "api_call", "vision", "/mxp-vision/scan", Map.of("image_hints", "milk,bread,apple"))
            .addStep("verify_weight", "api_call", "vision", "/mxp-vision/weight/verify", Map.of("sku", "SKU-1001"))
            .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
            .addStep("generate_receipt", "api_call", "payment", "/payment/transaction/{id}", Collections.emptyMap())
            .build();
    }
}