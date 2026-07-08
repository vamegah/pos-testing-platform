#!/bin/bash
# scripts/scaffold_new_product.sh
# New Product Scaffold Generator
#
# Creates a manifest template, adapter stub, and test-data fixture stub
# for a new product in the POS Test Framework.
#
# Usage:
#   ./scripts/scaffold_new_product.sh <product-name>
#
# Examples:
#   ./scripts/scaffold_new_product.sh my-new-product
#   ./scripts/scaffold_new_product.sh pos-9000
#
# Output:
#   - simulators/product-profiles/<product-name>/manifest.json
#   - framework-core/src/main/java/com/toshiba/pos/adapter/<product-name>/<ProductName>Adapter.java
#   - product-tests/src/test/java/com/toshiba/pos/tests/<ProductName>Test.java
#
# The generated stub fails its own placeholder test with a clear TODO,
# not a silent no-op.

set -euo pipefail

# ============================================================
# Configuration
# ============================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Template directories
TEMPLATES_DIR="${SCRIPT_DIR}/templates"
MANIFEST_TEMPLATE="${TEMPLATES_DIR}/manifest_template.json"
ADAPTER_TEMPLATE="${TEMPLATES_DIR}/adapter_template.java"
TEST_TEMPLATE="${TEMPLATES_DIR}/test_fixture_template.java"

# Output directories
PROFILES_DIR="${PROJECT_ROOT}/simulators/product-profiles"
ADAPTERS_DIR="${PROJECT_ROOT}/framework-core/src/main/java/com/toshiba/pos/adapter"
TESTS_DIR="${PROJECT_ROOT}/product-tests/src/test/java/com/toshiba/pos/tests"

# ============================================================
# Functions
# ============================================================

print_usage() {
    cat << EOF
Usage: $0 <product-name>

Creates a new product scaffold with manifest, adapter, and test fixture.

Product name rules:
  - Lowercase letters, numbers, and hyphens only
  - Must be at least 3 characters
  - Must not contain spaces or special characters

Examples:
  $0 my-new-product
  $0 pos-9000
  $0 mxp-smart-ultra

Output files:
  - simulators/product-profiles/<product-name>/manifest.json
  - framework-core/.../adapter/<product-name>/<ProductName>Adapter.java
  - product-tests/.../tests/<ProductName>Test.java

The generated stub fails its own placeholder test with a clear TODO.
EOF
}

validate_product_name() {
    local name="$1"
    if [[ -z "$name" ]]; then
        echo "❌ Error: Product name is required"
        print_usage
        exit 1
    fi
    
    if [[ ! "$name" =~ ^[a-z][a-z0-9-]*$ ]]; then
        echo "❌ Error: Product name must be lowercase letters, numbers, and hyphens only"
        echo "   Must start with a letter: $name"
        print_usage
        exit 1
    fi
    
    if [[ ${#name} -lt 3 ]]; then
        echo "❌ Error: Product name must be at least 3 characters"
        print_usage
        exit 1
    fi
}

to_pascal_case() {
    echo "$1" | sed 's/-/ /g' | awk '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) tolower(substr($i,2))}1' | sed 's/ //g'
}

to_camel_case() {
    local pascal=$(to_pascal_case "$1")
    echo "$pascal" | sed 's/^./\L&/'
}

ensure_directory() {
    local dir="$1"
    if [[ ! -d "$dir" ]]; then
        mkdir -p "$dir"
        echo "  Created directory: $dir"
    fi
}

copy_template() {
    local template="$1"
    local destination="$2"
    local product_name="$3"
    local product_pascal="$4"
    local product_camel="$5"
    
    if [[ ! -f "$template" ]]; then
        echo "⚠️  Warning: Template not found: $template"
        echo "   Creating from inline template..."
        return 1
    fi
    
    # Replace placeholders and write to destination
    sed -e "s/{{PRODUCT_NAME}}/$product_name/g" \
        -e "s/{{PRODUCT_NAME_PASCAL}}/$product_pascal/g" \
        -e "s/{{PRODUCT_NAME_CAMEL}}/$product_camel/g" \
        "$template" > "$destination"
    
    echo "  Created: $destination"
    return 0
}

# ============================================================
# Main
# ============================================================

main() {
    local product_name="$1"
    
    echo "========================================"
    echo "  New Product Scaffold Generator"
    echo "========================================"
    echo "  Product: $product_name"
    echo "========================================"
    
    # Validate product name
    validate_product_name "$product_name"
    
    # Convert name formats
    local product_pascal=$(to_pascal_case "$product_name")
    local product_camel=$(to_camel_case "$product_name")
    
    echo ""
    echo "📁 Creating directories..."
    
    # Create manifest directory
    local manifest_dir="${PROFILES_DIR}/${product_name}"
    ensure_directory "$manifest_dir"
    
    # Create adapter directory
    local adapter_dir="${ADAPTERS_DIR}/${product_name}"
    ensure_directory "$adapter_dir"
    
    # ============================================================
    # Generate Manifest
    # ============================================================
    echo ""
    echo "📄 Generating manifest.json..."
    
    cat > "${manifest_dir}/manifest.json" << EOF
{
  "name": "${product_pascal}",
  "version": "0.1.0",
  "description": "${product_pascal} product profile — TODO: add description",
  "capabilities": {
    "display": {
      "type": "touchscreen",
      "size": 15.6,
      "orientation": "landscape"
    },
    "payment": {
      "methods": ["card", "mobile_wallet"],
      "mock_sentinels": {
        "approved": "4111111111111111",
        "declined": "4111111111110000"
      }
    },
    "peripherals": {
      "scanner": true,
      "printer": true,
      "scale": false,
      "pin_pad": false
    },
    "hooks": {
      "custom_hook": {
        "enabled": false,
        "description": "TODO: add custom hook description",
        "endpoint": "/${product_name}/custom"
      }
    }
  },
  "services": {
    "pricing": {
      "url": "http://pricing-service:8081"
    },
    "promotions": {
      "url": "http://promotions-service:8082"
    },
    "tax": {
      "url": "http://tax-service:8083"
    },
    "payment": {
      "url": "http://payment-gateway:8084"
    }
  }
}
EOF
    echo "  ✅ Created: ${manifest_dir}/manifest.json"
    
    # ============================================================
    # Generate Adapter
    # ============================================================
    echo ""
    echo "📄 Generating adapter stub..."
    
    cat > "${adapter_dir}/${product_pascal}Adapter.java" << EOF
package com.toshiba.pos.adapter.${product_name};

import com.toshiba.pos.adapter.AbstractProductAdapter;
import com.toshiba.pos.model.E2EFlowSteps;
import com.toshiba.pos.model.PeripheralCapabilities;
import com.toshiba.pos.model.UiScreenSet;

import java.util.*;

/**
 * Adapter for ${product_pascal} product profile.
 * 
 * <p>TODO: Implement this adapter for the ${product_pascal} product.
 * 
 * <p>Required methods to implement:
 * <ul>
 *   <li>{@link #buildPeripheralCapabilities(Map)}</li>
 *   <li>{@link #buildUiScreenSet(Map)}</li>
 *   <li>{@link #buildE2EFlowSteps(Map)}</li>
 * </ul>
 */
public class ${product_pascal}Adapter extends AbstractProductAdapter {

    private static final String MANIFEST_PATH = "/profiles/${product_name}/manifest.json";

    @Override
    protected String getManifestPath() {
        return MANIFEST_PATH;
    }

    @Override
    protected PeripheralCapabilities buildPeripheralCapabilities(Map<String, Object> manifest) {
        // TODO: Build peripheral capabilities from manifest
        // Example:
        // Map<String, Object> caps = (Map<String, Object>) manifest.getOrDefault("capabilities", Collections.emptyMap());
        // Map<String, Object> peripherals = (Map<String, Object>) caps.getOrDefault("peripherals", Collections.emptyMap());
        // 
        // return new PeripheralCapabilities.Builder()
        //     .set("scanner", (Boolean) peripherals.getOrDefault("scanner", false))
        //     .set("printer", (Boolean) peripherals.getOrDefault("printer", false))
        //     .build();
        
        throw new UnsupportedOperationException(
            "TODO: Implement buildPeripheralCapabilities for ${product_pascal}"
        );
    }

    @Override
    protected UiScreenSet buildUiScreenSet(Map<String, Object> manifest) {
        // TODO: Build UI screen set from manifest
        // Example:
        // return new UiScreenSet.Builder()
        //     .addScreen("welcome", "title", "subtitle", "start_button")
        //     .addScreen("basket", "items", "subtotal", "pay_button")
        //     .addScreen("payment", "total", "methods", "authorize_button")
        //     .addScreen("receipt", "title", "details", "new_button")
        //     .build();
        
        throw new UnsupportedOperationException(
            "TODO: Implement buildUiScreenSet for ${product_pascal}"
        );
    }

    @Override
    protected E2EFlowSteps buildE2EFlowSteps(Map<String, Object> manifest) {
        // TODO: Build E2E flow steps from manifest
        // Example:
        // return new E2EFlowSteps.Builder()
        //     .addStep("scan_item", "api_call", "pricing", "/price/{sku}", Map.of("sku", "SKU-1001"))
        //     .addStep("calculate_tax", "api_call", "tax", "/tax", Map.of("region", "CA"))
        //     .addStep("authorize_payment", "api_call", "payment", "/payment/authorize", Map.of("card", "4111111111111111"))
        //     .build();
        
        throw new UnsupportedOperationException(
            "TODO: Implement buildE2EFlowSteps for ${product_pascal}"
        );
    }
}
EOF
    echo "  ✅ Created: ${adapter_dir}/${product_pascal}Adapter.java"
    
    # ============================================================
    # Generate Test Fixture
    # ============================================================
    echo ""
    echo "📄 Generating test fixture stub..."
    
    cat > "${TESTS_DIR}/${product_pascal}Test.java" << EOF
package com.toshiba.pos.tests;

import com.toshiba.pos.adapter.${product_name}.${product_pascal}Adapter;
import com.toshiba.pos.engine.ProductE2EEngine;
import com.toshiba.pos.registry.AdapterRegistry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test fixture for ${product_pascal} product.
 * 
 * <p>TODO: Implement product-specific tests for ${product_pascal}.
 * 
 * <p>This test fails with a clear TODO message until implemented.
 */
public class ${product_pascal}Test {

    private ${product_pascal}Adapter adapter;
    private ProductE2EEngine engine;

    @BeforeClass
    public void setup() {
        adapter = new ${product_pascal}Adapter();
        adapter.load();
        engine = new ProductE2EEngine();
    }

    /**
     * Test the adapter loads correctly.
     */
    @Test
    public void testAdapterLoads() {
        // This test will fail until the adapter is fully implemented
        // TODO: Implement adapter methods
        System.out.println("========================================");
        System.out.println("  ${product_pascal} Test");
        System.out.println("========================================");
        System.out.println("  ⚠️  TODO: Implement adapter for ${product_pascal}");
        System.out.println("  See: ${adapter_dir}/${product_pascal}Adapter.java");
        System.out.println("========================================");
        
        fail("TODO: Implement ${product_pascal}Adapter.buildPeripheralCapabilities(), " +
             "buildUiScreenSet(), and buildE2EFlowSteps()");
    }

    /**
     * Test the adapter validates correctly.
     */
    @Test(dependsOnMethods = "testAdapterLoads")
    public void testAdapterValidates() {
        // This test will fail until the adapter is fully implemented
        try {
            adapter.validate();
        } catch (Exception e) {
            fail("Adapter validation failed: " + e.getMessage() + 
                 " (TODO: Implement adapter methods)");
        }
    }

    /**
     * Test E2E flow for ${product_pascal}.
     */
    @Test(dependsOnMethods = "testAdapterLoads")
    public void testE2EFlow() {
        // Register adapter and run E2E test
        AdapterRegistry.getInstance().register(adapter);
        
        // This will fail until the adapter is fully implemented
        // TODO: Implement E2E flow
        fail("TODO: Implement E2E flow for ${product_pascal}");
    }
}
EOF
    echo "  ✅ Created: ${TESTS_DIR}/${product_pascal}Test.java"
    
    # ============================================================
    # Summary
    # ============================================================
    echo ""
    echo "========================================"
    echo "  ✅ Scaffold Creation Complete!"
    echo "========================================"
    echo ""
    echo "📁 Files created:"
    echo "  1. ${manifest_dir}/manifest.json"
    echo "  2. ${adapter_dir}/${product_pascal}Adapter.java"
    echo "  3. ${TESTS_DIR}/${product_pascal}Test.java"
    echo ""
    echo "📝 Next steps:"
    echo "  1. Review and customize the manifest:"
    echo "     ${manifest_dir}/manifest.json"
    echo "  2. Implement the adapter methods:"
    echo "     ${adapter_dir}/${product_pascal}Adapter.java"
    echo "       - buildPeripheralCapabilities()"
    echo "       - buildUiScreenSet()"
    echo "       - buildE2EFlowSteps()"
    echo "  3. Implement the test fixture:"
    echo "     ${TESTS_DIR}/${product_pascal}Test.java"
    echo "  4. Add the product to the adapter registry (if needed)"
    echo "  5. Run the tests:"
    echo "     mvn test -Dtest=${product_pascal}Test"
    echo ""
    echo "⚠️  The generated stub fails its own placeholder test with a clear TODO."
    echo "   This is intentional — don't forget to implement the adapter!"
    echo "========================================"
}

# ============================================================
# Entry Point
# ============================================================

if [[ "${#@}" -lt 1 ]]; then
    print_usage
    exit 1
fi

main "$@"