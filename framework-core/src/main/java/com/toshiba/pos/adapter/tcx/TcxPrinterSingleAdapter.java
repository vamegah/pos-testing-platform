// framework-core/src/main/java/com/toshiba/pos/adapter/tcx/TcxPrinterSingleAdapter.java

package com.toshiba.pos.adapter.tcx;

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

public class TcxPrinterSingleAdapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/tcx-printer-single.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        PeripheralCapabilities.Builder builder = new PeripheralCapabilities.Builder();
        builder.set("printer", true);
        builder.set("paper_out_simulation", true);
        builder.set("jam_simulation", true);
        builder.set("virtual_port", true);
        return builder.build();
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        return new UiScreenSet.Builder()
            .addScreen("welcome", "title", "subtitle", "start_button")
            .addScreen("basket", "items", "subtotal", "pay_button", "scan_button")
            .addScreen("printing", "status", "spinner", "message")
            .addScreen("paper_out", "alert", "message", "icon", "dismiss_button")
            .addScreen("receipt", "title", "details", "new_button")
            .addScreen("assist", "title", "message", "details", "dismiss_button")
            .build();
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        return new E2EFlowSteps.Builder()
            .addStep("scan_item", "api_call", "pricing", "/price/{sku}", Map.of("sku", "SKU-1001"))
            .addStep("calculate_tax", "api_call", "tax", "/tax", Map.of("region", "CA"))
            .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
            .addStep("print_receipt", "api_call", "printer", "/tcx-printer-single/print/receipt", Collections.emptyMap())
            .build();
    }
}