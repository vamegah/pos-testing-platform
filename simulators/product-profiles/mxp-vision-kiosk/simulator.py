# simulators/product-profiles/mxp-vision-kiosk/simulator.py

"""
MxP™ Vision Kiosk Simulator

Mocked computer-vision bulk scan, weight-scale cross-check, and NFC/biometric payment.

Supports:
  - Bulk scan: Recognizes a list of test SKUs from a mock vision scan
  - Weight-scale cross-check: Verifies item weights match expected values
  - NFC payment: Mock NFC tap-to-pay
  - Biometric payment: Mock fingerprint/face payment authorization

All data is mocked — no real payment, no real biometrics.
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

# Mock item database for vision recognition
MOCK_ITEMS = {
    "SKU-1001": {"name": "Milk (1 gal)", "expected_weight_kg": 3.78, "price": 2.99},
    "SKU-1002": {"name": "Bread (white)", "expected_weight_kg": 0.45, "price": 1.49},
    "SKU-1003": {"name": "Eggs (dozen)", "expected_weight_kg": 0.68, "price": 3.99},
    "SKU-1004": {
        "name": "Chicken Breast (lb)",
        "expected_weight_kg": 0.45,
        "price": 4.50,
    },
    "SKU-1005": {"name": "Apple (each)", "expected_weight_kg": 0.18, "price": 0.99},
    "SKU-1006": {
        "name": "Orange Juice (64oz)",
        "expected_weight_kg": 1.89,
        "price": 1.99,
    },
    "SKU-1007": {
        "name": "Cheese (cheddar, 8oz)",
        "expected_weight_kg": 0.23,
        "price": 5.49,
    },
    "SKU-1008": {
        "name": "Butter (salted, 1lb)",
        "expected_weight_kg": 0.45,
        "price": 2.29,
    },
    "SKU-1009": {
        "name": "Cereal (family size)",
        "expected_weight_kg": 0.91,
        "price": 3.29,
    },
    "SKU-1010": {
        "name": "Coffee (ground, 12oz)",
        "expected_weight_kg": 0.34,
        "price": 7.99,
    },
}

# Mock biometric users
MOCK_BIOMETRIC_USERS = {
    "BIOMETRIC-USER-001": {"name": "Test User", "status": "approved"},
    "BIOMETRIC-USER-002": {"name": "Test User 2", "status": "approved"},
    "BIOMETRIC-USER-999": {"name": "Unknown User", "status": "declined"},
}

# Mock NFC cards
MOCK_NFC_CARDS = {
    "NFC-CARD-001": {"status": "approved"},
    "NFC-CARD-002": {"status": "approved"},
    "NFC-CARD-999": {"status": "declined"},
}

# Session storage (in-memory)
vision_sessions = {}
weight_verifications = {}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "mxp-vision-kiosk-simulator"}), 200


@app.route("/mxp-vision/scan", methods=["POST"])
def vision_scan():
    """
    Mock computer-vision bulk scan.

    Accepts a list of item identifiers (mock images) and returns
    recognized items with confidence scores.

    Expected payload:
    {
        "session_id": "scan-12345",
        "image_hints": ["apple", "milk", "bread"],
        "max_items": 20
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id", f"scan-{datetime.utcnow().timestamp()}")
    image_hints = data.get("image_hints", [])
    max_items = data.get("max_items", 20)

    if not image_hints:
        return (
            jsonify(
                {
                    "session_id": session_id,
                    "items": [],
                    "total_recognized": 0,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )

    # Simulate vision recognition
    recognized_items = []
    for hint in image_hints[:max_items]:
        # Find matching item
        matched_sku = None
        matched_name = None
        matched_price = 0.0
        matched_weight = 0.0

        for sku, item in MOCK_ITEMS.items():
            if hint.lower() in item["name"].lower() or hint.lower() in sku.lower():
                matched_sku = sku
                matched_name = item["name"]
                matched_price = item["price"]
                matched_weight = item["expected_weight_kg"]
                break

        if matched_sku:
            recognized_items.append(
                {
                    "sku": matched_sku,
                    "name": matched_name,
                    "price": matched_price,
                    "expected_weight_kg": matched_weight,
                    "confidence": round(random.uniform(0.85, 0.99), 3),
                }
            )
        else:
            # Unknown item — low confidence
            recognized_items.append(
                {
                    "sku": None,
                    "name": f"Unknown: {hint}",
                    "price": 0.0,
                    "expected_weight_kg": 0.0,
                    "confidence": round(random.uniform(0.25, 0.45), 3),
                }
            )

    # Store session
    vision_sessions[session_id] = {
        "items": recognized_items,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }

    logger.info(f"Vision scan {session_id}: {len(recognized_items)} items recognized")

    return (
        jsonify(
            {
                "session_id": session_id,
                "items": recognized_items,
                "total_recognized": len(
                    [i for i in recognized_items if i["sku"] is not None]
                ),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-vision/weight/verify", methods=["POST"])
def verify_weight():
    """
    Weight-scale cross-check.

    Verifies that the weight of an item matches the expected weight.

    Expected payload:
    {
        "sku": "SKU-1001",
        "measured_weight_kg": 3.78
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    sku = data.get("sku")
    measured_weight = data.get("measured_weight_kg")

    if not sku:
        abort(400, description="Missing 'sku' field")

    if measured_weight is None:
        abort(400, description="Missing 'measured_weight_kg' field")

    # Look up expected weight
    expected_weight = 0.0
    item_name = sku
    if sku in MOCK_ITEMS:
        expected_weight = MOCK_ITEMS[sku]["expected_weight_kg"]
        item_name = MOCK_ITEMS[sku]["name"]

    # Calculate tolerance (±5%)
    tolerance = expected_weight * 0.05
    matches = abs(measured_weight - expected_weight) <= tolerance

    weight_verifications[sku] = {
        "measured": measured_weight,
        "expected": expected_weight,
        "matches": matches,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }

    logger.info(
        f"Weight verify: {sku} -> {measured_weight}kg (expected: {expected_weight}kg, matches: {matches})"
    )

    return (
        jsonify(
            {
                "sku": sku,
                "name": item_name,
                "measured_weight_kg": measured_weight,
                "expected_weight_kg": expected_weight,
                "matches": matches,
                "tolerance_kg": tolerance,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-vision/payment/biometric", methods=["POST"])
def biometric_payment():
    """
    Mock biometric payment authorization (fingerprint/face).

    Expected payload:
    {
        "biometric_id": "BIOMETRIC-USER-001",
        "amount": 10.00
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    biometric_id = data.get("biometric_id")
    amount = data.get("amount", 0.0)

    if not biometric_id:
        abort(400, description="Missing 'biometric_id' field")

    # Look up user
    user = MOCK_BIOMETRIC_USERS.get(biometric_id)
    if not user:
        return (
            jsonify(
                {
                    "status": "declined",
                    "reason": "Unknown biometric ID",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            402,
        )

    if user.get("status") == "declined":
        return (
            jsonify(
                {
                    "status": "declined",
                    "reason": "Biometric authentication failed",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            402,
        )

    # Generate auth token
    auth_token = f"BIO-{datetime.utcnow().timestamp()}-{random.randint(1000, 9999)}"

    logger.info(f"Biometric payment approved: {biometric_id} for ${amount}")

    return (
        jsonify(
            {
                "status": "approved",
                "biometric_id": biometric_id,
                "auth_token": auth_token,
                "amount": amount,
                "user_name": user.get("name"),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-vision/payment/nfc", methods=["POST"])
def nfc_payment():
    """
    Mock NFC payment authorization (tap-to-pay).

    Expected payload:
    {
        "card_id": "NFC-CARD-001",
        "amount": 10.00
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    card_id = data.get("card_id")
    amount = data.get("amount", 0.0)

    if not card_id:
        abort(400, description="Missing 'card_id' field")

    # Look up card
    card = MOCK_NFC_CARDS.get(card_id)
    if not card:
        return (
            jsonify(
                {
                    "status": "declined",
                    "reason": "Unknown NFC card",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            402,
        )

    if card.get("status") == "declined":
        return (
            jsonify(
                {
                    "status": "declined",
                    "reason": "NFC card declined",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            402,
        )

    # Generate transaction ID
    transaction_id = f"NFC-{datetime.utcnow().timestamp()}-{random.randint(1000, 9999)}"

    logger.info(f"NFC payment approved: {card_id} for ${amount}")

    return (
        jsonify(
            {
                "status": "approved",
                "card_id": card_id,
                "transaction_id": transaction_id,
                "amount": amount,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/mxp-vision/transaction", methods=["POST"])
def process_transaction():
    """
    Process a full transaction through the MxP Vision Kiosk pipeline.

    Expected payload:
    {
        "session_id": "scan-12345",
        "items": [{"sku": "SKU-1001", "quantity": 2}],
        "region": "CA",
        "payment_method": "card" | "nfc" | "biometric",
        "payment_data": {"card_number": "4111111111111111"} or {"card_id": "NFC-CARD-001"} or {"biometric_id": "BIOMETRIC-USER-001"}
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    session_id = data.get("session_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    region = data.get("region", "CA")
    payment_method = data.get("payment_method", "card")
    payment_data = data.get("payment_data", {})

    if not items:
        abort(400, description="Missing 'items' field")

    logger.info(
        f"Processing MxP Vision transaction: {session_id} with {len(items)} items"
    )

    try:
        # Step 1: Get prices for all items
        subtotal = 0.0
        priced_items = []
        total_expected_weight = 0.0

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

            # Track expected weight
            if sku in MOCK_ITEMS:
                total_expected_weight += (
                    MOCK_ITEMS[sku]["expected_weight_kg"] * quantity
                )

        # Step 2: Calculate tax
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

        # Step 3: Process payment based on method
        payment_result = None

        if payment_method == "card":
            card_number = payment_data.get("card_number", "4111111111111111")
            pay_response = requests.post(
                f"{PAYMENT_URL}/payment/authorize",
                json={
                    "amount": total,
                    "currency": "USD",
                    "card_number": card_number,
                    "card_expiry": payment_data.get("expiry", "12/25"),
                    "cvv": payment_data.get("cvv", "123"),
                    "merchant_id": "MXP_VISION_KIOSK",
                    "order_id": f"VISION-{datetime.utcnow().timestamp()}",
                },
                timeout=5,
            )
            payment_result = pay_response.json()
            payment_result["status"] = (
                "approved" if pay_response.status_code == 200 else "declined"
            )

        elif payment_method == "nfc":
            nfc_response = requests.post(
                f"{app.config.get('BASE_URL', 'http://localhost:5000')}/mxp-vision/payment/nfc",
                json={
                    "card_id": payment_data.get("card_id", "NFC-CARD-001"),
                    "amount": total,
                },
                timeout=5,
            )
            payment_result = nfc_response.json()

        elif payment_method == "biometric":
            bio_response = requests.post(
                f"{app.config.get('BASE_URL', 'http://localhost:5000')}/mxp-vision/payment/biometric",
                json={
                    "biometric_id": payment_data.get(
                        "biometric_id", "BIOMETRIC-USER-001"
                    ),
                    "amount": total,
                },
                timeout=5,
            )
            payment_result = bio_response.json()

        else:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "payment",
                        "error": f"Unknown payment method: {payment_method}",
                    }
                ),
                400,
            )

        result = {
            "status": (
                "completed"
                if payment_result.get("status") == "approved"
                else "declined"
            ),
            "session_id": session_id,
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "expected_weight_kg": total_expected_weight,
            "payment_method": payment_method,
            "payment": payment_result,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"MxP Vision transaction {session_id}: {result['status']}")
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except requests.exceptions.RequestException as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
