# hardware-emulation/card-reader/simulator.py

"""
Card Reader Peripheral Simulator (B2)

Models the physical EMV dip/tap/swipe + PIN-entry device protocol.
Distinct from the backend payment-gateway service it routes to.

Capabilities:
  - EMV dip (insert card)
  - EMV tap (contactless)
  - EMV swipe (magnetic stripe)
  - PIN entry (4-6 digits)
  - Tender event emission (approved/declined/PIN-required)

This simulator emits tender events that a test consumes.
It does NOT process payments — it delegates to the mock-payment-gateway.
"""

import os
import json
import logging
import random
import time
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
PAYMENT_GATEWAY_URL = os.environ.get(
    "PAYMENT_GATEWAY_URL", "http://payment-gateway:8084"
)

# Test card sentinels (matches mock-payment-gateway)
TEST_CARDS = {
    "4111111111111111": {"status": "approved", "name": "Approved Test Card"},
    "4111111111110000": {"status": "declined", "name": "Declined Test Card"},
    "4111111111112222": {"status": "approved", "name": "Approved Card 2"},
    "4111111111113333": {"status": "pin_required", "name": "PIN Required Card"},
    "4111111111114444": {"status": "declined", "name": "Declined Card 2"},
}

# PIN codes for PIN-required cards
PIN_CODES = {
    "4111111111113333": "1234",
    "4111111111115555": "5678",
}


# Transaction state
class TransactionState(Enum):
    IDLE = "idle"
    CARD_INSERTED = "card_inserted"
    PIN_ENTRY = "pin_entry"
    PROCESSING = "processing"
    COMPLETED = "completed"
    DECLINED = "declined"
    CANCELLED = "cancelled"


# Card reader session state
card_reader_state = {
    "state": TransactionState.IDLE.value,
    "card_number": None,
    "pan_masked": None,
    "entry_method": None,  # dip, tap, swipe
    "pin_attempts": 0,
    "max_pin_attempts": 3,
    "current_transaction_id": None,
    "tender_events": [],
}

# ============================================================
# Helper Functions
# ============================================================


def mask_pan(pan: str) -> str:
    """Mask a PAN for logging."""
    if not pan or len(pan) < 4:
        return "XXXX"
    return f"XXXX...{pan[-4:]}"


def generate_transaction_id() -> str:
    """Generate a unique transaction ID."""
    return f"CRD-{int(time.time())}-{random.randint(1000, 9999)}"


def is_pin_required(card_number: str) -> bool:
    """Check if a card requires PIN entry."""
    return card_number in PIN_CODES


def is_valid_pin(card_number: str, pin: str) -> bool:
    """Validate PIN for a card."""
    return PIN_CODES.get(card_number) == pin


def emit_tender_event(event_type: str, data: Dict[str, Any]):
    """Store a tender event for later retrieval."""
    event = {
        "event_type": event_type,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "data": data,
        "event_id": len(card_reader_state["tender_events"]) + 1,
    }
    card_reader_state["tender_events"].append(event)
    logger.info(f"Tender event emitted: {event_type}")
    return event


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
                "service": "card-reader-simulator",
                "state": card_reader_state["state"],
            }
        ),
        200,
    )


@app.route("/card-reader/state", methods=["GET"])
def get_state():
    """Get the current card reader state."""
    return (
        jsonify(
            {
                "state": card_reader_state["state"],
                "card_reader_ready": card_reader_state["state"]
                == TransactionState.IDLE.value,
                "card_inserted": card_reader_state["card_number"] is not None,
                "pan_masked": card_reader_state.get("pan_masked"),
                "entry_method": card_reader_state.get("entry_method"),
                "tender_events_count": len(card_reader_state["tender_events"]),
            }
        ),
        200,
    )


@app.route("/card-reader/insert", methods=["POST"])
def insert_card():
    """
    Simulate inserting a card (EMV dip).

    Expected payload:
    {
        "card_number": "4111111111111111",
        "entry_method": "dip"  # dip, tap, swipe
    }
    """
    global card_reader_state

    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    card_number = data.get("card_number")
    entry_method = data.get("entry_method", "dip")

    if not card_number:
        abort(400, description="Missing 'card_number' field")

    if card_reader_state["state"] not in [
        TransactionState.IDLE.value,
        TransactionState.CANCELLED.value,
    ]:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": f"Card reader is busy (state: {card_reader_state['state']})",
                }
            ),
            409,
        )

    # Validate card
    if card_number not in TEST_CARDS:
        # Unknown card — treat as declined
        card_info = {"status": "declined", "name": "Unknown Card"}
    else:
        card_info = TEST_CARDS[card_number]

    card_reader_state["state"] = TransactionState.CARD_INSERTED.value
    card_reader_state["card_number"] = card_number
    card_reader_state["pan_masked"] = mask_pan(card_number)
    card_reader_state["entry_method"] = entry_method
    card_reader_state["current_transaction_id"] = generate_transaction_id()
    card_reader_state["pin_attempts"] = 0

    logger.info(f"Card inserted: {card_reader_state['pan_masked']} ({entry_method})")

    # Check if PIN is required
    pin_required = is_pin_required(card_number)

    if pin_required:
        card_reader_state["state"] = TransactionState.PIN_ENTRY.value
        emit_tender_event(
            "pin_required",
            {
                "pan_masked": card_reader_state["pan_masked"],
                "transaction_id": card_reader_state["current_transaction_id"],
            },
        )

    return (
        jsonify(
            {
                "status": "accepted",
                "transaction_id": card_reader_state["current_transaction_id"],
                "pan_masked": card_reader_state["pan_masked"],
                "entry_method": entry_method,
                "pin_required": pin_required,
                "card_name": card_info.get("name", "Unknown Card"),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/card-reader/pin", methods=["POST"])
def enter_pin():
    """
    Enter PIN for a card.

    Expected payload:
    {
        "pin": "1234"
    }
    """
    global card_reader_state

    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    pin = data.get("pin")

    if not pin:
        abort(400, description="Missing 'pin' field")

    if card_reader_state["state"] != TransactionState.PIN_ENTRY.value:
        return (
            jsonify(
                {
                    "status": "error",
                    "error": f"PIN entry not expected (state: {card_reader_state['state']})",
                }
            ),
            409,
        )

    card_number = card_reader_state.get("card_number")
    if not card_number:
        return jsonify({"status": "error", "error": "No card inserted"}), 409

    # Validate PIN
    if is_valid_pin(card_number, pin):
        card_reader_state["state"] = TransactionState.PROCESSING.value
        emit_tender_event(
            "pin_accepted",
            {
                "pan_masked": card_reader_state["pan_masked"],
                "transaction_id": card_reader_state["current_transaction_id"],
            },
        )

        # Process the payment
        return process_payment(card_number)
    else:
        card_reader_state["pin_attempts"] += 1
        remaining = (
            card_reader_state["max_pin_attempts"] - card_reader_state["pin_attempts"]
        )

        if card_reader_state["pin_attempts"] >= card_reader_state["max_pin_attempts"]:
            # Too many attempts — decline and eject
            card_reader_state["state"] = TransactionState.DECLINED.value
            emit_tender_event(
                "pin_blocked",
                {
                    "pan_masked": card_reader_state["pan_masked"],
                    "transaction_id": card_reader_state["current_transaction_id"],
                    "attempts": card_reader_state["pin_attempts"],
                },
            )

            # Eject the card
            eject_card()

            return (
                jsonify(
                    {
                        "status": "declined",
                        "reason": "PIN blocked - too many attempts",
                        "attempts": card_reader_state["pin_attempts"],
                        "timestamp": datetime.utcnow().isoformat() + "Z",
                    }
                ),
                402,
            )

        return (
            jsonify(
                {
                    "status": "pin_required",
                    "attempts": card_reader_state["pin_attempts"],
                    "remaining_attempts": remaining,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            401,
        )


@app.route("/card-reader/process", methods=["POST"])
def process_payment(card_number: Optional[str] = None):
    """
    Process the payment by routing to the mock-payment-gateway.
    Called automatically after successful PIN entry.
    """
    global card_reader_state

    if card_number is None:
        data = request.get_json() or {}
        card_number = data.get("card_number") or card_reader_state.get("card_number")

    if not card_number:
        return jsonify({"status": "error", "error": "No card number available"}), 400

    transaction_id = card_reader_state.get("current_transaction_id")
    pan_masked = card_reader_state.get("pan_masked")

    # Check if card is valid
    card_info = TEST_CARDS.get(
        card_number, {"status": "declined", "name": "Unknown Card"}
    )

    if card_info["status"] == "declined":
        card_reader_state["state"] = TransactionState.DECLINED.value
        emit_tender_event(
            "declined",
            {
                "pan_masked": pan_masked,
                "transaction_id": transaction_id,
                "reason": "Card declined",
            },
        )
        eject_card()
        return (
            jsonify(
                {
                    "status": "declined",
                    "reason": "Card declined by issuer",
                    "transaction_id": transaction_id,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            402,
        )

    # Route to payment gateway
    import requests

    try:
        payment_response = requests.post(
            f"{PAYMENT_GATEWAY_URL}/payment/authorize",
            json={
                "amount": 10.00,  # Default amount, can be overridden
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": "12/25",
                "cvv": "123",
                "merchant_id": "CARD_READER_TEST",
                "order_id": transaction_id,
            },
            timeout=5,
        )

        if payment_response.status_code == 200:
            data = payment_response.json()
            card_reader_state["state"] = TransactionState.COMPLETED.value
            emit_tender_event(
                "approved",
                {
                    "pan_masked": pan_masked,
                    "transaction_id": transaction_id,
                    "auth_code": data.get("auth_code"),
                },
            )
            eject_card()
            return (
                jsonify(
                    {
                        "status": "approved",
                        "transaction_id": transaction_id,
                        "auth_code": data.get("auth_code"),
                        "timestamp": datetime.utcnow().isoformat() + "Z",
                    }
                ),
                200,
            )
        else:
            card_reader_state["state"] = TransactionState.DECLINED.value
            emit_tender_event(
                "declined",
                {
                    "pan_masked": pan_masked,
                    "transaction_id": transaction_id,
                    "reason": "Payment gateway declined",
                },
            )
            eject_card()
            return (
                jsonify(
                    {
                        "status": "declined",
                        "reason": "Payment gateway declined",
                        "transaction_id": transaction_id,
                        "timestamp": datetime.utcnow().isoformat() + "Z",
                    }
                ),
                402,
            )

    except Exception as e:
        logger.error(f"Payment processing error: {e}")
        card_reader_state["state"] = TransactionState.DECLINED.value
        eject_card()
        return (
            jsonify(
                {
                    "status": "error",
                    "error": str(e),
                    "transaction_id": transaction_id,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            500,
        )


@app.route("/card-reader/eject", methods=["POST"])
def eject_card():
    """Eject the card from the reader."""
    global card_reader_state

    card_reader_state["state"] = TransactionState.IDLE.value
    card_reader_state["card_number"] = None
    card_reader_state["pan_masked"] = None
    card_reader_state["pin_attempts"] = 0

    logger.info("Card ejected")

    return (
        jsonify(
            {"status": "ejected", "timestamp": datetime.utcnow().isoformat() + "Z"}
        ),
        200,
    )


@app.route("/card-reader/events", methods=["GET"])
def get_tender_events():
    """Get all tender events."""
    return (
        jsonify(
            {
                "events": card_reader_state["tender_events"],
                "count": len(card_reader_state["tender_events"]),
            }
        ),
        200,
    )


@app.route("/card-reader/clear", methods=["POST"])
def clear_state():
    """Clear the card reader state and reset."""
    global card_reader_state

    # Keep the event history but reset state
    card_reader_state["state"] = TransactionState.IDLE.value
    card_reader_state["card_number"] = None
    card_reader_state["pan_masked"] = None
    card_reader_state["entry_method"] = None
    card_reader_state["pin_attempts"] = 0
    card_reader_state["current_transaction_id"] = None

    logger.info("Card reader state cleared")

    return (
        jsonify(
            {"status": "cleared", "timestamp": datetime.utcnow().isoformat() + "Z"}
        ),
        200,
    )


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5009))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Card Reader Simulator starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
