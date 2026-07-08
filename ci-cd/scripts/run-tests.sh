#!/bin/bash
# ci-cd/run-tests.sh
# Test runner script for the containerized test suite

set -e

echo "========================================"
echo "  POS Test Runner Container"
echo "========================================"
echo "  Environment: ${ENVIRONMENT:-test}"
echo "  Log Level: ${LOG_LEVEL:-INFO}"
echo "  Test Groups: ${TEST_GROUPS:-all}"
echo "========================================"

echo ""
echo "🔍 Service URLs:"
echo "  Pricing:    ${PRICING_SERVICE_URL:-http://pricing-service:8081}"
echo "  Promotions: ${PROMOTIONS_SERVICE_URL:-http://promotions-service:8082}"
echo "  Tax:        ${TAX_SERVICE_URL:-http://tax-service:8083}"
echo "  Payment:    ${PAYMENT_GATEWAY_URL:-http://payment-gateway:8084}"
echo ""

# Verify services are reachable (if not in test-only mode)
if [ "${SKIP_SERVICE_CHECK:-false}" != "true" ]; then
    echo "🔍 Checking service connectivity..."
    
    for service in "PRICING_SERVICE_URL" "PROMOTIONS_SERVICE_URL" "TAX_SERVICE_URL" "PAYMENT_GATEWAY_URL"; do
        url="${!service}"
        if [ -n "$url" ]; then
            # Extract host and port for health check
            # Remove protocol
            host_port="${url#http://}"
            # Check if it's localhost (skip health check for localhost if services not running)
            if [[ "$host_port" != localhost* ]]; then
                health_url="${url}/health"
                echo "  Checking ${service}: ${health_url}"
                if curl -s -f -o /dev/null "${health_url}" 2>/dev/null; then
                    echo "    ✅ ${service} is reachable"
                else
                    echo "    ⚠️ ${service} is not reachable (continuing anyway)"
                fi
            fi
        fi
    done
    echo ""
fi

# Determine test groups
test_groups="${TEST_GROUPS:-all}"
maven_opts=""

if [ "$test_groups" != "all" ] && [ "$test_groups" != "none" ]; then
    # Groups are comma-separated for TestNG
    # e.g., "api,smoke" or "api"
    maven_opts="-Dgroups=${test_groups}"
elif [ "$test_groups" = "none" ]; then
    echo "⚠️ TEST_GROUPS=none - Skipping tests"
    exit 0
fi

echo "🚀 Running tests with groups: ${test_groups}"
echo ""

# Run the tests
mvn test \
    -DskipTests=false \
    ${maven_opts} \
    -DPRICING_SERVICE_URL="${PRICING_SERVICE_URL}" \
    -DPROMOTIONS_SERVICE_URL="${PROMOTIONS_SERVICE_URL}" \
    -DTAX_SERVICE_URL="${TAX_SERVICE_URL}" \
    -DPAYMENT_GATEWAY_URL="${PAYMENT_GATEWAY_URL}" \
    -Denvironment="${ENVIRONMENT:-test}" \
    -Dlog.level="${LOG_LEVEL:-INFO}"

# Check results
if [ -d "target/surefire-reports" ]; then
    echo ""
    echo "📊 Test Results:"
    find target/surefire-reports -name "*.xml" -exec echo "  - {}" \;
    
    # Count test results
    total=$(find target/surefire-reports -name "*.xml" -exec grep -l "tests=\"" {} \; | wc -l)
    if [ "$total" -gt 0 ]; then
        echo "  Total test files: $total"
    fi
fi

echo ""
echo "✅ Test execution complete"