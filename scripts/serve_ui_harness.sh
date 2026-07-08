#!/bin/bash
# scripts/serve_ui_harness.sh
# Local static file server for the Kiosk UI Harness
# 
# Usage:
#   ./scripts/serve_ui_harness.sh [port]
# 
# Examples:
#   ./scripts/serve_ui_harness.sh         # Serve on default port 8080
#   ./scripts/serve_ui_harness.sh 9000    # Serve on port 9000
#
# Requirements:
#   - Python 3.x (for http.server)
#   - The kiosk UI harness must be at simulators/kiosk-ui-harness/

set -euo pipefail

# Default port
PORT="${1:-8080}"

# Directory containing the UI harness
HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../simulators/kiosk-ui-harness" && pwd)"

# Check if harness exists
if [ ! -d "$HARNESS_DIR" ]; then
    echo "❌ Error: Kiosk UI harness not found at $HARNESS_DIR"
    echo "   Make sure simulators/kiosk-ui-harness/ exists."
    exit 1
fi

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "❌ Error: python3 not found. Please install Python 3."
    exit 1
fi

echo "=========================================="
echo "  Kiosk UI Harness - Static File Server"
echo "=========================================="
echo "  Serving: $HARNESS_DIR"
echo "  Port:    $PORT"
echo "  URL:     http://localhost:$PORT"
echo "=========================================="
echo ""
echo "Press Ctrl+C to stop the server."
echo ""

# Start Python HTTP server
cd "$HARNESS_DIR"
python3 -m http.server "$PORT"