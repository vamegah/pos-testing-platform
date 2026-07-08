#!/bin/bash
# socat_rs232_simulator.sh
# Virtual COM port pair for RS-232 peripheral emulation (scanner/scale)
# Uses socat to create linked pseudo-terminals (pty devices)
#
# This implements the "real-machine-less evaluation" approach:
# - Software simulates hardware peripherals
# - Reduces reliance on physical devices
# - Enables automated testing under wide conditions
#
# Usage:
#   ./socat_rs232_simulator.sh [start|stop|status]
#
# Examples:
#   ./socat_rs232_simulator.sh start
#   ./socat_rs232_simulator.sh status
#   ./socat_rs232_simulator.sh stop

set -euo pipefail

# ============================================
# Configuration
# ============================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${SCRIPT_DIR}/socat_rs232.log"
PID_FILE="${SCRIPT_DIR}/socat_rs232.pid"
SERVICE_NAME="socat-rs232-simulator"

# Device paths
# These are the virtual COM ports that will be created
DEVICE_PAIR_1="/tmp/pty-sim-1"  # Application side (e.g., POS system)
DEVICE_PAIR_2="/tmp/pty-sim-2"  # Peripheral side (e.g., scanner)

# Socat options
SOCAT_OPTS="-d -d -lf ${LOG_FILE}"

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

check_socat() {
    if ! command -v socat &> /dev/null; then
        error "socat is not installed. Please install it: sudo apt-get install socat (Ubuntu/Debian) or brew install socat (macOS)"
    fi
}

check_environment() {
    # Check if we're in a Docker container or on a supported OS
    if ! command -v stty &> /dev/null; then
        log "Warning: stty not found. Some terminal settings may not work."
    fi
}

is_running() {
    if [ -f "${PID_FILE}" ]; then
        local pid
        pid=$(cat "${PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            return 0
        else
            rm -f "${PID_FILE}"
            return 1
        fi
    fi
    return 1
}

# ============================================
# Core Functions
# ============================================

start() {
    log "Starting ${SERVICE_NAME}..."
    
    # Check if already running
    if is_running; then
        log "${SERVICE_NAME} is already running (PID: $(cat ${PID_FILE}))"
        return 0
    fi
    
    # Validate dependencies
    check_socat
    check_environment
    
    # Clean up stale socket files
    rm -f "${DEVICE_PAIR_1}" "${DEVICE_PAIR_2}" 2>/dev/null || true
    
    # Create virtual COM port pair using socat
    # This creates two linked pseudo-terminals:
    #   - One side acts as the application (POS system)
    #   - The other side acts as the peripheral (scanner/scale)
    #
    # The PTY devices are linked so data written to one is read from the other
    log "Creating virtual COM port pair..."
    log "  Application side: ${DEVICE_PAIR_1}"
    log "  Peripheral side: ${DEVICE_PAIR_2}"
    
    # Start socat in background
    # - PTY,link=... creates a pseudo-terminal and symlinks it to the specified path
    # - raw,echo=0 sets raw mode with echo disabled (proper RS-232 behavior)
    # - The two PTYs are linked together
    nohup socat ${SOCAT_OPTS} \
        PTY,link="${DEVICE_PAIR_1}",raw,echo=0 \
        PTY,link="${DEVICE_PAIR_2}",raw,echo=0 \
        > /dev/null 2>&1 &
    
    local pid=$!
    echo "${pid}" > "${PID_FILE}"
    
    # Wait for devices to be created
    local timeout=5
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if [ -e "${DEVICE_PAIR_1}" ] && [ -e "${DEVICE_PAIR_2}" ]; then
            log "Virtual COM ports created successfully:"
            log "  ${DEVICE_PAIR_1} -> $(readlink -f ${DEVICE_PAIR_1} 2>/dev/null || echo 'unknown')"
            log "  ${DEVICE_PAIR_2} -> $(readlink -f ${DEVICE_PAIR_2} 2>/dev/null || echo 'unknown')"
            log "${SERVICE_NAME} started (PID: ${pid})"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    
    # If we get here, devices were not created in time
    kill "${pid}" 2>/dev/null || true
    rm -f "${PID_FILE}"
    error "Failed to create virtual COM ports within timeout"
}

stop() {
    log "Stopping ${SERVICE_NAME}..."
    
    if ! is_running; then
        log "${SERVICE_NAME} is not running"
        return 0
    fi
    
    local pid
    pid=$(cat "${PID_FILE}")
    
    # Kill the process
    kill "${pid}" 2>/dev/null || true
    
    # Wait for process to terminate
    local timeout=5
    local elapsed=0
    while kill -0 "${pid}" 2>/dev/null && [ $elapsed -lt $timeout ]; do
        sleep 1
        elapsed=$((elapsed + 1))
    done
    
    # Force kill if still running
    if kill -0 "${pid}" 2>/dev/null; then
        kill -9 "${pid}" 2>/dev/null || true
        log "Force killed ${SERVICE_NAME} (PID: ${pid})"
    fi
    
    # Clean up
    rm -f "${PID_FILE}"
    rm -f "${DEVICE_PAIR_1}" "${DEVICE_PAIR_2}" 2>/dev/null || true
    
    log "${SERVICE_NAME} stopped"
}

status() {
    if is_running; then
        local pid
        pid=$(cat "${PID_FILE}")
        log "${SERVICE_NAME} is running (PID: ${pid})"
        
        # Show device status
        if [ -e "${DEVICE_PAIR_1}" ]; then
            log "  ${DEVICE_PAIR_1} -> $(readlink -f ${DEVICE_PAIR_1} 2>/dev/null || echo 'unknown')"
        else
            log "  ${DEVICE_PAIR_1}: NOT FOUND"
        fi
        
        if [ -e "${DEVICE_PAIR_2}" ]; then
            log "  ${DEVICE_PAIR_2} -> $(readlink -f ${DEVICE_PAIR_2} 2>/dev/null || echo 'unknown')"
        else
            log "  ${DEVICE_PAIR_2}: NOT FOUND"
        fi
        
        # Show recent logs
        if [ -f "${LOG_FILE}" ]; then
            log "Recent log entries:"
            tail -5 "${LOG_FILE}" | sed 's/^/    /'
        fi
    else
        log "${SERVICE_NAME} is not running"
    fi
}

test_communication() {
    log "Testing RS-232 communication..."
    
    if ! is_running; then
        error "${SERVICE_NAME} is not running. Please start it first."
    fi
    
    # Test writing to one end and reading from the other
    local test_message="SCAN_TEST|123456789012|2026-01-15T12:00:00Z"
    local test_file="/tmp/rs232_test_output.txt"
    
    log "Sending test message: ${test_message}"
    
    # Write to device 1 (application side)
    echo "${test_message}" > "${DEVICE_PAIR_1}" 2>/dev/null || {
        error "Failed to write to ${DEVICE_PAIR_1}"
    }
    
    # Read from device 2 (peripheral side) using timeout
    if command -v timeout &> /dev/null; then
        timeout 2 cat "${DEVICE_PAIR_2}" > "${test_file}" 2>/dev/null || {
            log "Warning: Timeout reading from ${DEVICE_PAIR_2}"
        }
    else
        cat "${DEVICE_PAIR_2}" > "${test_file}" 2>/dev/null &
        local cat_pid=$!
        sleep 2
        kill "${cat_pid}" 2>/dev/null || true
    fi
    
    # Check if data was received
    if [ -f "${test_file}" ] && [ -s "${test_file}" ]; then
        local received_data
        received_data=$(cat "${test_file}")
        log "✅ Received data: ${received_data}"
        rm -f "${test_file}"
        return 0
    else
        log "❌ No data received from virtual COM port"
        rm -f "${test_file}"
        return 1
    fi
}

# ============================================
# Usage
# ============================================
usage() {
    cat << EOF
Usage: $0 {start|stop|status|test|help}

Virtual COM port simulator for RS-232 peripherals (scanner/scale).

Commands:
  start   - Create virtual COM port pair and start socat
  stop    - Stop socat and clean up virtual COM ports
  status  - Show current status of the simulator
  test    - Test communication through the virtual COM ports
  help    - Show this help message

Examples:
  $0 start
  $0 status
  $0 test
  $0 stop

Environment variables:
  DEVICE_PAIR_1  - Override first device path (default: /tmp/pty-sim-1)
  DEVICE_PAIR_2  - Override second device path (default: /tmp/pty-sim-2)
  SOCAT_OPTS     - Additional options to pass to socat

EOF
}

# ============================================
# Main
# ============================================
main() {
    # Allow environment variable overrides
    DEVICE_PAIR_1="${DEVICE_PAIR_1:-/tmp/pty-sim-1}"
    DEVICE_PAIR_2="${DEVICE_PAIR_2:-/tmp/pty-sim-2}"
    
    # Ensure directories exist
    mkdir -p "$(dirname "${DEVICE_PAIR_1}")"
    mkdir -p "$(dirname "${DEVICE_PAIR_2}")"
    mkdir -p "$(dirname "${LOG_FILE}")"
    mkdir -p "$(dirname "${PID_FILE}")"
    
    case "${1:-help}" in
        start)
            start
            ;;
        stop)
            stop
            ;;
        status)
            status
            ;;
        test)
            test_communication
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