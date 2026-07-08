# simulators/product-profiles/mxp-smart-wall/simulator.py

"""
MxP™ SMART | wall Simulator

Space-constrained, wall-mounted lane profile (reduced peripheral set: no bagging-area scale, compact printer only).

Supports:
  - Wall-mounted form factor
  - Reduced peripheral set (no scale)
  - Compact printer only
  - Handheld scanner
  - Compact PIN pad

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

# Peripheral availability (wall profile = reduced set)
PERIPHERALS = {
    "scanner": {"available": True, "type": "handheld"},
    "printer": {"available": True, "type": "compact", "paper_width": "3in"},
    "scale": {"available": False, "reason": "No bagging-area scale in wall profile"},
    "pin_pad": {"available": True, "type": "compact"},
}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "mxp-smart-wall-simulator"}), 200


@app.route("/mxp-smart-wall/peripherals", methods=["GET"])
def get_peripherals():
    """Get the available peripherals for the wall profile."""
    return (
        jsonify(
            {
                "peripherals": PERIPHERALS,
                "profile": "wall",
                "space_constraints": {
                    "max_depth_cm": 10,
                    "max_width_cm": 30,
                    "mounting": "wall",
                },
            }
        ),
        200,
    )


@app.route("/mxp-smart-wall/scan", methods=["POST"])
def scan_item():
    """
    Simulate scanning an item with the handheld scanner.

    Expected payload:
    {
        "sku": "SKU-1001"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    sku = data.get("sku")
    if not sku:
        abort(400, description="Missing 'sku' field")

    # Verify scanner is available
    if not PERIPHERALS["scanner"]["available"]:
        abort(503, description="Scanner not available in wall profile")

    # Look up item
    price_response = requests.get(f"{PRICING_URL}/price/{sku}", timeout=5)
    if price_response.status_code != 200:
        return jsonify({"status": "error", "sku": sku, "error": "SKU not found"}), 404

    price_data = price_response.json()

    logger.info(f"Wall profile scan: {sku} -> ${price_data.get('price', 0)}")

    return (
        jsonify(
            {
                "status": "scanned",
                "sku": sku,
                "price": price_data.get("price"),
                "name": price_data.get("name"),
                "peripheral": "handheld_scanner",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-smart-wall/print", methods=["POST"])
def print_receipt():
    """
    Simulate printing a receipt with the compact printer.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [{"sku": "SKU-1001", "name": "Milk", "price": 2.99}],
        "total": 10.00
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    # Verify printer is available
    if not PERIPHERALS["printer"]["available"]:
        abort(503, description="Printer not available in wall profile")

    transaction_id = data.get("transaction_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    total = data.get("total", 0.0)

    # Simulate compact printer output (3-inch paper)
    receipt = {
        "type": "compact_receipt",
        "paper_width": "3in",
        "transaction_id": transaction_id,
        "items": items,
        "total": total,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "footer": "Thank you for your purchase!",
    }

    logger.info(f"Wall profile print: {transaction_id} - {len(items)} items")

    return (
        jsonify(
            {
                "status": "printed",
                "receipt": receipt,
                "peripheral": "compact_printer",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-smart-wall/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction through the MxP SMART Wall pipeline.
    NOTE: Scale operations are not available in wall profile.
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})

    if not items:
        abort(400, description="Missing 'items' field")

    # Check for scale operations (should not be used in wall profile)
    if data.get("scale_operation"):
        abort(400, description="Scale operations not available in wall profile")

    logger.info(f"Processing MxP SMART Wall transaction with {len(items)} items")

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

        # Step 2: Apply promotions
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
                "merchant_id": "MXP_SMART_WALL",
                "order_id": f"WALL-{datetime.utcnow().timestamp()}",
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

        # Step 5: Print receipt (compact printer)
        print_response = requests.post(
            f"{app.config.get('BASE_URL', 'http://localhost:5000')}/mxp-smart-wall/print",
            json={
                "transaction_id": payment_data.get("transaction_id"),
                "items": priced_items,
                "total": total,
            },
            timeout=5,
        )

        print_result = {}
        if print_response.status_code == 200:
            print_result = print_response.json()

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "profile": "wall",
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "promotions": promo_data,
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "receipt": print_result.get("receipt"),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"MxP SMART Wall transaction: {result['status']}")
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5003))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
