# simulators/product-profiles/self-checkout-system-7/simulator.py

"""
Self Checkout System 7 Simulator

Full-basket, attended/unattended self-service lane (scanner + scale + bagging + printer + payment + transaction-awareness-light event).

Supports:
  - Full-basket transactions
  - Bagging area weight verification
  - Attended mode with override
  - Unattended mode
  - Transaction awareness light events
  - Payment (card/cash/gift card)

All data is mocked — no real payment, no real customer data.
"""

import os
import json
import logging
import requests
import random
from typing import Dict, Any, Optional, List
from datetime import datetime
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Service URLs
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")
PAYMENT_URL = os.environ.get("PAYMENT_GATEWAY_URL", "http://payment-gateway:8084")

# Mock item weights (in kg)
MOCK_ITEM_WEIGHTS = {
    "SKU-1001": 3.78,
    "SKU-1002": 0.45,
    "SKU-1003": 0.68,
    "SKU-1004": 0.45,
    "SKU-1005": 0.18,
    "SKU-1006": 1.89,
    "SKU-1007": 0.23,
    "SKU-1008": 0.45,
    "SKU-1009": 0.91,
    "SKU-1010": 0.34,
}

# Session storage
sessions = {}
bagging_sessions = {}
awareness_events = []

# Mode state
current_mode = "unattended"


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return (
        jsonify({"status": "healthy", "service": "self-checkout-system-7-simulator"}),
        200,
    )


@app.route("/self-checkout/mode", methods=["GET", "POST"])
def mode_management():
    """Get or set the current mode (unattended/attended)."""
    global current_mode

    if request.method == "GET":
        return (
            jsonify(
                {"mode": current_mode, "available_modes": ["unattended", "attended"]}
            ),
            200,
        )

    data = request.get_json()
    if not data or "mode" not in data:
        abort(400, description="Missing 'mode' field")

    mode = data.get("mode")
    if mode not in ["unattended", "attended"]:
        abort(
            400, description=f"Invalid mode: {mode}. Must be 'unattended' or 'attended'"
        )

    current_mode = mode
    logger.info(f"Mode switched to: {mode}")
    return jsonify({"mode": mode, "status": "updated"}), 200


@app.route("/self-checkout/transaction/start", methods=["POST"])
def start_transaction():
    """
    Start a new transaction session.

    Expected payload:
    {
        "session_id": "txn-12345"  # optional
    }
    """
    data = request.get_json() or {}
    session_id = data.get("session_id", f"txn-{datetime.utcnow().timestamp()}")

    sessions[session_id] = {
        "items": [],
        "bagging_verified": False,
        "started_at": datetime.utcnow().isoformat() + "Z",
        "mode": current_mode,
        "status": "active",
    }

    logger.info(f"Transaction started: {session_id}")

    return (
        jsonify(
            {
                "session_id": session_id,
                "status": "started",
                "mode": current_mode,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/self-checkout/scan", methods=["POST"])
def scan_item():
    """
    Scan an item into the transaction.

    Expected payload:
    {
        "session_id": "txn-12345",
        "sku": "SKU-1001",
        "quantity": 1
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id")
    sku = data.get("sku")
    quantity = data.get("quantity", 1)

    if not session_id:
        abort(400, description="Missing 'session_id' field")
    if not sku:
        abort(400, description="Missing 'sku' field")

    if session_id not in sessions:
        abort(404, description=f"Transaction {session_id} not found")

    # Look up item
    price_response = requests.get(f"{PRICING_URL}/price/{sku}", timeout=5)
    if price_response.status_code != 200:
        return jsonify({"status": "error", "sku": sku, "error": "SKU not found"}), 404

    price_data = price_response.json()

    # Add item to session
    sessions[session_id]["items"].append(
        {
            "sku": sku,
            "quantity": quantity,
            "price": price_data.get("price", 0),
            "name": price_data.get("name", sku),
            "weight_kg": MOCK_ITEM_WEIGHTS.get(sku, 0.5),
        }
    )

    logger.info(f"Item scanned: {sku} x{quantity} in {session_id}")

    return (
        jsonify(
            {
                "status": "scanned",
                "session_id": session_id,
                "sku": sku,
                "quantity": quantity,
                "price": price_data.get("price"),
                "name": price_data.get("name"),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/self-checkout/bagging/verify", methods=["POST"])
def verify_bagging():
    """
    Verify bagging area weight.

    Expected payload:
    {
        "session_id": "txn-12345",
        "measured_weight_kg": 3.78
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id")
    measured_weight = data.get("measured_weight_kg")

    if not session_id:
        abort(400, description="Missing 'session_id' field")
    if measured_weight is None:
        abort(400, description="Missing 'measured_weight_kg' field")

    if session_id not in sessions:
        abort(404, description=f"Transaction {session_id} not found")

    session = sessions[session_id]
    items = session.get("items", [])

    # Calculate expected weight
    expected_weight = sum(
        item.get("weight_kg", 0) * item.get("quantity", 1) for item in items
    )

    # Check with tolerance (±5%)
    tolerance = max(0.05, expected_weight * 0.05)
    matches = abs(measured_weight - expected_weight) <= tolerance

    session["bagging_verified"] = matches
    session["bagging_measured_weight"] = measured_weight
    session["bagging_expected_weight"] = expected_weight

    logger.info(
        f"Bagging verify: {session_id} -> {measured_weight}kg (expected: {expected_weight}kg, matches: {matches})"
    )

    return jsonify(
        {
            "status": "verified" if matches else "mismatch",
            "session_id": session_id,
            "measured_weight_kg": measured_weight,
            "expected_weight_kg": expected_weight,
            "matches": matches,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    ), (200 if matches else 400)


@app.route("/self-checkout/attended/override", methods=["POST"])
def attended_override():
    """
    Attendant override for assistance (only in attended mode).

    Expected payload:
    {
        "session_id": "txn-12345",
        "action": "approve_weight_mismatch" | "approve_age_restricted" | "reset"
    }
    """
    if current_mode != "attended":
        abort(403, description="Attended override only available in attended mode")

    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id")
    action = data.get("action")

    if not session_id:
        abort(400, description="Missing 'session_id' field")
    if not action:
        abort(400, description="Missing 'action' field")

    if session_id not in sessions:
        abort(404, description=f"Transaction {session_id} not found")

    allowed_actions = ["approve_weight_mismatch", "approve_age_restricted", "reset"]
    if action not in allowed_actions:
        abort(400, description=f"Invalid action: {action}. Allowed: {allowed_actions}")

    session = sessions[session_id]
    session["override_action"] = action
    session["override_timestamp"] = datetime.utcnow().isoformat() + "Z"

    logger.info(f"Attended override: {session_id} -> {action}")

    return (
        jsonify(
            {
                "status": "overridden",
                "session_id": session_id,
                "action": action,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/self-checkout/awareness/event", methods=["POST"])
def awareness_event():
    """
    Transaction awareness light event.

    Expected payload:
    {
        "session_id": "txn-12345",
        "event_type": "started" | "scanning" | "bagging" | "payment" | "completed" | "assistance_needed"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id")
    event_type = data.get("event_type")

    if not session_id:
        abort(400, description="Missing 'session_id' field")
    if not event_type:
        abort(400, description="Missing 'event_type' field")

    allowed_events = [
        "started",
        "scanning",
        "bagging",
        "payment",
        "completed",
        "assistance_needed",
    ]
    if event_type not in allowed_events:
        abort(
            400,
            description=f"Invalid event_type: {event_type}. Allowed: {allowed_events}",
        )

    event = {
        "session_id": session_id,
        "event_type": event_type,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    awareness_events.append(event)

    logger.info(f"Awareness event: {session_id} -> {event_type}")

    return (
        jsonify(
            {"status": "logged", "event_id": len(awareness_events), "event": event}
        ),
        200,
    )


@app.route("/self-checkout/awareness/events", methods=["GET"])
def get_awareness_events():
    """Get all awareness events."""
    return jsonify({"events": awareness_events, "count": len(awareness_events)}), 200


@app.route("/self-checkout/transaction/complete", methods=["POST"])
def complete_transaction():
    """
    Complete a transaction.

    Expected payload:
    {
        "session_id": "txn-12345",
        "region": "CA",
        "payment": {"card_number": "4111111111111111"}
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id")
    region = data.get("region", "CA")
    payment = data.get("payment", {})

    if not session_id:
        abort(400, description="Missing 'session_id' field")

    if session_id not in sessions:
        abort(404, description=f"Transaction {session_id} not found")

    session = sessions[session_id]
    items = session.get("items", [])

    if not items:
        abort(400, description="No items in transaction")

    # Check bagging verification (unless overridden in attended mode)
    if not session.get("bagging_verified", False) and current_mode == "unattended":
        abort(400, description="Bagging verification required in unattended mode")

    # Log awareness event: payment
    awareness_event({"session_id": session_id, "event_type": "payment"})

    try:
        # Calculate subtotal
        subtotal = sum(item.get("price", 0) * item.get("quantity", 1) for item in items)

        # Calculate tax
        tax_response = requests.post(
            f"{TAX_URL}/tax", json={"subtotal": subtotal, "region": region}, timeout=5
        )
        if tax_response.status_code != 200:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "tax",
                        "error": "Tax calculation failed",
                    }
                ),
                400,
            )

        tax_data = tax_response.json()
        tax_amount = tax_data.get("tax_amount", 0)
        total = tax_data.get("total", subtotal)

        # Authorize payment
        card_number = payment.get("card_number", "4111111111111111")
        payment_response = requests.post(
            f"{PAYMENT_URL}/payment/authorize",
            json={
                "amount": total,
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": payment.get("expiry", "12/25"),
                "cvv": payment.get("cvv", "123"),
                "merchant_id": "SELF_CHECKOUT_7",
                "order_id": session_id,
            },
            timeout=5,
        )

        if payment_response.status_code not in [200, 402]:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "payment",
                        "error": "Payment authorization failed",
                    }
                ),
                400,
            )

        payment_data = payment_response.json()

        # Log awareness event: completed
        awareness_event({"session_id": session_id, "event_type": "completed"})

        session["status"] = "completed"
        session["completed_at"] = datetime.utcnow().isoformat() + "Z"

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "session_id": session_id,
            "mode": current_mode,
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": items,
            "bagging_verified": session.get("bagging_verified", False),
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(
            f"Self Checkout System 7 transaction {session_id}: {result['status']}"
        )
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5005))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
