// framework-core/src/main/java/com/toshiba/pos/testdata/TestDataConfig.java

package com.toshiba.pos.testdata;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TestDataConfig — Configuration for synthetic test data generation.
 * 
 * <p>This class holds configuration parameters for the FixtureFactory,
 * including seed, locale, and default values.
 */
public class TestDataConfig {

    private final long seed;
    private final String locale;
    private final int defaultBasketSize;
    private final Map<String, Object> overrides;

    private TestDataConfig(Builder builder) {
        this.seed = builder.seed;
        this.locale = builder.locale;
        this.defaultBasketSize = builder.defaultBasketSize;
        this.overrides = Collections.unmodifiableMap(builder.overrides);
    }

    public long getSeed() { return seed; }
    public String getLocale() { return locale; }
    public int getDefaultBasketSize() { return defaultBasketSize; }
    public Map<String, Object> getOverrides() { return overrides; }

    /**
     * Get a typed override value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOverride(String key, Class<T> type) {
        Object value = overrides.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Check if an override exists.
     */
    public boolean hasOverride(String key) {
        return overrides.containsKey(key);
    }

    @Override
    public String toString() {
        return "TestDataConfig{seed=" + seed + ", locale='" + locale + 
               "', defaultBasketSize=" + defaultBasketSize + ", overrides=" + overrides + '}';
    }

    /**
     * Builder for TestDataConfig.
     */
    public static class Builder {
        private long seed = 42L;
        private String locale = "en";
        private int defaultBasketSize = 3;
        private final Map<String, Object> overrides = new HashMap<>();

        public Builder seed(long seed) { this.seed = seed; return this; }
        public Builder locale(String locale) { this.locale = locale; return this; }
        public Builder defaultBasketSize(int size) { this.defaultBasketSize = size; return this; }
        
        public Builder override(String key, Object value) {
            this.overrides.put(key, value);
            return this;
        }

        public Builder overrides(Map<String, Object> overrides) {
            this.overrides.putAll(overrides);
            return this;
        }

        public TestDataConfig build() {
            return new TestDataConfig(this);
        }
    }
}