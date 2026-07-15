# hardware-emulation/cash-drawer/simulator.py

"""
Cash Drawer Peripheral Simulator (B2)

Models the physical cash drawer open/close signal fired only on completed cash tender,
plus a "drawer left open" alert scenario.

Capabilities:
  - Open signal (fired only on completed cash tender)
  - Close signal (manual or automatic)
  - Drawer left open alert (after configurable timeout)
  - State tracking (open/closed/alert)
  - Events emitted for each action

This is a peripheral simulator that responds to cash tender completion.
"""

import os
import json
import logging
import time
import threading
from datetime import datetime
from typing import Dict, Any, Optional
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
# Time in seconds before a "drawer left open" alert is triggered
DRAWER_OPEN_ALERT_TIMEOUT = int(os.environ.get("DRAWER_OPEN_ALERT_TIMEOUT", 30))
# Time in seconds before an open drawer automatically closes (optional)
AUTO_CLOSE_TIMEOUT = int(os.environ.get("AUTO_CLOSE_TIMEOUT", 300))

# ============================================================
# State Management
# ============================================================


class DrawerState(Enum):
    CLOSED = "closed"
    OPEN = "open"
    ALERT = "alert"
    UNKNOWN = "unknown"


class TenderType(Enum):
    CASH = "cash"
    CARD = "card"
    NFC = "nfc"
    BIOMETRIC = "biometric"


# Cash drawer state
drawer_state = {
    "state": DrawerState.CLOSED.value,
    "open_time": None,
    "close_time": None,
    "last_tender_type": None,
    "transaction_id": None,
    "alert_triggered": False,
    "alert_time": None,
    "events": [],
}

# Alert timer (for drawer left open)
alert_timer = None

# ============================================================
# Helper Functions
# ============================================================


def generate_event_id() -> int:
    """Generate a unique event ID."""
    return len(drawer_state["events"]) + 1


def emit_event(event_type: str, data: Dict[str, Any]) -> Dict[str, Any]:
    """Emit a cash drawer event."""
    event = {
        "event_id": generate_event_id(),
        "event_type": event_type,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "data": data,
    }
    drawer_state["events"].append(event)
    logger.info(f"Cash drawer event: {event_type}")
    return event


def cancel_alert_timer():
    """Cancel the alert timer if it's running."""
    global alert_timer
    if alert_timer:
        alert_timer.cancel()
        alert_timer = None
        logger.debug("Alert timer cancelled")


def schedule_alert_timer():
    """Schedule the drawer left open alert timer."""
    global alert_timer
    cancel_alert_timer()

    def trigger_alert():
        if drawer_state["state"] == DrawerState.OPEN.value:
            drawer_state["state"] = DrawerState.ALERT.value
            drawer_state["alert_triggered"] = True
            drawer_state["alert_time"] = datetime.utcnow().isoformat() + "Z"
            emit_event(
                "alert_drawer_left_open",
                {
                    "open_time": drawer_state["open_time"],
                    "alert_time": drawer_state["alert_time"],
                    "transaction_id": drawer_state["transaction_id"],
                },
            )
            logger.warning("⚠️ Drawer left open alert triggered!")

    alert_timer = threading.Timer(DRAWER_OPEN_ALERT_TIMEOUT, trigger_alert)
    alert_timer.daemon = True
    alert_timer.start()
    logger.debug(f"Alert timer scheduled for {DRAWER_OPEN_ALERT_TIMEOUT}s")


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
                "service": "cash-drawer-simulator",
                "state": drawer_state["state"],
            }
        ),
        200,
    )


@app.route("/cash-drawer/state", methods=["GET"])
def get_state():
    """Get the current cash drawer state."""
    return (
        jsonify(
            {
                "state": drawer_state["state"],
                "is_open": drawer_state["state"] == DrawerState.OPEN.value,
                "is_closed": drawer_state["state"] == DrawerState.CLOSED.value,
                "is_alert": drawer_state["state"] == DrawerState.ALERT.value,
                "open_time": drawer_state["open_time"],
                "close_time": drawer_state["close_time"],
                "last_tender_type": drawer_state["last_tender_type"],
                "transaction_id": drawer_state["transaction_id"],
                "alert_triggered": drawer_state["alert_triggered"],
                "events_count": len(drawer_state["events"]),
            }
        ),
        200,
    )


@app.route("/cash-drawer/open", methods=["POST"])
def open_drawer():
    """
    Open the cash drawer (only on completed cash tender).

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "tender_type": "cash",
        "amount": 10.00
    }
    """
    global drawer_state

    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id")
    tender_type = data.get("tender_type", "cash")
    amount = data.get("amount", 0.0)

    # Only cash tender opens the drawer
    if tender_type != TenderType.CASH.value:
        return (
            jsonify(
                {
                    "status": "ignored",
                    "reason": f"Drawer only opens on cash tender (tender_type: {tender_type})",
                    "transaction_id": transaction_id,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )

    if not transaction_id:
        abort(400, description="Missing 'transaction_id' field")

    if drawer_state["state"] in [DrawerState.OPEN.value, DrawerState.ALERT.value]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": f"Drawer is already {drawer_state['state']}",
                    "transaction_id": transaction_id,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            409,
        )

    # Open the drawer
    drawer_state["state"] = DrawerState.OPEN.value
    drawer_state["open_time"] = datetime.utcnow().isoformat() + "Z"
    drawer_state["close_time"] = None
    drawer_state["last_tender_type"] = tender_type
    drawer_state["transaction_id"] = transaction_id
    drawer_state["alert_triggered"] = False

    emit_event(
        "drawer_opened",
        {
            "transaction_id": transaction_id,
            "tender_type": tender_type,
            "amount": amount,
            "open_time": drawer_state["open_time"],
        },
    )

    logger.info(f"Cash drawer opened for transaction: {transaction_id}")

    # Schedule alert timer if drawer stays open too long
    schedule_alert_timer()

    return (
        jsonify(
            {
                "status": "opened",
                "transaction_id": transaction_id,
                "tender_type": tender_type,
                "amount": amount,
                "open_time": drawer_state["open_time"],
                "alert_timeout_seconds": DRAWER_OPEN_ALERT_TIMEOUT,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/cash-drawer/close", methods=["POST"])
def close_drawer():
    """
    Close the cash drawer (manual or automatic).

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "auto_close": false
    }
    """
    global drawer_state

    data = request.get_json() or {}
    transaction_id = data.get("transaction_id")
    auto_close = data.get("auto_close", False)

    if drawer_state["state"] == DrawerState.CLOSED.value:
        return (
            jsonify(
                {
                    "status": "already_closed",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )

    if drawer_state["state"] == DrawerState.ALERT.value:
        # Closing from alert state resets the alert
        drawer_state["alert_triggered"] = False
        drawer_state["alert_time"] = None

    # Close the drawer
    cancel_alert_timer()
    drawer_state["state"] = DrawerState.CLOSED.value
    drawer_state["close_time"] = datetime.utcnow().isoformat() + "Z"
    drawer_state["transaction_id"] = None

    emit_event(
        "drawer_closed",
        {
            "transaction_id": transaction_id,
            "close_time": drawer_state["close_time"],
            "auto_close": auto_close,
        },
    )

    logger.info(f"Cash drawer closed (auto_close: {auto_close})")

    return (
        jsonify(
            {
                "status": "closed",
                "close_time": drawer_state["close_time"],
                "auto_close": auto_close,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/cash-drawer/alert/status", methods=["GET"])
def get_alert_status():
    """Get the current alert status."""
    return (
        jsonify(
            {
                "alert_triggered": drawer_state["alert_triggered"],
                "alert_time": drawer_state["alert_time"],
                "drawer_state": drawer_state["state"],
                "open_time": drawer_state["open_time"],
                "timeout_seconds": DRAWER_OPEN_ALERT_TIMEOUT,
            }
        ),
        200,
    )


@app.route("/cash-drawer/alert/dismiss", methods=["POST"])
def dismiss_alert():
    """Dismiss the drawer left open alert."""
    global drawer_state

    if drawer_state["state"] != DrawerState.ALERT.value:
        return (
            jsonify(
                {"status": "no_alert", "timestamp": datetime.utcnow().isoformat() + "Z"}
            ),
            200,
        )

    # Dismiss the alert by closing the drawer
    return close_drawer()


@app.route("/cash-drawer/events", methods=["GET"])
def get_events():
    """Get all cash drawer events."""
    return (
        jsonify(
            {
                "events": drawer_state["events"],
                "count": len(drawer_state["events"]),
                "drawer_state": drawer_state["state"],
            }
        ),
        200,
    )


@app.route("/cash-drawer/clear", methods=["POST"])
def clear_state():
    """Clear the cash drawer state and reset."""
    global drawer_state, alert_timer

    cancel_alert_timer()
    drawer_state = {
        "state": DrawerState.CLOSED.value,
        "open_time": None,
        "close_time": None,
        "last_tender_type": None,
        "transaction_id": None,
        "alert_triggered": False,
        "alert_time": None,
        "events": [],
    }

    logger.info("Cash drawer state cleared")

    return (
        jsonify(
            {"status": "cleared", "timestamp": datetime.utcnow().isoformat() + "Z"}
        ),
        200,
    )


@app.route("/cash-drawer/reset", methods=["POST"])
def reset_drawer():
    """
    Reset the cash drawer to a known state (closed).
    """
    global drawer_state, alert_timer

    cancel_alert_timer()
    drawer_state["state"] = DrawerState.CLOSED.value
    drawer_state["open_time"] = None
    drawer_state["close_time"] = datetime.utcnow().isoformat() + "Z"
    drawer_state["last_tender_type"] = None
    drawer_state["transaction_id"] = None
    drawer_state["alert_triggered"] = False
    drawer_state["alert_time"] = None

    emit_event(
        "drawer_reset",
        {
            "reset_time": datetime.utcnow().isoformat() + "Z",
        },
    )

    logger.info("Cash drawer reset")

    return (
        jsonify({"status": "reset", "timestamp": datetime.utcnow().isoformat() + "Z"}),
        200,
    )


# ============================================================
# Testing Helpers
# ============================================================


@app.route("/cash-drawer/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run a test scenario for the cash drawer.

    Expected payload:
    {
        "scenario": "happy_path" | "left_open_alert" | "non_cash_tender"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "happy_path")

    # Reset state first
    reset_drawer()

    if scenario == "happy_path":
        # Cash tender → open → close
        open_response = open_drawer(
            {
                "transaction_id": "test-happy-001",
                "tender_type": "cash",
                "amount": 10.00,
            }
        )
        # Close immediately
        close_response = close_drawer(
            {
                "transaction_id": "test-happy-001",
            }
        )
        return (
            jsonify(
                {
                    "scenario": "happy_path",
                    "open_result": (
                        open_response.get_json()
                        if hasattr(open_response, "get_json")
                        else open_response
                    ),
                    "close_result": (
                        close_response.get_json()
                        if hasattr(close_response, "get_json")
                        else close_response
                    ),
                    "final_state": drawer_state["state"],
                }
            ),
            200,
        )

    elif scenario == "left_open_alert":
        # Cash tender → open → leave open → alert triggers
        open_response = open_drawer(
            {
                "transaction_id": "test-alert-001",
                "tender_type": "cash",
                "amount": 10.00,
            }
        )
        # Don't close — alert will trigger after timeout
        # In test mode, we return immediately with the alert scheduled
        return (
            jsonify(
                {
                    "scenario": "left_open_alert",
                    "open_result": (
                        open_response.get_json()
                        if hasattr(open_response, "get_json")
                        else open_response
                    ),
                    "alert_timeout_seconds": DRAWER_OPEN_ALERT_TIMEOUT,
                    "message": "Drawer left open. Alert will trigger after timeout.",
                    "final_state": drawer_state["state"],
                }
            ),
            200,
        )

    elif scenario == "non_cash_tender":
        # Non-cash tender → drawer does NOT open
        open_response = open_drawer(
            {
                "transaction_id": "test-noncash-001",
                "tender_type": "card",
                "amount": 10.00,
            }
        )
        return (
            jsonify(
                {
                    "scenario": "non_cash_tender",
                    "open_result": (
                        open_response.get_json()
                        if hasattr(open_response, "get_json")
                        else open_response
                    ),
                    "message": "Drawer did NOT open (only cash tender opens drawer)",
                    "final_state": drawer_state["state"],
                }
            ),
            200,
        )

    else:
        abort(400, description=f"Unknown scenario: {scenario}")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5010))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Cash Drawer Simulator starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
