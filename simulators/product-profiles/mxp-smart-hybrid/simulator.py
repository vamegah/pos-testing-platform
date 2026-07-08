# simulators/product-profiles/mxp-smart-hybrid/simulator.py

"""
MxP™ SMART | hybrid Simulator

Pedestal/kiosk profile with cashier↔self-service mode toggle (models the "rotate 180°" assisted mode).

Supports:
  - Self-Service mode: Customer self-checkout
  - Assisted mode: Cashier-assisted checkout (rotated 180°)
  - Mode toggle: Simulates physical rotation
  - Mode switch events: Logging for auditing

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

# Service URLs
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")
PAYMENT_URL = os.environ.get("PAYMENT_GATEWAY_URL", "http://payment-gateway:8084")

# Mode state
current_mode = "self_service"  # or "assisted"
mode_history = []

# Mode-specific endpoint availability
ENDPOINTS = {
    "self_service": {
        "scan": True,
        "payment": True,
        "print": True,
        "scale": True,
        "override": False,
    },
    "assisted": {
        "scan": True,
        "payment": True,
        "print": True,
        "scale": True,
        "override": True,
    },
}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "mxp-smart-hybrid-simulator"}), 200


@app.route("/mxp-smart-hybrid/mode", methods=["GET"])
def get_mode():
    """Get the current mode."""
    return (
        jsonify(
            {
                "mode": current_mode,
                "available_modes": ["self_service", "assisted"],
                "endpoints": ENDPOINTS.get(current_mode, {}),
                "rotation_degrees": 180 if current_mode == "assisted" else 0,
            }
        ),
        200,
    )


@app.route("/mxp-smart-hybrid/toggle", methods=["POST"])
def toggle_mode():
    """
    Toggle between self-service and assisted modes.
    Simulates rotating the display 180°.
    """
    global current_mode

    data = request.get_json() or {}
    target_mode = data.get("mode")

    if target_mode:
        if target_mode not in ["self_service", "assisted"]:
            abort(
                400,
                description=f"Invalid mode: {target_mode}. Must be 'self_service' or 'assisted'",
            )

        old_mode = current_mode
        current_mode = target_mode

        # Log mode change event
        mode_history.append(
            {
                "from": old_mode,
                "to": current_mode,
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "trigger": data.get("trigger", "api"),
            }
        )

        logger.info(f"Mode toggled: {old_mode} -> {current_mode}")

        return (
            jsonify(
                {
                    "status": "switched",
                    "previous_mode": old_mode,
                    "current_mode": current_mode,
                    "rotation_degrees": 180 if current_mode == "assisted" else 0,
                    "available_endpoints": ENDPOINTS.get(current_mode, {}),
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )
    else:
        # Toggle to the other mode
        old_mode = current_mode
        current_mode = "assisted" if old_mode == "self_service" else "self_service"

        mode_history.append(
            {
                "from": old_mode,
                "to": current_mode,
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "trigger": "toggle",
            }
        )

        logger.info(f"Mode toggled: {old_mode} -> {current_mode}")

        return (
            jsonify(
                {
                    "status": "toggled",
                    "previous_mode": old_mode,
                    "current_mode": current_mode,
                    "rotation_degrees": 180 if current_mode == "assisted" else 0,
                    "available_endpoints": ENDPOINTS.get(current_mode, {}),
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )


@app.route("/mxp-smart-hybrid/mode/event", methods=["GET"])
def get_mode_history():
    """Get mode change history."""
    return (
        jsonify(
            {
                "history": mode_history,
                "count": len(mode_history),
                "current_mode": current_mode,
            }
        ),
        200,
    )


@app.route("/mxp-smart-hybrid/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction through the MxP SMART Hybrid pipeline.
    Behavior differs based on current mode.
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})
    override = data.get("override", False)

    if not items:
        abort(400, description="Missing 'items' field")

    # Check if override is allowed in current mode
    if override and not ENDPOINTS.get(current_mode, {}).get("override", False):
        abort(403, description="Override not allowed in current mode")

    logger.info(
        f"Processing MxP SMART Hybrid transaction in {current_mode} mode with {len(items)} items"
    )

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

        # Step 4: Payment
        card_number = payment.get("card_number", "4111111111111111")
        payment_response = requests.post(
            f"{PAYMENT_URL}/payment/authorize",
            json={
                "amount": total,
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": payment.get("expiry", "12/25"),
                "cvv": payment.get("cvv", "123"),
                "merchant_id": f"MXP_SMART_HYBRID_{current_mode.upper()}",
                "order_id": f"SMART-{datetime.utcnow().timestamp()}",
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

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "mode": current_mode,
            "rotation_degrees": 180 if current_mode == "assisted" else 0,
            "override_applied": override,
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

        logger.info(
            f"MxP SMART Hybrid transaction: {result['status']} in {current_mode} mode"
        )
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5002))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
