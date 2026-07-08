# simulators/product-profiles/mxp-smart-wing/simulator.py

"""
MxP™ SMART | wing Simulator

Modular/component-based lane; peripherals are toggled independently (scanner, scale, printer, payment each optional).

Supports:
  - Independent peripheral toggling
  - Validation of peripheral combinations
  - Modular component-based architecture

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

# Peripheral states (enabled by default)
peripheral_states = {"scanner": True, "scale": True, "printer": True, "pin_pad": True}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "mxp-smart-wing-simulator"}), 200


@app.route("/mxp-smart-wing/peripherals", methods=["GET"])
def get_peripherals():
    """Get the current peripheral states."""
    return jsonify({"peripherals": peripheral_states, "profile": "wing"}), 200


@app.route("/mxp-smart-wing/peripheral/toggle", methods=["POST"])
def toggle_peripheral():
    """
    Toggle a specific peripheral on/off.

    Expected payload:
    {
        "peripheral": "scanner",
        "enabled": false
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    peripheral = data.get("peripheral")
    enabled = data.get("enabled")

    if not peripheral:
        abort(400, description="Missing 'peripheral' field")

    if peripheral not in peripheral_states:
        abort(
            400,
            description=f"Unknown peripheral: {peripheral}. Valid: {list(peripheral_states.keys())}",
        )

    if enabled is None:
        abort(400, description="Missing 'enabled' field (must be true/false)")

    old_state = peripheral_states[peripheral]
    peripheral_states[peripheral] = enabled

    logger.info(f"Peripheral toggled: {peripheral} {old_state} -> {enabled}")

    return (
        jsonify(
            {
                "status": "toggled",
                "peripheral": peripheral,
                "enabled": enabled,
                "previous_state": old_state,
                "all_peripherals": peripheral_states,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-smart-wing/peripheral/validate", methods=["POST"])
def validate_combination():
    """
    Validate a peripheral combination against the schema.

    Expected payload:
    {
        "peripherals": {
            "scanner": true,
            "scale": false,
            "printer": true,
            "pin_pad": true
        }
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    combo = data.get("peripherals", {})

    # Validate that all required fields are present
    for peripheral in peripheral_states.keys():
        if peripheral not in combo:
            abort(400, description=f"Missing peripheral: {peripheral}")

    # Validate values are boolean
    for peripheral, state in combo.items():
        if not isinstance(state, bool):
            abort(400, description=f"Invalid value for {peripheral}: must be boolean")

    # At least one peripheral must be enabled (minimum viable configuration)
    if not any(combo.values()):
        return (
            jsonify(
                {
                    "valid": False,
                    "reason": "At least one peripheral must be enabled",
                    "peripherals": combo,
                }
            ),
            400,
        )

    logger.info(f"Combination validated: {combo}")

    return (
        jsonify(
            {
                "valid": True,
                "peripherals": combo,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-smart-wing/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction with the current peripheral configuration.

    Expected payload:
    {
        "items": [{"sku": "SKU-1001", "quantity": 2}],
        "region": "CA",
        "payment": {"card_number": "4111111111111111"}
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})

    if not items:
        abort(400, description="Missing 'items' field")

    # Check that required peripherals are available
    if not peripheral_states.get("scanner", False):
        # If scanner is disabled, items must be provided as a list (manual entry)
        # This is allowed as long as items are provided
        pass

    if not peripheral_states.get("pin_pad", False):
        # If PIN pad is disabled, payment must be via other method (mock)
        # For testing, we allow card payment without PIN pad
        pass

    logger.info(f"Processing MxP SMART Wing transaction with {len(items)} items")
    logger.info(f"  Scanner: {'✅' if peripheral_states['scanner'] else '❌'}")
    logger.info(f"  Scale: {'✅' if peripheral_states['scale'] else '❌'}")
    logger.info(f"  Printer: {'✅' if peripheral_states['printer'] else '❌'}")
    logger.info(f"  PIN Pad: {'✅' if peripheral_states['pin_pad'] else '❌'}")

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

        # Step 2: Scale check (if enabled)
        if peripheral_states.get("scale", False):
            # Mock scale validation (always passes for wing profile)
            logger.info("  Scale check: PASSED")

        # Step 3: Apply promotions
        promo_response = requests.post(
            f"{PROMOTIONS_URL}/promotions/cart", json={"items": items}, timeout=5
        )
        promo_data = {}
        if promo_response.status_code == 200:
            promo_data = promo_response.json()

        # Step 4: Calculate tax
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

        # Step 5: Authorize payment
        card_number = payment.get("card_number", "4111111111111111")
        payment_response = requests.post(
            f"{PAYMENT_URL}/payment/authorize",
            json={
                "amount": total,
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": payment.get("expiry", "12/25"),
                "cvv": payment.get("cvv", "123"),
                "merchant_id": "MXP_SMART_WING",
                "order_id": f"WING-{datetime.utcnow().timestamp()}",
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

        # Step 6: Print receipt (if printer enabled)
        receipt_status = "not_printed"
        if peripheral_states.get("printer", False):
            # Mock print
            receipt_status = "printed"
            logger.info("  Receipt printed")
        else:
            logger.info("  Receipt skipped (printer disabled)")

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "profile": "wing",
            "peripherals": peripheral_states.copy(),
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "promotions": promo_data,
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "receipt": receipt_status,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"MxP SMART Wing transaction: {result['status']}")
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5004))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
