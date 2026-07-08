# simulators/pos-services/tax/app.py

"""
tax-service — Minimal REST API for POS tax calculations.
Returns tax for subtotal based on region. Mock-only — no real tax data.
"""

import os
import json
import logging
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Mock tax rates by region
# Format: region_code -> { "tax_rate": float, "description": str }
MOCK_TAX_RATES = {
    "CA": {"tax_rate": 0.0725, "description": "California state sales tax"},
    "TX": {"tax_rate": 0.0625, "description": "Texas state sales tax"},
    "NY": {"tax_rate": 0.04, "description": "New York state sales tax"},
    "FL": {"tax_rate": 0.06, "description": "Florida state sales tax"},
    "IL": {"tax_rate": 0.0625, "description": "Illinois state sales tax"},
    "PA": {"tax_rate": 0.06, "description": "Pennsylvania state sales tax"},
    "OH": {"tax_rate": 0.0575, "description": "Ohio state sales tax"},
    "GA": {"tax_rate": 0.04, "description": "Georgia state sales tax"},
    "NC": {"tax_rate": 0.0475, "description": "North Carolina state sales tax"},
    "MI": {"tax_rate": 0.06, "description": "Michigan state sales tax"},
    "NJ": {"tax_rate": 0.06625, "description": "New Jersey state sales tax"},
    "VA": {"tax_rate": 0.053, "description": "Virginia state sales tax"},
    "WA": {"tax_rate": 0.065, "description": "Washington state sales tax"},
    "MA": {"tax_rate": 0.0625, "description": "Massachusetts state sales tax"},
    "AZ": {"tax_rate": 0.056, "description": "Arizona state sales tax"},
    "IN": {"tax_rate": 0.07, "description": "Indiana state sales tax"},
    "TN": {"tax_rate": 0.07, "description": "Tennessee state sales tax"},
    "MO": {"tax_rate": 0.04225, "description": "Missouri state sales tax"},
    "MD": {"tax_rate": 0.06, "description": "Maryland state sales tax"},
    "WI": {"tax_rate": 0.05, "description": "Wisconsin state sales tax"},
    "MN": {"tax_rate": 0.06875, "description": "Minnesota state sales tax"},
    "CO": {"tax_rate": 0.029, "description": "Colorado state sales tax"},
    "AL": {"tax_rate": 0.04, "description": "Alabama state sales tax"},
    "SC": {"tax_rate": 0.06, "description": "South Carolina state sales tax"},
    "LA": {"tax_rate": 0.0445, "description": "Louisiana state sales tax"},
    "KY": {"tax_rate": 0.06, "description": "Kentucky state sales tax"},
    "OR": {"tax_rate": 0.0, "description": "Oregon - no sales tax"},
    "NH": {"tax_rate": 0.0, "description": "New Hampshire - no sales tax"},
    "DE": {"tax_rate": 0.0, "description": "Delaware - no sales tax"},
    "MT": {"tax_rate": 0.0, "description": "Montana - no sales tax"},
}


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "tax-service"}), 200


@app.route("/tax", methods=["POST"])
def calculate_tax():
    """
    Calculate tax for a given subtotal and region.

    Expected payload:
    {
        "subtotal": 100.00,
        "region": "CA",
        "items": [  # Optional: item-level tax calculation
            { "sku": "SKU-1001", "price": 2.99, "taxable": true },
            { "sku": "SKU-1005", "price": 0.99, "taxable": false }
        ]
    }

    Returns:
        JSON: {
            "subtotal": float,
            "region": str,
            "tax_rate": float,
            "tax_amount": float,
            "total": float
        }

    Examples:
        POST /tax
        { "subtotal": 100.00, "region": "CA" }
        -> { "subtotal": 100.00, "region": "CA", "tax_rate": 0.0725, "tax_amount": 7.25, "total": 107.25 }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    # Extract parameters
    subtotal = data.get("subtotal")
    region = data.get("region", "").upper()
    items = data.get("items", [])

    # Validate subtotal
    if subtotal is None:
        abort(400, description="Missing 'subtotal' field")

    try:
        subtotal = float(subtotal)
    except (TypeError, ValueError):
        abort(400, description="'subtotal' must be a number")

    if subtotal < 0:
        abort(400, description="'subtotal' cannot be negative")

    # Validate region
    if not region:
        abort(400, description="Missing 'region' field")

    if region not in MOCK_TAX_RATES:
        abort(404, description=f"Region '{region}' not found")

    # Calculate tax
    tax_rate = MOCK_TAX_RATES[region]["tax_rate"]

    # If items provided, calculate item-level tax
    if items:
        taxable_subtotal = 0.0
        non_taxable_subtotal = 0.0

        for item in items:
            price = float(item.get("price", 0))
            taxable = item.get("taxable", True)

            if taxable:
                taxable_subtotal += price
            else:
                non_taxable_subtotal += price

        # Use taxable_subtotal for tax calculation
        tax_amount = round(taxable_subtotal * tax_rate, 2)
        total = round(subtotal + tax_amount, 2)

        logger.info(
            f"Item-level tax calculation: subtotal=${subtotal:.2f}, taxable=${taxable_subtotal:.2f}, region={region}, tax_rate={tax_rate:.4f}, tax=${tax_amount:.2f}"
        )
    else:
        # Simple tax calculation
        tax_amount = round(subtotal * tax_rate, 2)
        total = round(subtotal + tax_amount, 2)

        logger.info(
            f"Simple tax calculation: subtotal=${subtotal:.2f}, region={region}, tax_rate={tax_rate:.4f}, tax=${tax_amount:.2f}"
        )

    return (
        jsonify(
            {
                "subtotal": subtotal,
                "region": region,
                "tax_rate": tax_rate,
                "tax_amount": tax_amount,
                "total": total,
                "description": MOCK_TAX_RATES[region]["description"],
            }
        ),
        200,
    )


@app.route("/tax/rate/<region>", methods=["GET"])
def get_tax_rate(region):
    """
    Get the tax rate for a specific region.

    Args:
        region (str): Region code (e.g., CA, TX, NY)

    Returns:
        JSON: { "region": str, "tax_rate": float, "description": str }

    Examples:
        GET /tax/rate/CA
        -> { "region": "CA", "tax_rate": 0.0725, "description": "California state sales tax" }
    """
    region = region.upper()

    if region not in MOCK_TAX_RATES:
        abort(404, description=f"Region '{region}' not found")

    return (
        jsonify(
            {
                "region": region,
                "tax_rate": MOCK_TAX_RATES[region]["tax_rate"],
                "description": MOCK_TAX_RATES[region]["description"],
            }
        ),
        200,
    )


@app.route("/tax/regions", methods=["GET"])
def get_all_regions():
    """Get all available tax regions."""
    regions = []
    for code, data in MOCK_TAX_RATES.items():
        regions.append(
            {
                "code": code,
                "tax_rate": data["tax_rate"],
                "description": data["description"],
            }
        )
    return jsonify({"regions": regions}), 200


@app.route("/tax/validate", methods=["POST"])
def validate_tax_configuration():
    """
    Validate tax configuration for a region.
    This is useful for testing regional tax setups.

    Expected payload:
    {
        "region": "CA",
        "subtotals": [10.00, 25.00, 100.00]
    }

    Returns:
        JSON: { "region": str, "results": [ { "subtotal": float, "tax_amount": float, "total": float } ] }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    region = data.get("region", "").upper()
    subtotals = data.get("subtotals", [])

    if not region:
        abort(400, description="Missing 'region' field")

    if region not in MOCK_TAX_RATES:
        abort(404, description=f"Region '{region}' not found")

    if not subtotals or not isinstance(subtotals, list):
        abort(400, description="'subtotals' must be a list of numbers")

    tax_rate = MOCK_TAX_RATES[region]["tax_rate"]
    results = []

    for subtotal in subtotals:
        try:
            subtotal = float(subtotal)
        except (TypeError, ValueError):
            continue

        if subtotal < 0:
            continue

        tax_amount = round(subtotal * tax_rate, 2)
        results.append(
            {
                "subtotal": subtotal,
                "tax_amount": tax_amount,
                "total": round(subtotal + tax_amount, 2),
            }
        )

    return jsonify({"region": region, "tax_rate": tax_rate, "results": results}), 200


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
    port = int(os.environ.get("PORT", 8083))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
