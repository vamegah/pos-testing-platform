# simulators/hardware-station/simulator.py

"""
Hardware Station Service (B3)

The mediator the POS App actually calls, which fans out to:
  - Printer
  - Cash Drawer
  - Scanner
  - Card Reader

Includes fault-isolation test: one peripheral down doesn't block the others.
"""

import os
import json
import logging
import time
import random
import requests
from datetime import datetime
from typing import Dict, Any, Optional, List
from enum import Enum
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# ============================================================
# Configuration
# ============================================================
# Service URLs (can be overridden by environment)
PRINTER_URL = os.environ.get("PRINTER_URL", "http://localhost:5007")
CASH_DRAWER_URL = os.environ.get("CASH_DRAWER_URL", "http://localhost:5010")
SCANNER_URL = os.environ.get(
    "SCANNER_URL", "http://localhost:5003"
)  # From Phase 12.4 wall profile
CARD_READER_URL = os.environ.get("CARD_READER_URL", "http://localhost:5009")

# Peripheral availability (for fault isolation tests)
peripheral_status = {
    "printer": {"available": True, "last_error": None},
    "cash_drawer": {"available": True, "last_error": None},
    "scanner": {"available": True, "last_error": None},
    "card_reader": {"available": True, "last_error": None},
}

# Session storage for transactions
sessions = {}

# ============================================================
# Helper Functions
# ============================================================


def generate_session_id() -> str:
    """Generate a unique session ID."""
    return f"HWS-{int(time.time())}-{random.randint(1000, 9999)}"


def call_peripheral(
    url: str,
    endpoint: str,
    method: str = "POST",
    payload: Optional[Dict] = None,
    timeout: int = 5,
) -> Dict[str, Any]:
    """Call a peripheral service with error handling."""
    try:
        full_url = f"{url}{endpoint}"
        if method.upper() == "GET":
            response = requests.get(full_url, timeout=timeout)
        else:
            response = requests.post(full_url, json=payload or {}, timeout=timeout)

        if response.status_code >= 400:
            return {
                "success": False,
                "status_code": response.status_code,
                "error": response.text[:200] if response.text else "Unknown error",
                "peripheral": endpoint.split("/")[1] if "/" in endpoint else "unknown",
            }

        return {
            "success": True,
            "status_code": response.status_code,
            "data": response.json() if response.text else {},
            "peripheral": endpoint.split("/")[1] if "/" in endpoint else "unknown",
        }
    except requests.exceptions.Timeout:
        return {
            "success": False,
            "error": "Timeout",
            "peripheral": endpoint.split("/")[1] if "/" in endpoint else "unknown",
        }
    except requests.exceptions.ConnectionError:
        return {
            "success": False,
            "error": "Connection refused (peripheral down)",
            "peripheral": endpoint.split("/")[1] if "/" in endpoint else "unknown",
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e),
            "peripheral": endpoint.split("/")[1] if "/" in endpoint else "unknown",
        }


# ============================================================
# Endpoints
# ============================================================


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return (
        jsonify(
            {
                "status": "healthy",
                "service": "hardware-station",
                "peripherals": {
                    "printer": peripheral_status["printer"]["available"],
                    "cash_drawer": peripheral_status["cash_drawer"]["available"],
                    "scanner": peripheral_status["scanner"]["available"],
                    "card_reader": peripheral_status["card_reader"]["available"],
                },
            }
        ),
        200,
    )


@app.route("/hardware-station/peripherals/status", methods=["GET"])
def get_peripheral_status():
    """Get the status of all peripherals."""
    return (
        jsonify(
            {
                "peripherals": peripheral_status,
            }
        ),
        200,
    )


@app.route("/hardware-station/peripherals/status/<peripheral>", methods=["GET"])
def get_peripheral_status_by_name(peripheral: str):
    """Get the status of a specific peripheral."""
    if peripheral not in peripheral_status:
        abort(404, description=f"Unknown peripheral: {peripheral}")
    return (
        jsonify(
            {
                "peripheral": peripheral,
                "status": peripheral_status[peripheral],
            }
        ),
        200,
    )


@app.route("/hardware-station/peripherals/<peripheral>/simulate-down", methods=["POST"])
def simulate_peripheral_down(peripheral: str):
    """
    Simulate a peripheral being down (for fault isolation tests).

    Expected payload:
    {
        "simulate": true,
        "duration_seconds": 60
    }
    """
    if peripheral not in peripheral_status:
        abort(404, description=f"Unknown peripheral: {peripheral}")

    data = request.get_json() or {}
    simulate = data.get("simulate", True)
    duration = data.get("duration_seconds", 60)

    old_status = peripheral_status[peripheral]["available"]
    peripheral_status[peripheral]["available"] = not simulate

    if simulate:
        peripheral_status[peripheral]["last_error"] = "Simulated down"
        logger.warning(f"Peripheral {peripheral} simulated as DOWN")

        # Schedule recovery if duration > 0
        if duration > 0:

            def recover():
                time.sleep(duration)
                peripheral_status[peripheral]["available"] = True
                peripheral_status[peripheral]["last_error"] = None
                logger.info(f"Peripheral {peripheral} auto-recovered after {duration}s")

            import threading

            threading.Thread(target=recover, daemon=True).start()
    else:
        peripheral_status[peripheral]["last_error"] = None
        logger.info(f"Peripheral {peripheral} restored")

    return (
        jsonify(
            {
                "status": "simulated_down" if simulate else "restored",
                "peripheral": peripheral,
                "available": peripheral_status[peripheral]["available"],
                "duration_seconds": duration if simulate else 0,
            }
        ),
        200,
    )


@app.route("/hardware-station/scan", methods=["POST"])
def scan_item():
    """
    Scan an item using the scanner peripheral.

    Expected payload:
    {
        "sku": "SKU-1001",
        "session_id": "txn-12345"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    sku = data.get("sku")
    session_id = data.get("session_id")

    if not sku:
        abort(400, description="Missing 'sku' field")
    if not session_id:
        abort(400, description="Missing 'session_id' field")

    # Check scanner availability
    if not peripheral_status["scanner"]["available"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": "Scanner is down",
                    "peripheral": "scanner",
                    "session_id": session_id,
                }
            ),
            503,
        )

    # Call scanner service
    result = call_peripheral(SCANNER_URL, "/mxp-smart-wall/scan", "POST", {"sku": sku})

    if not result["success"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": result.get("error", "Scanner failed"),
                    "peripheral": "scanner",
                    "session_id": session_id,
                }
            ),
            500,
        )

    # Update session
    if session_id not in sessions:
        sessions[session_id] = {"items": []}
    sessions[session_id]["items"].append(
        {"sku": sku, "scanned_at": datetime.utcnow().isoformat() + "Z"}
    )

    return (
        jsonify(
            {
                "status": "scanned",
                "session_id": session_id,
                "sku": sku,
                "peripheral": "scanner",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/hardware-station/print", methods=["POST"])
def print_receipt():
    """
    Print a receipt using the printer peripheral.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [...],
        "total": 10.00,
        "session_id": "txn-12345"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id")
    items = data.get("items", [])
    total = data.get("total", 0.0)
    session_id = data.get("session_id")

    if not transaction_id:
        abort(400, description="Missing 'transaction_id' field")

    # Check printer availability
    if not peripheral_status["printer"]["available"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": "Printer is down",
                    "peripheral": "printer",
                    "transaction_id": transaction_id,
                }
            ),
            503,
        )

    # Call printer service
    result = call_peripheral(
        PRINTER_URL,
        "/tcx-printer-single/print/receipt",
        "POST",
        {
            "transaction_id": transaction_id,
            "items": items,
            "total": total,
            "tax": 0.0,
            "subtotal": total,
        },
    )

    if not result["success"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": result.get("error", "Printer failed"),
                    "peripheral": "printer",
                    "transaction_id": transaction_id,
                }
            ),
            500,
        )

    return (
        jsonify(
            {
                "status": "printed",
                "transaction_id": transaction_id,
                "peripheral": "printer",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/hardware-station/cash-drawer/open", methods=["POST"])
def open_cash_drawer():
    """
    Open the cash drawer.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "tender_type": "cash",
        "amount": 10.00,
        "session_id": "txn-12345"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id")
    tender_type = data.get("tender_type", "cash")
    amount = data.get("amount", 0.0)

    if not transaction_id:
        abort(400, description="Missing 'transaction_id' field")

    # Check cash drawer availability
    if not peripheral_status["cash_drawer"]["available"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": "Cash drawer is down",
                    "peripheral": "cash_drawer",
                    "transaction_id": transaction_id,
                }
            ),
            503,
        )

    # Call cash drawer service
    result = call_peripheral(
        CASH_DRAWER_URL,
        "/cash-drawer/open",
        "POST",
        {
            "transaction_id": transaction_id,
            "tender_type": tender_type,
            "amount": amount,
        },
    )

    if not result["success"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": result.get("error", "Cash drawer failed"),
                    "peripheral": "cash_drawer",
                    "transaction_id": transaction_id,
                }
            ),
            500,
        )

    return (
        jsonify(
            {
                "status": "opened",
                "transaction_id": transaction_id,
                "peripheral": "cash_drawer",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/hardware-station/card-reader/process", methods=["POST"])
def process_card():
    """
    Process a card payment via the card reader.

    Expected payload:
    {
        "card_number": "4111111111111111",
        "amount": 10.00,
        "pin": "1234",
        "session_id": "txn-12345"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    card_number = data.get("card_number")
    amount = data.get("amount", 10.00)
    pin = data.get("pin")
    session_id = data.get("session_id")

    if not card_number:
        abort(400, description="Missing 'card_number' field")

    # Check card reader availability
    if not peripheral_status["card_reader"]["available"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": "Card reader is down",
                    "peripheral": "card_reader",
                }
            ),
            503,
        )

    # Call card reader service: insert card
    insert_result = call_peripheral(
        CARD_READER_URL,
        "/card-reader/insert",
        "POST",
        {
            "card_number": card_number,
            "entry_method": "dip",
        },
    )

    if not insert_result["success"]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": insert_result.get("error", "Card reader failed"),
                    "peripheral": "card_reader",
                    "step": "insert",
                }
            ),
            500,
        )

    insert_data = insert_result.get("data", {})

    # If PIN is required, enter it
    if insert_data.get("pin_required", False):
        if not pin:
            return (
                jsonify(
                    {
                        "status": "error",
                        "error": "PIN required but not provided",
                        "peripheral": "card_reader",
                        "step": "pin",
                    }
                ),
                400,
            )

        pin_result = call_peripheral(
            CARD_READER_URL, "/card-reader/pin", "POST", {"pin": pin}
        )
        if not pin_result["success"]:
            return (
                jsonify(
                    {
                        "status": "error",
                        "error": pin_result.get("error", "PIN entry failed"),
                        "peripheral": "card_reader",
                        "step": "pin",
                    }
                ),
                500,
            )

    return (
        jsonify(
            {
                "status": "processed",
                "transaction_id": insert_data.get("transaction_id"),
                "card_masked": insert_data.get("pan_masked"),
                "peripheral": "card_reader",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/hardware-station/fault-isolation", methods=["POST"])
def test_fault_isolation():
    """
    Fault-isolation test: one peripheral down doesn't block the others.

    This endpoint runs a test where one peripheral is simulated as down
    and verifies that other peripherals still work.

    Expected payload:
    {
        "down_peripheral": "printer"
    }
    """
    data = request.get_json() or {}
    down_peripheral = data.get("down_peripheral", "printer")

    if down_peripheral not in peripheral_status:
        abort(400, description=f"Unknown peripheral: {down_peripheral}")

    results = {}
    session_id = generate_session_id()

    # Simulate the specified peripheral as down
    simulate_down(down_peripheral, 60)
    results["simulated_down"] = down_peripheral

    # Try all peripheral operations (one should fail, others should succeed)
    # 1. Scanner (should work unless it's the down one)
    if down_peripheral != "scanner":
        scan_result = scan_item({"sku": "SKU-1001", "session_id": session_id})
        results["scanner"] = "success" if scan_result.status_code == 200 else "failed"
    else:
        scan_result = scan_item({"sku": "SKU-1001", "session_id": session_id})
        results["scanner"] = (
            "blocked" if scan_result.status_code == 503 else "unexpected"
        )

    # 2. Printer (should work unless it's the down one)
    if down_peripheral != "printer":
        print_result = print_receipt(
            {
                "transaction_id": session_id,
                "items": [
                    {
                        "sku": "SKU-1001",
                        "name": "Test Item",
                        "price": 10.00,
                        "quantity": 1,
                    }
                ],
                "total": 10.00,
                "session_id": session_id,
            }
        )
        results["printer"] = "success" if print_result.status_code == 200 else "failed"
    else:
        print_result = print_receipt(
            {
                "transaction_id": session_id,
                "items": [
                    {
                        "sku": "SKU-1001",
                        "name": "Test Item",
                        "price": 10.00,
                        "quantity": 1,
                    }
                ],
                "total": 10.00,
                "session_id": session_id,
            }
        )
        results["printer"] = (
            "blocked" if print_result.status_code == 503 else "unexpected"
        )

    # 3. Cash Drawer (should work unless it's the down one)
    if down_peripheral != "cash_drawer":
        drawer_result = open_cash_drawer(
            {
                "transaction_id": session_id,
                "tender_type": "cash",
                "amount": 10.00,
                "session_id": session_id,
            }
        )
        results["cash_drawer"] = (
            "success" if drawer_result.status_code == 200 else "failed"
        )
    else:
        drawer_result = open_cash_drawer(
            {
                "transaction_id": session_id,
                "tender_type": "cash",
                "amount": 10.00,
                "session_id": session_id,
            }
        )
        results["cash_drawer"] = (
            "blocked" if drawer_result.status_code == 503 else "unexpected"
        )

    # 4. Card Reader (should work unless it's the down one)
    if down_peripheral != "card_reader":
        card_result = process_card(
            {
                "card_number": "4111111111111111",
                "amount": 10.00,
                "session_id": session_id,
            }
        )
        results["card_reader"] = (
            "success" if card_result.status_code == 200 else "failed"
        )
    else:
        card_result = process_card(
            {
                "card_number": "4111111111111111",
                "amount": 10.00,
                "session_id": session_id,
            }
        )
        results["card_reader"] = (
            "blocked" if card_result.status_code == 503 else "unexpected"
        )

    # Verify fault isolation: only the down peripheral should be blocked
    blocked_count = sum(1 for v in results.values() if v == "blocked")
    success_count = sum(1 for v in results.values() if v == "success")

    logger.info(
        f"Fault isolation test: down={down_peripheral}, blocked={blocked_count}, success={success_count}"
    )

    return (
        jsonify(
            {
                "test": "fault_isolation",
                "down_peripheral": down_peripheral,
                "results": results,
                "summary": {
                    "blocked": blocked_count,
                    "success": success_count,
                    "passed": blocked_count == 1 and success_count == 3,
                },
            }
        ),
        200,
    )


# ============================================================
# Helper: Simulate peripheral down
# ============================================================


def simulate_down(peripheral: str, duration: int = 60):
    """Helper to simulate a peripheral being down."""
    if peripheral not in peripheral_status:
        return

    old_status = peripheral_status[peripheral]["available"]
    peripheral_status[peripheral]["available"] = False
    peripheral_status[peripheral]["last_error"] = "Simulated down"

    if duration > 0:

        def recover():
            time.sleep(duration)
            peripheral_status[peripheral]["available"] = True
            peripheral_status[peripheral]["last_error"] = None

        import threading

        threading.Thread(target=recover, daemon=True).start()


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5011))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Hardware Station Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
