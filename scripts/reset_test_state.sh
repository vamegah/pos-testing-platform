#!/bin/bash
# scripts/reset_test_state.sh
# Reset all stateful mock services between test suites

set -euo pipefail

echo "========================================"
echo "  Resetting Test State"
echo "========================================"

# Services and their reset endpoints
services=(
    "http://localhost:8085/test/reset"  # Inventory
    "http://localhost:8086/test/reset"  # Order Processing
    "http://localhost:5013/test/reset"  # Local Data Cache
    "http://localhost:8089/test/reset"  # Loyalty
)

for service in "${services[@]}"; do
    echo "Resetting: $service"
    response=$(curl -s -X POST "$service" || echo "Failed")
    if [[ "$response" == *"reset"* ]]; then
        echo "  ✅ $service reset successfully"
    else
        echo "  ⚠️ $service may not be available: $response"
    fi
done

echo "========================================"
echo "  Test state reset complete"
echo "========================================"