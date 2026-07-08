// framework-core/src/main/java/com/toshiba/pos/adapter/scs/SelfCheckoutSystem7Adapter.java

package com.toshiba.pos.adapter.scs;

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

public class SelfCheckoutSystem7Adapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/self-checkout-system-7.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        Map<String, Object> caps = (Map<String, Object>) manifest.getOrDefault("capabilities", Collections.emptyMap());
        Map<String, Object> peripherals = (Map<String, Object>) caps.getOrDefault("peripherals", Collections.emptyMap());
        
        PeripheralCapabilities.Builder builder = new PeripheralCapabilities.Builder();
        builder.set("scanner", (Boolean) peripherals.getOrDefault("scanner", true));
        builder.set("scale", (Boolean) peripherals.getOrDefault("scale", true));
        builder.set("bagging", true);
        builder.set("printer", (Boolean) peripherals.getOrDefault("printer", true));
        builder.set("pin_pad", (Boolean) peripherals.getOrDefault("pin_pad", true));
        builder.set("cash_recycling", true);
        builder.set("cashless", true);
        builder.set("kiosk", true);
        
        return builder.build();
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        return new UiScreenSet.Builder()
            .addScreen("welcome", "title", "subtitle", "start_button")
            .addScreen("basket", "items", "subtotal", "bagging_indicator", "pay_button", "scan_button", "assist_button")
            .addScreen("payment", "total", "methods", "cash_method", "card_method", "authorize_button")
            .addScreen("receipt", "title", "details", "new_button")
            .addScreen("assist", "title", "message", "details", "dismiss_button")
            .build();
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        return new E2EFlowSteps.Builder()
            .addStep("start_transaction", "api_call", "scs", "/self-checkout/transaction/start", Map.of("session_id", "e2e-001"))
            .addStep("scan_item", "api_call", "pricing", "/price/{sku}", Map.of("sku", "SKU-1001"))
            .addStep("verify_bagging", "api_call", "scs", "/self-checkout/bagging/verify", Map.of("weight", 3.78))
            .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
            .addStep("complete_transaction", "api_call", "scs", "/self-checkout/transaction/complete", Collections.emptyMap())
            .build();
    }
}