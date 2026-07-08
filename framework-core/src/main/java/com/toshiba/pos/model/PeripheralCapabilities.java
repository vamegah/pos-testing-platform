// framework-core/src/main/java/com/toshiba/pos/model/PeripheralCapabilities.java

package com.toshiba.pos.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * PeripheralCapabilities — describes which peripherals are available for a product.
 * 
 * <p>This model is used by ProductAdapter to expose peripheral availability
 * to the test engine and UI harness.
 */
public class PeripheralCapabilities {

    private final Map<String, Boolean> peripherals;

    private PeripheralCapabilities(Map<String, Boolean> peripherals) {
        this.peripherals = new HashMap<>(peripherals);
    }

    public boolean isAvailable(String peripheral) {
        return peripherals.getOrDefault(peripheral, false);
    }

    public Map<String, Boolean> getAll() {
        return new HashMap<>(peripherals);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeripheralCapabilities that = (PeripheralCapabilities) o;
        return Objects.equals(peripherals, that.peripherals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peripherals);
    }

    @Override
    public String toString() {
        return "PeripheralCapabilities{" + peripherals + '}';
    }

    /**
     * Builder for PeripheralCapabilities.
     */
    public static class Builder {
        private final Map<String, Boolean> peripherals = new HashMap<>();

        public Builder enable(String peripheral) {
            peripherals.put(peripheral, true);
            return this;
        }

        public Builder disable(String peripheral) {
            peripherals.put(peripheral, false);
            return this;
        }

        public Builder set(String peripheral, boolean enabled) {
            peripherals.put(peripheral, enabled);
            return this;
        }

        public PeripheralCapabilities build() {
            return new PeripheralCapabilities(peripherals);
        }
    }
}