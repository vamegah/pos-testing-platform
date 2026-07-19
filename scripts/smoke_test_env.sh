#!/bin/bash
# scripts/smoke_test_env.sh
# Environment Smoke-Test CI Gate
#
# From a clean checkout, boot the full stack and verify every service's
# health-check passes. Run this before the product/architecture test stages.
#
# Usage:
#   ./scripts/smoke_test_env.sh [profile]
#
# Examples:
#   ./scripts/smoke_test_env.sh           # Full stack
#   ./scripts/smoke_test_env.sh api       # API-only services
#   ./scripts/smoke_test_env.sh ui        # UI services

set -euo pipefail

PROFILE="${1:-full}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ============================================================
# Service URLs (from registry)
# ============================================================
# Core services
PRICING_URL="http://localhost:8081/health"
PROMOTIONS_URL="http://localhost:8082/health"
TAX_URL="http://localhost:8083/health"
PAYMENT_URL="http://localhost:8084/health"

# API services (if API profile)
GATEWAY_URL="http://localhost:5014/health"
INVENTORY_URL="http://localhost:8085/health"
ORDER_URL="http://localhost:8086/health"
CRM_URL="http://localhost:8087/health"

# UI services (if UI profile)
KIOSK_UI_URL="http://localhost:8080/"

# Architecture services (if full/architecture profile)
CARD_READER_URL="http://localhost:5009/health"
CASH_DRAWER_URL="http://localhost:5010/health"
HARDWARE_STATION_URL="http://localhost:5011/health"
COMMERCE_SCALE_UNIT_URL="http://localhost:5012/health"
LOCAL_DATA_CACHE_URL="http://localhost:5013/health"
DATA_SERVICES_URL="http://localhost:8088/health"
LOYALTY_URL="http://localhost:8089/health"
ERP_URL="http://localhost:8090/health"

# ============================================================
# Functions
# ============================================================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

check_service() {
    local name="$1"
    local url="$2"
    local max_retries="${3:-5}"
    local retry_delay="${4:-2}"
    
    log_info "Checking $name at $url"
    
    for i in $(seq 1 $max_retries); do
        if curl -s -f -o /dev/null "$url" 2>/dev/null; then
            log_info "  ✅ $name is healthy"
            return 0
        fi
        log_warn "  ⏳ $name not ready (attempt $i/$max_retries)"
        sleep "$retry_delay"
    done
    
    log_error "  ❌ $name failed health check"
    return 1
}

check_service_with_response() {
    local name="$1"
    local url="$2"
    local max_retries="${3:-5}"
    local retry_delay="${4:-2}"
    
    log_info "Checking $name at $url"
    
    for i in $(seq 1 $max_retries); do
        response=$(curl -s "$url" 2>/dev/null || echo "")
        if echo "$response" | grep -q "healthy\|status.*ok\|up"; then
            log_info "  ✅ $name is healthy"
            return 0
        fi
        log_warn "  ⏳ $name not ready (attempt $i/$max_retries)"
        sleep "$retry_delay"
    done
    
    log_error "  ❌ $name failed health check"
    return 1
}

start_services() {
    local profile="$1"
    
    log_info "Starting services with profile: $profile"
    
    cd "$PROJECT_ROOT"
    
    case "$profile" in
        api)
            docker compose -f docker-compose.yml -f docker-compose.api.yml up -d
            ;;
        ui)
            docker compose -f docker-compose.yml -f docker-compose.ui.yml up -d
            ;;
        architecture)
            docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml up -d
            ;;
        full)
            docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml -f docker-compose.ui.yml up -d
            ;;
        *)
            log_error "Unknown profile: $profile"
            exit 1
            ;;
    esac
    
    log_info "Services started. Waiting for services to be ready..."
    sleep 5
}

stop_services() {
    local profile="$1"
    
    log_info "Stopping services..."
    
    cd "$PROJECT_ROOT"
    
    case "$profile" in
        api)
            docker compose -f docker-compose.yml -f docker-compose.api.yml down
            ;;
        ui)
            docker compose -f docker-compose.yml -f docker-compose.ui.yml down
            ;;
        architecture)
            docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml down
            ;;
        full)
            docker compose -f docker-compose.yml -f docker-compose.api.yml -f docker-compose.architecture.yml -f docker-compose.ui.yml down
            ;;
    esac
}

# ============================================================
# Main
# ============================================================

main() {
    local profile="$1"
    local services_checked=0
    local services_failed=0
    local failed_services=""
    
    log_info "========================================"
    log_info "  Environment Smoke-Test CI Gate"
    log_info "  Profile: $profile"
    log_info "========================================"
    
    # Start services
    start_services "$profile"
    
    # Check core services
    log_info "--- Core Services ---"
    if check_service "Pricing" "$PRICING_URL"; then
        ((services_checked++))
    else
        ((services_failed++))
        failed_services="$failed_services pricing"
    fi
    
    if check_service "Promotions" "$PROMOTIONS_URL"; then
        ((services_checked++))
    else
        ((services_failed++))
        failed_services="$failed_services promotions"
    fi
    
    if check_service "Tax" "$TAX_URL"; then
        ((services_checked++))
    else
        ((services_failed++))
        failed_services="$failed_services tax"
    fi
    
    if check_service "Payment Gateway" "$PAYMENT_URL"; then
        ((services_checked++))
    else
        ((services_failed++))
        failed_services="$failed_services payment"
    fi
    
    # Check services based on profile
    if [[ "$profile" == "api" ]] || [[ "$profile" == "architecture" ]] || [[ "$profile" == "full" ]]; then
        log_info "--- API Services ---"
        if check_service "API Gateway" "$GATEWAY_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services gateway"
        fi
        
        if check_service "Inventory" "$INVENTORY_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services inventory"
        fi
        
        if check_service "Order Processing" "$ORDER_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services order"
        fi
        
        if check_service "CRM" "$CRM_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services crm"
        fi
    fi
    
    if [[ "$profile" == "ui" ]] || [[ "$profile" == "full" ]]; then
        log_info "--- UI Services ---"
        if check_service_with_response "Kiosk UI" "$KIOSK_UI_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services kiosk-ui"
        fi
    fi
    
    if [[ "$profile" == "architecture" ]] || [[ "$profile" == "full" ]]; then
        log_info "--- Architecture Services ---"
        if check_service "Card Reader" "$CARD_READER_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services card-reader"
        fi
        
        if check_service "Cash Drawer" "$CASH_DRAWER_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services cash-drawer"
        fi
        
        if check_service "Hardware Station" "$HARDWARE_STATION_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services hardware-station"
        fi
        
        if check_service "Commerce Scale Unit" "$COMMERCE_SCALE_UNIT_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services commerce-scale-unit"
        fi
        
        if check_service "Local Data Cache" "$LOCAL_DATA_CACHE_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services local-data-cache"
        fi
        
        if check_service "Data Services" "$DATA_SERVICES_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services data-services"
        fi
        
        if check_service "Loyalty" "$LOYALTY_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services loyalty"
        fi
        
        if check_service "ERP" "$ERP_URL"; then
            ((services_checked++))
        else
            ((services_failed++))
            failed_services="$failed_services erp"
        fi
    fi
    
    # Report results
    log_info "========================================"
    log_info "  Smoke-Test Results"
    log_info "========================================"
    log_info "  Services checked: $services_checked"
    log_info "  Services passed: $((services_checked - services_failed))"
    log_info "  Services failed: $services_failed"
    
    if [[ $services_failed -gt 0 ]]; then
        log_error "  Failed services: $failed_services"
        log_error "  ❌ Smoke-test FAILED"
        stop_services "$profile"
        exit 1
    fi
    
    log_info "  ✅ All services healthy!"
    log_info "========================================"
    
    # Keep services running for subsequent stages
    log_info "Services are running and healthy. Proceeding to tests..."
    
    return 0
}

# ============================================================
# Entry Point
# ============================================================

main "$PROFILE"