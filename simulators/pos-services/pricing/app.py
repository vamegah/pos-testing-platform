# simulators/pos-services/pricing/app.py

"""
pricing-service — Minimal REST API for POS pricing lookups.
Returns price for a given SKU. Mock-only — no real pricing data.
"""

import os
import json
import logging
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Mock product catalog
# Format: SKU -> { "sku": str, "price": float, "name": str }
MOCK_CATALOG = {
    "SKU-1001": {"sku": "SKU-1001", "price": 2.99, "name": "Milk (1 gal)"},
    "SKU-1002": {"sku": "SKU-1002", "price": 1.49, "name": "Bread (white)"},
    "SKU-1003": {"sku": "SKU-1003", "price": 3.99, "name": "Eggs (dozen)"},
    "SKU-1004": {"sku": "SKU-1004", "price": 4.50, "name": "Chicken Breast (lb)"},
    "SKU-1005": {"sku": "SKU-1005", "price": 0.99, "name": "Apple (each)"},
    "SKU-1006": {"sku": "SKU-1006", "price": 1.99, "name": "Orange Juice (64oz)"},
    "SKU-1007": {"sku": "SKU-1007", "price": 5.49, "name": "Cheese (cheddar, 8oz)"},
    "SKU-1008": {"sku": "SKU-1008", "price": 2.29, "name": "Butter (salted, 1lb)"},
    "SKU-1009": {"sku": "SKU-1009", "price": 3.29, "name": "Cereal (family size)"},
    "SKU-1010": {"sku": "SKU-1010", "price": 7.99, "name": "Coffee (ground, 12oz)"},
}

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "pricing-service"}), 200

@app.route('/price/<sku>', methods=['GET'])
def get_price(sku):
    """
    Get price for a given SKU.
    
    Args:
        sku (str): Stock Keeping Unit identifier
        
    Returns:
        JSON: { "sku": str, "price": float, "name": str } if found
        HTTP 404: if SKU not found
        
    Examples:
        GET /price/SKU-1001
        -> { "sku": "SKU-1001", "price": 2.99, "name": "Milk (1 gal)" }
        
        GET /price/SKU-9999
        -> HTTP 404 Not Found
    """
    # Validate SKU format (basic check)
    if not sku or len(sku) < 3:
        abort(400, description="SKU must be at least 3 characters")

    # Look up in catalog
    if sku in MOCK_CATALOG:
        logger.info(f"Price lookup: {sku} -> ${MOCK_CATALOG[sku]['price']}")
        return jsonify(MOCK_CATALOG[sku]), 200
    else:
        logger.warning(f"Price lookup: {sku} -> NOT FOUND")
        abort(404, description=f"SKU '{sku}' not found")

@app.route('/price/bulk', methods=['POST'])
def get_prices_bulk():
    """
    Get prices for multiple SKUs in one request.
    
    Expected payload:
    {
        "skus": ["SKU-1001", "SKU-1002", ...]
    }
    
    Returns:
        JSON: { "results": [ { "sku": str, "price": float, "name": str, "found": bool }, ... ] }
    """
    data = request.get_json()
    if not data or 'skus' not in data:
        abort(400, description="Missing 'skus' field in request body")
    
    skus = data.get('skus', [])
    if not isinstance(skus, list):
        abort(400, description="'skus' must be a list")
    
    results = []
    for sku in skus:
        if sku in MOCK_CATALOG:
            results.append({
                "sku": sku,
                "price": MOCK_CATALOG[sku]['price'],
                "name": MOCK_CATALOG[sku]['name'],
                "found": True
            })
        else:
            results.append({
                "sku": sku,
                "found": False
            })
    
    return jsonify({"results": results}), 200

@app.route('/catalog', methods=['GET'])
def get_catalog():
    """
    Get the full mock product catalog.
    
    Returns:
        JSON: { "catalog": { "SKU-1001": { "price": 2.99, ... }, ... } }
    """
    return jsonify({"catalog": MOCK_CATALOG}), 200

@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors with JSON response."""
    return jsonify({
        "error": "Not Found",
        "message": str(error.description) if hasattr(error, 'description') else "Resource not found"
    }), 404

@app.errorhandler(400)
def bad_request(error):
    """Handle 400 errors with JSON response."""
    return jsonify({
        "error": "Bad Request",
        "message": str(error.description) if hasattr(error, 'description') else "Invalid request"
    }), 400

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8081))
    debug = os.environ.get('DEBUG', 'false').lower() == 'true'
    app.run(host='0.0.0.0', port=port, debug=debug)