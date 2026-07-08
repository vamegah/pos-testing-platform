// framework-core/src/main/java/com/toshiba/pos/adapter/tcx/TcxDisplayAdapter.java

package com.toshiba.pos.adapter.tcx;

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

public class TcxDisplayAdapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/tcx-display.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        PeripheralCapabilities.Builder builder = new PeripheralCapabilities.Builder();
        builder.set("display_10_1", true);
        builder.set("display_12_1", true);
        builder.set("display_15_6", true);
        builder.set("display_21_5", true);
        builder.set("landscape", true);
        builder.set("portrait", true);
        return builder.build();
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        return new UiScreenSet.Builder()
            .addScreen("welcome", "title", "subtitle", "start_button")
            .addScreen("display_config", "size_selector", "orientation_selector", "resolution_display", "validate_button")
            .addScreen("basket", "items", "subtotal", "pay_button")
            .addScreen("payment", "total", "methods", "authorize_button")
            .addScreen("receipt", "title", "details", "new_button")
            .build();
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        return new E2EFlowSteps.Builder()
            .addStep("validate_display", "api_call", "display", "/tcx-display/render/validate", 
                Map.of("size", 15.6, "orientation", "landscape", "resolution", Map.of("width", 1920, "height", 1080)))
            .addStep("scan_item", "api_call", "pricing", "/price/{sku}", Map.of("sku", "SKU-1001"))
            .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
            .build();
    }
}