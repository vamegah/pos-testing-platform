# simulators/commerce-scale-unit/simulator.py

"""
Commerce Scale Unit (C1)

A store/edge-tier facade aggregating the existing pricing, promotions, and tax
services into one business-logic unit. Includes edge-autonomy: keeps working
when the cloud layer D is unreachable (using cached data).
"""

import os
import json
import logging
import time
import requests
import threading
from datetime import datetime, timedelta
from typing import Dict, Any, Optional, List
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
# Cloud service URLs
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")

# Edge autonomy settings
CACHE_TTL_SECONDS = int(os.environ.get("CACHE_TTL_SECONDS", 300))  # 5 minutes
CLOUD_UNREACHABLE = os.environ.get("CLOUD_UNREACHABLE", "false").lower() == "true"

# ============================================================
# Cache (for edge autonomy)
# ============================================================
# Catalog cache: SKU -> {price, name, weight, cached_at}
catalog_cache = {}

# Region cache: region_code -> {tax_rate, name, cached_at}
region_cache = {}

# Last successful cloud sync time
last_cloud_sync = None

# Cached items and regions from last successful sync
cached_catalog = {}
cached_regions = {}


# ============================================================
# Helper Functions
# ============================================================


def is_cloud_available() -> bool:
    """Check if the cloud layer is available."""
    if CLOUD_UNREACHABLE:
        return False
    try:
        response = requests.get(f"{PRICING_URL}/health", timeout=2)
        return response.status_code == 200
    except:
        return False


def sync_catalog_from_cloud() -> bool:
    """Sync catalog data from the cloud."""
    global cached_catalog, cached_regions, last_cloud_sync

    try:
        # Get catalog from pricing service
        catalog_response = requests.get(f"{PRICING_URL}/catalog", timeout=5)
        if catalog_response.status_code != 200:
            return False

        catalog_data = catalog_response.json()
        if "catalog" in catalog_data:
            # Cache catalog items
            for sku, item in catalog_data["catalog"].items():
                cached_catalog[sku] = {
                    "sku": sku,
                    "price": item.get("price", 0),
                    "name": item.get("name", sku),
                    "cached_at": datetime.utcnow().isoformat() + "Z",
                }
                logger.debug(f"Cached catalog item: {sku} -> ${item.get('price', 0)}")

        # Get regions from tax service
        regions_response = requests.get(f"{TAX_URL}/tax/regions", timeout=5)
        if regions_response.status_code == 200:
            regions_data = regions_response.json()
            if "regions" in regions_data:
                for region in regions_data["regions"]:
                    cached_regions[region["code"]] = {
                        "code": region["code"],
                        "tax_rate": region["tax_rate"],
                        "description": region.get("description", ""),
                        "cached_at": datetime.utcnow().isoformat() + "Z",
                    }

        last_cloud_sync = datetime.utcnow()
        logger.info(
            f"Synced from cloud: {len(cached_catalog)} items, {len(cached_regions)} regions"
        )
        return True

    except Exception as e:
        logger.warning(f"Cloud sync failed: {e}")
        return False


def get_price_from_cache(sku: str) -> Optional[float]:
    """Get price from cache."""
    item = cached_catalog.get(sku)
    if item:
        return item.get("price")
    return None


def get_region_from_cache(region_code: str) -> Optional[Dict[str, Any]]:
    """Get region data from cache."""
    return cached_regions.get(region_code)


def get_price_from_cloud(sku: str) -> Optional[float]:
    """Get price from cloud pricing service."""
    try:
        response = requests.get(f"{PRICING_URL}/price/{sku}", timeout=5)
        if response.status_code == 200:
            data = response.json()
            return data.get("price")
    except:
        pass
    return None


def get_tax_from_cloud(subtotal: float, region: str) -> Optional[Dict[str, Any]]:
    """Get tax from cloud tax service."""
    try:
        response = requests.post(
            f"{TAX_URL}/tax", json={"subtotal": subtotal, "region": region}, timeout=5
        )
        if response.status_code == 200:
            return response.json()
    except:
        pass
    return None


def get_promotions_from_cloud(sku: str) -> Optional[List[Dict[str, Any]]]:
    """Get promotions from cloud promotions service."""
    try:
        response = requests.get(f"{PROMOTIONS_URL}/promotions/sku/{sku}", timeout=5)
        if response.status_code == 200:
            data = response.json()
            return data.get("promotions", [])
    except:
        pass
    return None


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
                "service": "commerce-scale-unit",
                "cloud_available": is_cloud_available(),
                "cache_size": len(cached_catalog),
                "regions_cached": len(cached_regions),
                "last_cloud_sync": (
                    last_cloud_sync.isoformat() if last_cloud_sync else None
                ),
            }
        ),
        200,
    )


@app.route("/commerce-scale-unit/checkout/price", methods=["POST"])
def checkout_price():
    """
    Aggregate pricing, promotions, and tax for a basket.

    Expected payload:
    {
        "items": [{"sku": "SKU-1001", "quantity": 2}],
        "region": "CA"
    }

    Returns:
    {
        "subtotal": 5.98,
        "tax": 0.43,
        "total": 6.41,
        "items": [...],
        "promotions": [...],
        "source": "cache" | "cloud"
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region_code = data.get("region", "CA")

    if not items:
        abort(400, description="Missing 'items' field")

    # Determine if cloud is available
    cloud_available = is_cloud_available()
    source = "cloud" if cloud_available else "cache"

    logger.info(f"Processing checkout: {len(items)} items, source={source}")

    # Process each item
    priced_items = []
    subtotal = 0.0
    all_promotions = []

    for item in items:
        sku = item.get("sku")
        quantity = item.get("quantity", 1)

        # Get price
        price = None
        if cloud_available:
            price = get_price_from_cloud(sku)
        if price is None:
            price = get_price_from_cache(sku)
        if price is None:
            return (
                jsonify(
                    {
                        "status": "error",
                        "error": f"Price not found for SKU: {sku}",
                        "sku": sku,
                    }
                ),
                404,
            )

        # Get promotions (only from cloud if available)
        promotions = []
        if cloud_available:
            promotions = get_promotions_from_cloud(sku) or []
            all_promotions.extend(promotions)

        item_total = price * quantity
        subtotal += item_total

        priced_items.append(
            {
                "sku": sku,
                "quantity": quantity,
                "price": price,
                "total": item_total,
                "source": source,
                "promotions": promotions,
            }
        )

    # Calculate tax
    tax_amount = 0.0
    tax_rate = 0.0

    if cloud_available:
        tax_result = get_tax_from_cloud(subtotal, region_code)
        if tax_result:
            tax_amount = tax_result.get("tax_amount", 0)
            tax_rate = tax_result.get("tax_rate", 0)
    else:
        region_data = get_region_from_cache(region_code)
        if region_data:
            tax_rate = region_data.get("tax_rate", 0)
            tax_amount = subtotal * tax_rate
        else:
            # Default tax rate if region not in cache
            tax_amount = subtotal * 0.07
            tax_rate = 0.07

    total = subtotal + tax_amount

    result = {
        "status": "success",
        "subtotal": round(subtotal, 2),
        "tax": round(tax_amount, 2),
        "tax_rate": round(tax_rate, 4),
        "total": round(total, 2),
        "items": priced_items,
        "promotions": all_promotions,
        "source": source,
        "region": region_code,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }

    if not cloud_available:
        result["note"] = "Edge mode (cloud unreachable) - using cached data"

    logger.info(f"Checkout complete: total=${total}, source={source}")
    return jsonify(result), 200


@app.route("/commerce-scale-unit/cloud/status", methods=["GET"])
def cloud_status():
    """Get the current cloud availability status."""
    available = is_cloud_available()
    return (
        jsonify(
            {
                "cloud_available": available,
                "cache_size": len(cached_catalog),
                "regions_cached": len(cached_regions),
                "last_cloud_sync": (
                    last_cloud_sync.isoformat() if last_cloud_sync else None
                ),
                "edge_mode": not available and len(cached_catalog) > 0,
            }
        ),
        200,
    )


@app.route("/commerce-scale-unit/cloud/simulate-unreachable", methods=["POST"])
def simulate_cloud_unreachable():
    """
    Simulate the cloud being unreachable (for edge-autonomy testing).

    Expected payload:
    {
        "unreachable": true,
        "duration_seconds": 60
    }
    """
    global CLOUD_UNREACHABLE

    data = request.get_json() or {}
    unreachable = data.get("unreachable", True)
    duration = data.get("duration_seconds", 60)

    CLOUD_UNREACHABLE = unreachable

    if unreachable:
        logger.warning(f"Simulating cloud unreachable for {duration}s")

        def recover():
            time.sleep(duration)
            global CLOUD_UNREACHABLE
            CLOUD_UNREACHABLE = False
            logger.info("Cloud recovered from simulated outage")

        threading.Thread(target=recover, daemon=True).start()
    else:
        logger.info("Cloud unreachable simulation cleared")

    return (
        jsonify(
            {
                "status": "simulated" if unreachable else "cleared",
                "cloud_unreachable": CLOUD_UNREACHABLE,
                "duration_seconds": duration if unreachable else 0,
            }
        ),
        200,
    )


@app.route("/commerce-scale-unit/cloud/sync", methods=["POST"])
def sync_cloud_cache():
    """Manually trigger a cloud sync."""
    success = sync_catalog_from_cloud()
    return jsonify(
        {
            "status": "synced" if success else "failed",
            "catalog_size": len(cached_catalog),
            "regions_cached": len(cached_regions),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    ), (200 if success else 500)


@app.route("/commerce-scale-unit/cache", methods=["GET"])
def get_cache_status():
    """Get the current cache status."""
    return (
        jsonify(
            {
                "catalog_count": len(cached_catalog),
                "regions_count": len(cached_regions),
                "catalog_items": list(cached_catalog.keys()),
                "regions": list(cached_regions.keys()),
                "last_cloud_sync": (
                    last_cloud_sync.isoformat() if last_cloud_sync else None
                ),
                "cloud_available": is_cloud_available(),
            }
        ),
        200,
    )


@app.route("/commerce-scale-unit/edge-autonomy", methods=["POST"])
def test_edge_autonomy():
    """
    Edge-autonomy test: verify the unit works when cloud is unreachable.

    This endpoint runs a test that:
    1. Ensures cache is populated
    2. Simulates cloud unreachable
    3. Processes a checkout using cached data
    4. Verifies the checkout succeeds
    5. Restores cloud connectivity
    """
    global CLOUD_UNREACHABLE

    logger.info("Running edge-autonomy test...")

    # Step 1: Ensure cache is populated
    if len(cached_catalog) == 0:
        sync_result = sync_catalog_from_cloud()
        if not sync_result:
            return (
                jsonify(
                    {
                        "status": "error",
                        "error": "Failed to populate cache",
                        "step": "cache_population",
                    }
                ),
                500,
            )
        logger.info("Cache populated")

    # Step 2: Simulate cloud unreachable
    old_unreachable = CLOUD_UNREACHABLE
    CLOUD_UNREACHABLE = True
    logger.info("Cloud unreachable simulated")

    # Step 3: Process checkout using cached data
    test_items = [{"sku": "SKU-1001", "quantity": 2}]
    checkout_result = checkout_price({"items": test_items, "region": "CA"})

    if checkout_result.status_code != 200:
        CLOUD_UNREACHABLE = old_unreachable
        return (
            jsonify(
                {
                    "status": "error",
                    "error": "Checkout failed in edge mode",
                    "response": (
                        checkout_result.get_json()
                        if hasattr(checkout_result, "get_json")
                        else None
                    ),
                }
            ),
            500,
        )

    checkout_data = (
        checkout_result.get_json() if hasattr(checkout_result, "get_json") else {}
    )
    source = checkout_data.get("source", "unknown")

    # Step 4: Restore cloud connectivity
    CLOUD_UNREACHABLE = old_unreachable
    logger.info("Cloud connectivity restored")

    # Step 5: Verify edge mode was used
    if source != "cache":
        return (
            jsonify(
                {
                    "status": "failed",
                    "error": f"Expected source='cache', got source='{source}'",
                    "step": "edge_mode_verification",
                }
            ),
            500,
        )

    return (
        jsonify(
            {
                "test": "edge_autonomy",
                "status": "passed",
                "checkout": checkout_data,
                "source": source,
                "cache_size": len(cached_catalog),
                "regions_cached": len(cached_regions),
                "cloud_unreachable_simulated": True,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


# ============================================================
# Initialization
# ============================================================


# Populate cache on startup
def initialize_cache():
    """Populate cache on startup."""
    logger.info("Initializing Commerce Scale Unit cache...")
    success = sync_catalog_from_cloud()
    if success:
        logger.info(
            f"Cache initialized: {len(cached_catalog)} items, {len(cached_regions)} regions"
        )
    else:
        logger.warning("Cache initialization failed (cloud may be unavailable)")


# Run cache initialization in background to avoid blocking startup
threading.Thread(target=initialize_cache, daemon=True).start()


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5012))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Commerce Scale Unit starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
