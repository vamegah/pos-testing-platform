#!/bin/bash
# usbip_bind_script.sh
# USB-over-IP (USB/IP) peripheral sharing stub for POS testing
#
# This script provides a documented interface for sharing USB peripherals
# (scanners, printers, PIN pads, etc.) over a network using USB/IP.
#
# The script checks for device presence before acting and safely no-ops
# if no real device is attached — no-op unless a real device is present.
#
# This implements the "real-machine-less evaluation" approach:
# - USB peripherals can be shared across test environments
# - Reduces physical hardware requirements
# - Enables remote testing
#
# Usage:
#   ./usbip_bind_script.sh [list|bind|unbind|status]
#
# Examples:
#   ./usbip_bind_script.sh list          # List available USB devices
#   ./usbip_bind_script.sh bind 1234:5678  # Bind device with VID:PID
#   ./usbip_bind_script.sh unbind 1234:5678  # Unbind device
#   ./usbip_bind_script.sh status        # Show USB/IP status

set -euo pipefail

# ============================================
# Configuration
# ============================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${SCRIPT_DIR}/usbip.log"
SERVICE_NAME="usbip-hub"

# USB/IP configuration
USBIP_HOST="localhost"          # Host running usbipd
USBIP_PORT="3240"               # Default USB/IP port
BIND_TIMEOUT="10"               # Seconds to wait for bind to complete

# ============================================
# Helper Functions
# ============================================

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

error() {
    echo "ERROR: $*" >&2
    log "ERROR: $*"
    exit 1
}

check_usbip() {
    if ! command -v usbip &> /dev/null; then
        echo "⚠️ usbip is not installed. This is a stub script."
        echo ""
        echo "To install USB/IP on your system:"
        echo "  Ubuntu/Debian: sudo apt-get install usbip linux-tools-$(uname -r)"
        echo "  RHEL/CentOS: sudo yum install usbip"
        echo "  macOS: USB/IP is not natively supported. Use a Linux VM or container."
        echo ""
        echo "For testing without USB/IP, this script will operate in stub mode."
        return 1
    fi
    return 0
}

check_usbipd() {
    if ! pgrep -x "usbipd" > /dev/null 2>&1; then
        echo "⚠️ usbipd (USB/IP daemon) is not running."
        echo "  Start it with: sudo usbipd -D"
        return 1
    fi
    return 0
}

check_root() {
    if [ "$EUID" -ne 0 ]; then
        echo "⚠️ USB/IP operations typically require root privileges."
        echo "  Consider running with: sudo $0 $*"
        echo ""
        return 1
    fi
    return 0
}

# ============================================
# Core Functions
# ============================================

list_devices() {
    log "Listing USB devices..."
    
    if ! check_usbip; then
        # Stub mode: list simulated devices
        log "Running in stub mode (USB/IP not installed)."
        cat << EOF
=== STUB MODE - Simulated USB Devices ===
VID:PID    Manufacturer    Product
1234:5678  Toshiba         TCx Printer
2345:6789  Honeywell       Barcode Scanner
3456:7890  Ingenico        PIN Pad
4567:8901  Micros          Scale
EOF
        return 0
    fi
    
    if ! check_root; then
        # Try with sudo if available
        if command -v sudo &> /dev/null; then
            log "Attempting with sudo..."
            exec sudo "$0" list_devices
        fi
    fi
    
    # Real USB/IP: list devices via usbip
    if usbip list -l 2>/dev/null; then
        return 0
    else
        log "No USB devices found or usbip command failed."
        log "Check that usbipd is running and devices are attached."
        return 1
    fi
}

bind_device() {
    local vid_pid="${1:-}"
    
    if [ -z "${vid_pid}" ]; then
        error "Usage: bind_device <VID:PID>"
        error "Example: bind_device 1234:5678"
    fi
    
    log "Binding USB device: ${vid_pid}..."
    
    if ! check_usbip; then
        log "Running in stub mode (USB/IP not installed)."
        log "Simulating bind for: ${vid_pid}"
        log "✅ Device bind simulated: ${vid_pid}"
        return 0
    fi
    
    if ! check_root; then
        # Try with sudo if available
        if command -v sudo &> /dev/null; then
            log "Attempting with sudo..."
            exec sudo "$0" bind_device "${vid_pid}"
        fi
    fi
    
    # Parse VID:PID
    local vid="${vid_pid%:*}"
    local pid="${vid_pid#*:}"
    
    # Check if device exists
    if ! lsusb -d "${vid}:${pid}" &> /dev/null; then
        log "⚠️ Device ${vid_pid} not found on USB bus."
        log "No-op: Device not attached, nothing to bind."
        return 1
    fi
    
    # Bind the device using usbip
    if usbip bind -b "${vid}:${pid}" 2>/dev/null; then
        log "✅ Device ${vid_pid} bound successfully"
        log "  Run 'usbip list -r ${USBIP_HOST}' to verify"
        return 0
    else
        log "❌ Failed to bind device ${vid_pid}"
        log "  Device may already be bound or in use."
        log "  Try: usbip unbind -b ${vid_pid}  # then retry"
        return 1
    fi
}

unbind_device() {
    local vid_pid="${1:-}"
    
    if [ -z "${vid_pid}" ]; then
        error "Usage: unbind_device <VID:PID>"
        error "Example: unbind_device 1234:5678"
    fi
    
    log "Unbinding USB device: ${vid_pid}..."
    
    if ! check_usbip; then
        log "Running in stub mode (USB/IP not installed)."
        log "Simulating unbind for: ${vid_pid}"
        log "✅ Device unbind simulated: ${vid_pid}"
        return 0
    fi
    
    if ! check_root; then
        # Try with sudo if available
        if command -v sudo &> /dev/null; then
            log "Attempting with sudo..."
            exec sudo "$0" unbind_device "${vid_pid}"
        fi
    fi
    
    # Unbind the device using usbip
    if usbip unbind -b "${vid_pid}" 2>/dev/null; then
        log "✅ Device ${vid_pid} unbound successfully"
        return 0
    else
        log "❌ Failed to unbind device ${vid_pid}"
        log "  Device may not be bound or may not exist."
        return 1
    fi
}

status() {
    log "USB/IP Status..."
    
    echo ""
    echo "=== USB/IP Status ==="
    
    # Check if USB/IP is installed
    if command -v usbip &> /dev/null; then
        echo "✅ usbip installed: $(usbip version 2>/dev/null | head -1 || echo 'unknown')"
    else
        echo "❌ usbip not installed (running in stub mode)"
    fi
    
    # Check if usbipd is running
    if pgrep -x "usbipd" > /dev/null 2>&1; then
        echo "✅ usbipd running (PID: $(pgrep -x usbipd))"
    else
        echo "⚠️ usbipd not running"
    fi
    
    # Check for USB/IP kernel module
    if lsmod | grep -q "usbip"; then
        echo "✅ USB/IP kernel module loaded"
    else
        echo "⚠️ USB/IP kernel module not loaded (may need: modprobe usbip_core usbip_host)"
    fi
    
    # Check if usbipd is listening on the default port
    if ss -tuln 2>/dev/null | grep -q ":${USBIP_PORT} " || netstat -tuln 2>/dev/null | grep -q ":${USBIP_PORT} "; then
        echo "✅ usbipd listening on port ${USBIP_PORT}"
    else
        echo "ℹ️ usbipd not listening on port ${USBIP_PORT} (may be using different port)"
    fi
    
    echo ""
    echo "=== USB Device Summary ==="
    
    # List USB devices (lsusb)
    if command -v lsusb &> /dev/null; then
        echo "Attached USB devices:"
        lsusb | head -5
        local count=$(lsusb | wc -l)
        if [ ${count} -gt 5 ]; then
            echo "  ... and $((count - 5)) more devices"
        fi
    else
        echo "⚠️ lsusb not available"
    fi
    
    echo ""
    echo "=== USB/IP Exported Devices ==="
    if command -v usbip &> /dev/null && check_usbipd 2>/dev/null; then
        usbip list -r "${USBIP_HOST}" 2>/dev/null || echo "  No exported devices found"
    else
        echo "  USB/IP not available (stub mode)"
        echo "  Simulated exported devices:"
        echo "    1234:5678 - Toshiba TCx Printer"
        echo "    2345:6789 - Honeywell Barcode Scanner"
    fi
}

# ============================================
# Usage
# ============================================
usage() {
    cat << EOF
Usage: $0 {list|bind|unbind|status|help}

USB/IP peripheral sharing for POS testing.

Commands:
  list                - List available USB devices (or stub devices)
  bind <VID:PID>      - Bind device with VID:PID (e.g., 1234:5678)
  unbind <VID:PID>    - Unbind device with VID:PID
  status              - Show USB/IP status (installed, running, devices)
  help                - Show this help message

Examples:
  $0 list                     # List devices (real or stub)
  $0 bind 1234:5678           # Bind Toshiba printer
  $0 unbind 1234:5678         # Unbind printer
  $0 status                   # Full status report

Environment variables:
  USBIP_HOST   - USB/IP server host (default: localhost)
  USBIP_PORT   - USB/IP server port (default: 3240)

Notes:
  - This script checks for device presence before acting
  - If no real device is attached, it safely no-ops
  - In stub mode (without usbip installed), operations are simulated
  - USB/IP typically requires root privileges

EOF
}

# ============================================
# Main
# ============================================
main() {
    # Allow environment variable overrides
    USBIP_HOST="${USBIP_HOST:-localhost}"
    USBIP_PORT="${USBIP_PORT:-3240}"
    
    # Ensure directories exist
    mkdir -p "$(dirname "${LOG_FILE}")"
    
    case "${1:-help}" in
        list|list_devices)
            list_devices
            ;;
        bind|bind_device)
            bind_device "${2:-}"
            ;;
        unbind|unbind_device)
            unbind_device "${2:-}"
            ;;
        status)
            status
            ;;
        help|--help|-h)
            usage
            ;;
        *)
            echo "Unknown command: ${1:-}"
            echo ""
            usage
            exit 1
            ;;
    esac
}

main "$@"