# simulators/pos-services/promotions/app.py

"""
promotions-service — Minimal REST API for POS promotion/discount lookups.
Returns applicable discounts for SKUs or entire carts. Mock-only — no real promotion data.
"""

import os
import json
import logging
from flask import Flask, request, jsonify, abort
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Mock promotions database
# Format: promotion_id -> { "id": str, "type": str, "description": str, "discount_percent": float, "applicable_skus": list, "min_quantity": int, "valid_until": str }
MOCK_PROMOTIONS = {
    "PROMO-001": {
        "id": "PROMO-001",
        "type": "percentage",
        "description": "10% off all dairy products",
        "discount_percent": 10.0,
        "applicable_skus": ["SKU-1001", "SKU-1007", "SKU-1008"],  # Milk, Cheese, Butter
        "min_quantity": 1,
        "valid_until": "2026-12-31",
    },
    "PROMO-002": {
        "id": "PROMO-002",
        "type": "percentage",
        "description": "20% off breakfast items",
        "discount_percent": 20.0,
        "applicable_skus": [
            "SKU-1002",
            "SKU-1009",
            "SKU-1010",
        ],  # Bread, Cereal, Coffee
        "min_quantity": 1,
        "valid_until": "2026-12-31",
    },
    "PROMO-003": {
        "id": "PROMO-003",
        "type": "fixed_amount",
        "description": "$1.00 off any chicken purchase",
        "discount_amount": 1.00,
        "applicable_skus": ["SKU-1004"],  # Chicken Breast
        "min_quantity": 1,
        "valid_until": "2026-06-30",
    },
    "PROMO-004": {
        "id": "PROMO-004",
        "type": "percentage",
        "description": "5% off all produce",
        "discount_percent": 5.0,
        "applicable_skus": ["SKU-1005"],  # Apples
        "min_quantity": 1,
        "valid_until": "2026-12-31",
    },
    "PROMO-005": {
        "id": "PROMO-005",
        "type": "percentage",
        "description": "15% off beverages",
        "discount_percent": 15.0,
        "applicable_skus": ["SKU-1006"],  # Orange Juice
        "min_quantity": 1,
        "valid_until": "2026-12-31",
    },
    "PROMO-006": {
        "id": "PROMO-006",
        "type": "fixed_amount",
        "description": "$2.00 off when buying 2 or more of any item",
        "discount_amount": 2.00,
        "applicable_skus": [],  # Applies to any SKU when min_quantity met
        "min_quantity": 2,
        "valid_until": "2026-12-31",
    },
}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "promotions-service"}), 200


@app.route("/promotions/sku/<sku>", methods=["GET"])
def get_promotions_for_sku(sku):
    """
    Get all applicable promotions for a single SKU.

    Args:
        sku (str): Stock Keeping Unit identifier

    Returns:
        JSON: { "sku": str, "promotions": [ { "id": str, "description": str, ... }, ... ] }

    Examples:
        GET /promotions/sku/SKU-1001
        -> { "sku": "SKU-1001", "promotions": [ { "id": "PROMO-001", ... } ] }
    """
    if not sku or len(sku) < 3:
        abort(400, description="SKU must be at least 3 characters")

    applicable = []
    for promo_id, promo in MOCK_PROMOTIONS.items():
        # Check if promo applies to this SKU
        if sku in promo.get("applicable_skus", []) or not promo.get("applicable_skus"):
            # Check if promo is still valid
            if is_promo_valid(promo):
                applicable.append(
                    {
                        "id": promo["id"],
                        "type": promo["type"],
                        "description": promo["description"],
                        "discount_percent": promo.get("discount_percent", 0),
                        "discount_amount": promo.get("discount_amount", 0),
                        "min_quantity": promo.get("min_quantity", 1),
                    }
                )

    logger.info(f"Promotions lookup: {sku} -> {len(applicable)} promotions found")
    return jsonify({"sku": sku, "promotions": applicable}), 200


@app.route("/promotions/cart", methods=["POST"])
def get_promotions_for_cart():
    """
    Get all applicable promotions for a cart of items.

    Expected payload:
    {
        "items": [ { "sku": "SKU-1001", "quantity": 2 }, ... ]
    }

    Returns:
        JSON: { "cart_promotions": [ { "id": str, "description": str, "discount_amount": float, ... } ], "total_discount": float }
    """
    data = request.get_json()
    if not data or "items" not in data:
        abort(400, description="Missing 'items' field in request body")

    items = data.get("items", [])
    if not isinstance(items, list):
        abort(400, description="'items' must be a list")

    # Collect all SKUs and quantities
    sku_quantities = {}
    for item in items:
        sku = item.get("sku")
        qty = item.get("quantity", 1)
        if sku:
            sku_quantities[sku] = sku_quantities.get(sku, 0) + qty

    # Find applicable promotions
    applicable_promotions = []
    total_discount = 0.0

    for promo_id, promo in MOCK_PROMOTIONS.items():
        if not is_promo_valid(promo):
            continue

        # Check if any item in cart qualifies
        applies = False
        discount_amount = 0.0

        for sku, qty in sku_quantities.items():
            # Check if SKU is applicable or promo applies to all items
            if sku in promo.get("applicable_skus", []) or not promo.get(
                "applicable_skus"
            ):
                # Check quantity threshold
                min_qty = promo.get("min_quantity", 1)
                if qty >= min_qty:
                    applies = True
                    # Calculate discount for this item (simplified)
                    if promo["type"] == "percentage":
                        # Would need price lookup for accurate discount
                        # For mock, estimate based on item count
                        discount_amount += promo.get("discount_percent", 0) / 100.0
                    elif promo["type"] == "fixed_amount":
                        discount_amount += promo.get("discount_amount", 0) * (
                            qty // min_qty
                        )

        if applies:
            applicable_promotions.append(
                {
                    "id": promo["id"],
                    "type": promo["type"],
                    "description": promo["description"],
                    "discount_percent": promo.get("discount_percent", 0),
                    "discount_amount": promo.get("discount_amount", 0),
                    "min_quantity": promo.get("min_quantity", 1),
                }
            )
            total_discount += discount_amount

    logger.info(
        f"Cart promotions: {len(applicable_promotions)} promotions found, total discount: ${total_discount:.2f}"
    )
    return (
        jsonify(
            {
                "cart_promotions": applicable_promotions,
                "total_discount_estimate": round(total_discount, 2),
            }
        ),
        200,
    )


@app.route("/promotions/all", methods=["GET"])
def get_all_promotions():
    """Get all active promotions."""
    active = []
    for promo_id, promo in MOCK_PROMOTIONS.items():
        if is_promo_valid(promo):
            active.append(promo)
    return jsonify({"promotions": active}), 200


def is_promo_valid(promo):
    """Check if a promotion is still valid based on date."""
    if "valid_until" not in promo:
        return True

    try:
        valid_until = datetime.strptime(promo["valid_until"], "%Y-%m-%d")
        return datetime.now() <= valid_until
    except ValueError:
        # If date format is invalid, assume it's valid
        logger.warning(
            f"Invalid date format for promotion {promo.get('id')}: {promo.get('valid_until')}"
        )
        return True


@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors with JSON response."""
    return (
        jsonify(
            {
                "error": "Not Found",
                "message": (
                    str(error.description)
                    if hasattr(error, "description")
                    else "Resource not found"
                ),
            }
        ),
        404,
    )


@app.errorhandler(400)
def bad_request(error):
    """Handle 400 errors with JSON response."""
    return (
        jsonify(
            {
                "error": "Bad Request",
                "message": (
                    str(error.description)
                    if hasattr(error, "description")
                    else "Invalid request"
                ),
            }
        ),
        400,
    )


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8082))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
