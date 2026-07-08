// framework-core/src/main/java/com/toshiba/pos/model/ProductProfile.java

package com.toshiba.pos.model;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * ProductProfile — metadata and capabilities for a POS product.
 * 
 * <p>This model class represents the parsed manifest data from Phase 12 profiles.
 * It is consumed by ProductAdapter implementations.
 */
public class ProductProfile {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final Map<String, Object> capabilities;
    private final Map<String, Object> services;

    private ProductProfile(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.capabilities = builder.capabilities;
        this.services = builder.services;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public Map<String, Object> getCapabilities() { return capabilities; }
    public Map<String, Object> getServices() { return services; }

    /**
     * Check if the product supports a specific capability.
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.containsKey(capability);
    }

    /**
     * Get a capability value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getCapability(String key, Class<T> type) {
        Object value = capabilities != null ? capabilities.get(key) : null;
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductProfile that = (ProductProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProductProfile{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", version='" + version + '\'' +
               '}';
    }

    /**
     * Builder for ProductProfile.
     */
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description;
        private Map<String, Object> capabilities;
        private Map<String, Object> services;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder capabilities(Map<String, Object> capabilities) { this.capabilities = capabilities; return this; }
        public Builder services(Map<String, Object> services) { this.services = services; return this; }

        public ProductProfile build() {
            return new ProductProfile(this);
        }
    }
}