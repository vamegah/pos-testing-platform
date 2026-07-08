# simulators/product-profiles/elera/simulator.py

"""
ELERA® Platform Simulator

Thin adapter that sits in front of Phase 1 services and routes transactions
through the ELERA unified-commerce capability profile.

Supports:
  - POS mode: Cashier-assisted checkout
  - Self-Service mode: Customer self-checkout
  - Produce Recognition hook: Simulated computer vision
  - Security Suite hook: Security event logging

All data is mocked — no real payment, no real customer data.
"""

import os
import json
import logging
import requests
from typing import Dict, Any, Optional, List
from datetime import datetime
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Service URLs (from environment or defaults)
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")
PAYMENT_URL = os.environ.get("PAYMENT_GATEWAY_URL", "http://payment-gateway:8084")

# Mock produce database
MOCK_PRODUCE = {
    "apple": {"sku": "PRODUCE-001", "price": 0.99, "name": "Apple"},
    "banana": {"sku": "PRODUCE-002", "price": 0.49, "name": "Banana"},
    "orange": {"sku": "PRODUCE-003", "price": 0.79, "name": "Orange"},
    "lemon": {"sku": "PRODUCE-004", "price": 0.89, "name": "Lemon"},
    "lime": {"sku": "PRODUCE-005", "price": 0.89, "name": "Lime"},
    "avocado": {"sku": "PRODUCE-006", "price": 1.29, "name": "Avocado"},
}

# Mock security events log
security_events = []


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "elera-simulator"}), 200


@app.route("/elera/mode", methods=["GET", "POST"])
def mode_management():
    """Get or set the current mode (POS or Self-Service)."""
    global current_mode

    if request.method == "GET":
        return jsonify({"mode": current_mode}), 200

    data = request.get_json()
    if not data or "mode" not in data:
        abort(400, description="Missing 'mode' field")

    mode = data.get("mode")
    if mode not in ["pos", "self_service"]:
        abort(400, description=f"Invalid mode: {mode}. Must be 'pos' or 'self_service'")

    current_mode = mode
    logger.info(f"Mode switched to: {mode}")
    return jsonify({"mode": mode, "status": "updated"}), 200


@app.route("/elera/produce/recognize", methods=["POST"])
def recognize_produce():
    """
    Simulated produce recognition via computer vision.

    Accepts an image name (mock) and returns the recognized produce.
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    image_hint = data.get("image_hint", "")
    if not image_hint:
        abort(400, description="Missing 'image_hint' field")

    # Mock recognition logic
    recognized = None
    confidence = 0.0

    # Simple mock: use hint to lookup produce
    hint_lower = image_hint.lower()
    for name, produce in MOCK_PRODUCE.items():
        if name in hint_lower:
            recognized = produce
            confidence = 0.95
            break

    if not recognized:
        # Default to apple with lower confidence
        recognized = MOCK_PRODUCE.get("apple")
        confidence = 0.60

    logger.info(
        f"Produce recognition: {image_hint} -> {recognized.get('name')} (confidence: {confidence})"
    )

    return (
        jsonify(
            {
                "recognized": recognized,
                "confidence": confidence,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/elera/security/event", methods=["POST"])
def security_event():
    """
    Log a security event (Security Suite hook).
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    event_type = data.get("event_type", "unknown")
    event_data = data.get("event_data", {})

    security_events.append(
        {
            "event_type": event_type,
            "event_data": event_data,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )

    logger.info(f"Security event logged: {event_type}")
    return (
        jsonify(
            {
                "status": "logged",
                "event_id": len(security_events),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/elera/security/events", methods=["GET"])
def get_security_events():
    """Get all security events (for testing)."""
    return jsonify({"events": security_events, "count": len(security_events)}), 200


@app.route("/elera/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction through the ELERA pipeline.

    Expected payload:
    {
        "mode": "pos" | "self_service",
        "items": [{"sku": "SKU-1001", "quantity": 2}],
        "region": "CA",
        "payment": {
            "card_number": "4111111111111111"
        }
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    mode = data.get("mode", current_mode)
    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})

    if not items:
        abort(400, description="Missing 'items' field")

    logger.info(f"Processing ELERA transaction in {mode} mode with {len(items)} items")

    try:
        # Step 1: Get prices for all items
        subtotal = 0.0
        priced_items = []
        for item in items:
            sku = item.get("sku")
            quantity = item.get("quantity", 1)

            price_response = requests.get(f"{PRICING_URL}/price/{sku}", timeout=5)
            if price_response.status_code != 200:
                return (
                    jsonify(
                        {
                            "status": "failed",
                            "step": "pricing",
                            "sku": sku,
                            "error": "Price lookup failed",
                        }
                    ),
                    400,
                )

            price_data = price_response.json()
            item_price = price_data.get("price", 0)
            subtotal += item_price * quantity
            priced_items.append(
                {
                    "sku": sku,
                    "quantity": quantity,
                    "price": item_price,
                    "total": item_price * quantity,
                }
            )

        # Step 2: Apply promotions (if any)
        promo_response = requests.post(
            f"{PROMOTIONS_URL}/promotions/cart", json={"items": items}, timeout=5
        )
        promo_data = {}
        if promo_response.status_code == 200:
            promo_data = promo_response.json()

        # Step 3: Calculate tax
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

        # Step 4: Authorize payment
        card_number = payment.get("card_number", "4111111111111111")
        payment_response = requests.post(
            f"{PAYMENT_URL}/payment/authorize",
            json={
                "amount": total,
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": payment.get("expiry", "12/25"),
                "cvv": payment.get("cvv", "123"),
                "merchant_id": "TEST_MERCHANT_ELERA",
                "order_id": f"ELERA-{datetime.utcnow().timestamp()}",
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

        # Step 5: Log security event (for self-service mode)
        if mode == "self_service":
            requests.post(
                f"{app.config.get('BASE_URL', 'http://localhost:5000')}/elera/security/event",
                json={
                    "event_type": "transaction_completed",
                    "event_data": {
                        "mode": mode,
                        "item_count": len(items),
                        "total": total,
                    },
                },
                timeout=2,
            )

        result = {
            "status": "completed",
            "mode": mode,
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "promotions": promo_data,
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"Transaction completed: {result['payment']['status']}")
        return jsonify(result), 200

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


# Current mode state (default: pos)
current_mode = "pos"


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
