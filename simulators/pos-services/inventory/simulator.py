# simulators/pos-services/inventory/simulator.py

"""
Inventory Microservice (D2)

Stock lookup + decrement-on-sale, with an oversell negative test.

Capabilities:
  - Stock lookup by SKU
  - Decrement stock on sale
  - Oversell prevention (reject if insufficient stock)
  - Stock replenishment
  - Event publishing on stock changes
"""

import os
import json
import logging
import time
import requests
from datetime import datetime
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
GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://gateway:5014")

# ============================================================
# Inventory State
# ============================================================
# Initial inventory: SKU -> stock level
inventory = {
    "SKU-1001": {"stock": 100, "name": "Milk (1 gal)", "price": 2.99, "reserved": 0},
    "SKU-1002": {"stock": 50, "name": "Bread (white)", "price": 1.49, "reserved": 0},
    "SKU-1003": {"stock": 75, "name": "Eggs (dozen)", "price": 3.99, "reserved": 0},
    "SKU-1004": {
        "stock": 30,
        "name": "Chicken Breast (lb)",
        "price": 4.50,
        "reserved": 0,
    },
    "SKU-1005": {"stock": 200, "name": "Apple (each)", "price": 0.99, "reserved": 0},
    "SKU-1006": {
        "stock": 60,
        "name": "Orange Juice (64oz)",
        "price": 1.99,
        "reserved": 0,
    },
    "SKU-1007": {
        "stock": 40,
        "name": "Cheese (cheddar, 8oz)",
        "price": 5.49,
        "reserved": 0,
    },
    "SKU-1008": {
        "stock": 80,
        "name": "Butter (salted, 1lb)",
        "price": 2.29,
        "reserved": 0,
    },
    "SKU-1009": {
        "stock": 45,
        "name": "Cereal (family size)",
        "price": 3.29,
        "reserved": 0,
    },
    "SKU-1010": {
        "stock": 25,
        "name": "Coffee (ground, 12oz)",
        "price": 7.99,
        "reserved": 0,
    },
}

# Transaction history for audit
stock_change_history = []


# ============================================================
# Helper Functions
# ============================================================


def publish_stock_event(event_type: str, sku: str, quantity: int, remaining: int):
    """Publish a stock event via the gateway."""
    try:
        event_data = {
            "sku": sku,
            "quantity": quantity,
            "remaining": remaining,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
        requests.post(
            f"{GATEWAY_URL}/gateway/events/publish",
            json={"event_type": event_type, "event_data": event_data},
            timeout=2,
        )
        logger.debug(f"Stock event published: {event_type} for {sku}")
    except Exception as e:
        logger.warning(f"Failed to publish stock event: {e}")


def log_stock_change(
    sku: str,
    old_stock: int,
    new_stock: int,
    quantity: int,
    change_type: str,
    transaction_id: str = None,
):
    """Log a stock change for audit."""
    entry = {
        "sku": sku,
        "old_stock": old_stock,
        "new_stock": new_stock,
        "quantity": quantity,
        "change_type": change_type,
        "transaction_id": transaction_id,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    stock_change_history.append(entry)
    logger.info(
        f"Stock change: {sku} {old_stock}->{new_stock} ({quantity}) {change_type}"
    )


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
                "service": "inventory-service",
                "items_count": len(inventory),
                "total_stock": sum(item["stock"] for item in inventory.values()),
                "history_count": len(stock_change_history),
            }
        ),
        200,
    )


@app.route("/inventory/stock", methods=["GET"])
def get_all_stock():
    """Get all inventory stock levels."""
    return (
        jsonify(
            {
                "items": inventory,
                "count": len(inventory),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/stock/<sku>", methods=["GET"])
def get_stock(sku: str):
    """Get stock level for a specific SKU."""
    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    item = inventory[sku]
    return (
        jsonify(
            {
                "sku": sku,
                "name": item.get("name"),
                "price": item.get("price"),
                "stock": item["stock"],
                "reserved": item.get("reserved", 0),
                "available": item["stock"] - item.get("reserved", 0),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/stock/<sku>/replenish", methods=["POST"])
def replenish_stock(sku: str):
    """
    Replenish stock for a SKU.

    Expected payload:
    {
        "quantity": 10,
        "note": "Restock from warehouse"
    }
    """
    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    data = request.get_json() or {}
    quantity = data.get("quantity", 0)
    note = data.get("note", "Manual replenishment")

    if quantity <= 0:
        abort(400, description="Quantity must be greater than 0")

    item = inventory[sku]
    old_stock = item["stock"]
    item["stock"] += quantity

    log_stock_change(sku, old_stock, item["stock"], quantity, "replenish")
    publish_stock_event("inventory.replenished", sku, quantity, item["stock"])

    return (
        jsonify(
            {
                "status": "replenished",
                "sku": sku,
                "old_stock": old_stock,
                "new_stock": item["stock"],
                "quantity": quantity,
                "note": note,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/stock/<sku>/reserve", methods=["POST"])
def reserve_stock(sku: str):
    """
    Reserve stock for a pending order.

    Expected payload:
    {
        "quantity": 2,
        "order_id": "order-12345"
    }
    """
    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    data = request.get_json() or {}
    quantity = data.get("quantity", 0)
    order_id = data.get("order_id", f"order-{int(time.time())}")

    if quantity <= 0:
        abort(400, description="Quantity must be greater than 0")

    item = inventory[sku]
    available = item["stock"] - item.get("reserved", 0)

    if quantity > available:
        abort(
            409,
            description=f"Insufficient stock for {sku}. Available: {available}, Requested: {quantity}",
        )

    item["reserved"] = item.get("reserved", 0) + quantity

    log_stock_change(sku, item["stock"], item["stock"], quantity, "reserve", order_id)

    return (
        jsonify(
            {
                "status": "reserved",
                "sku": sku,
                "quantity": quantity,
                "available": item["stock"] - item.get("reserved", 0),
                "order_id": order_id,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/stock/<sku>/decrement", methods=["POST"])
def decrement_stock(sku: str):
    """
    Decrement stock on sale (with oversell prevention).

    Expected payload:
    {
        "quantity": 2,
        "transaction_id": "txn-12345"
    }
    """
    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    data = request.get_json() or {}
    quantity = data.get("quantity", 0)
    transaction_id = data.get("transaction_id", f"txn-{int(time.time())}")

    if quantity <= 0:
        abort(400, description="Quantity must be greater than 0")

    item = inventory[sku]
    available = item["stock"] - item.get("reserved", 0)

    # Oversell prevention: check if enough stock is available
    if quantity > available:
        return (
            jsonify(
                {
                    "status": "rejected",
                    "sku": sku,
                    "requested": quantity,
                    "available": available,
                    "stock": item["stock"],
                    "reserved": item.get("reserved", 0),
                    "reason": "Insufficient stock",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            409,
        )

    # Decrement stock
    old_stock = item["stock"]
    item["stock"] -= quantity

    # Clear reserved if this was a sale (partial release)
    if item.get("reserved", 0) > 0:
        # Release some reserved stock
        reserved_used = min(quantity, item.get("reserved", 0))
        item["reserved"] = max(0, item.get("reserved", 0) - reserved_used)

    log_stock_change(sku, old_stock, item["stock"], quantity, "sale", transaction_id)
    publish_stock_event("inventory.decremented", sku, quantity, item["stock"])

    return (
        jsonify(
            {
                "status": "decremented",
                "sku": sku,
                "old_stock": old_stock,
                "new_stock": item["stock"],
                "quantity": quantity,
                "remaining": item["stock"],
                "reserved": item.get("reserved", 0),
                "transaction_id": transaction_id,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )

# simulators/pos-services/inventory/simulator.py
# Add reset endpoint


@app.route("/test/reset", methods=["POST"])
def reset_state():
    """Reset inventory state to initial values."""
    global inventory, stock_change_history

    # Reset inventory to initial state
    inventory = {
        "SKU-1001": {
            "stock": 100,
            "name": "Milk (1 gal)",
            "price": 2.99,
            "reserved": 0,
        },
        "SKU-1002": {
            "stock": 50,
            "name": "Bread (white)",
            "price": 1.49,
            "reserved": 0,
        },
        "SKU-1003": {"stock": 75, "name": "Eggs (dozen)", "price": 3.99, "reserved": 0},
        "SKU-1004": {
            "stock": 30,
            "name": "Chicken Breast (lb)",
            "price": 4.50,
            "reserved": 0,
        },
        "SKU-1005": {
            "stock": 200,
            "name": "Apple (each)",
            "price": 0.99,
            "reserved": 0,
        },
        "SKU-1006": {
            "stock": 60,
            "name": "Orange Juice (64oz)",
            "price": 1.99,
            "reserved": 0,
        },
        "SKU-1007": {
            "stock": 40,
            "name": "Cheese (cheddar, 8oz)",
            "price": 5.49,
            "reserved": 0,
        },
        "SKU-1008": {
            "stock": 80,
            "name": "Butter (salted, 1lb)",
            "price": 2.29,
            "reserved": 0,
        },
        "SKU-1009": {
            "stock": 45,
            "name": "Cereal (family size)",
            "price": 3.29,
            "reserved": 0,
        },
        "SKU-1010": {
            "stock": 25,
            "name": "Coffee (ground, 12oz)",
            "price": 7.99,
            "reserved": 0,
        },
    }
    stock_change_history = []

    logger.info("Inventory state reset to initial")
    return (
        jsonify(
            {
                "status": "reset",
                "service": "inventory",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/stock/<sku>/release-reserved", methods=["POST"])
def release_reserved(sku: str):
    """
    Release reserved stock (e.g., order cancelled).

    Expected payload:
    {
        "quantity": 2,
        "order_id": "order-12345"
    }
    """
    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    data = request.get_json() or {}
    quantity = data.get("quantity", 0)
    order_id = data.get("order_id")

    if quantity <= 0:
        abort(400, description="Quantity must be greater than 0")

    item = inventory[sku]
    current_reserved = item.get("reserved", 0)

    if quantity > current_reserved:
        abort(
            409,
            description=f"Cannot release {quantity}, only {current_reserved} reserved",
        )

    item["reserved"] = current_reserved - quantity

    log_stock_change(
        sku, item["stock"], item["stock"], quantity, "release_reserved", order_id
    )

    return (
        jsonify(
            {
                "status": "released",
                "sku": sku,
                "quantity": quantity,
                "reserved_remaining": item["reserved"],
                "order_id": order_id,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/transaction/history", methods=["GET"])
def get_transaction_history():
    """Get stock change transaction history."""
    limit = request.args.get("limit", 50, type=int)
    sku_filter = request.args.get("sku")

    history = stock_change_history
    if sku_filter:
        history = [h for h in history if h["sku"] == sku_filter]

    history = history[-limit:]

    return (
        jsonify(
            {
                "history": history,
                "count": len(history),
                "total": len(stock_change_history),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/inventory/test/oversell", methods=["POST"])
def test_oversell():
    """
    Oversell negative test.

    Attempts to decrement more stock than available.
    Expected to fail with 409.

    Expected payload:
    {
        "sku": "SKU-1010",
        "quantity": 100  # More than available
    }
    """
    data = request.get_json() or {}
    sku = data.get("sku", "SKU-1010")
    quantity = data.get("quantity", 100)

    if sku not in inventory:
        abort(404, description=f"SKU {sku} not found in inventory")

    available = inventory[sku]["stock"] - inventory[sku].get("reserved", 0)

    result = decrement_stock(sku)
    # We expect this to fail
    if result.status_code == 409:
        return (
            jsonify(
                {
                    "test": "oversell_negative",
                    "sku": sku,
                    "quantity": quantity,
                    "available": available,
                    "result": "rejected_as_expected",
                    "passed": True,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )
    else:
        return (
            jsonify(
                {
                    "test": "oversell_negative",
                    "sku": sku,
                    "quantity": quantity,
                    "available": available,
                    "result": "unexpected_success",
                    "passed": False,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            500,
        )


@app.route("/inventory/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run inventory test scenarios.

    Expected payload:
    {
        "scenario": "stock_lookup" | "decrement_success" | "decrement_failure" | "reserve_release"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "stock_lookup")
    results = {}

    if scenario == "stock_lookup":
        sku = "SKU-1001"
        stock = get_stock(sku)
        results["stock_lookup"] = {
            "sku": sku,
            "found": stock.status_code == 200,
            "stock": stock.json.get("stock") if stock.status_code == 200 else None,
            "passed": stock.status_code == 200,
        }

    elif scenario == "decrement_success":
        sku = "SKU-1001"
        # Ensure there is stock
        inventory[sku]["stock"] = 100
        result = decrement_stock(sku)
        results["decrement_success"] = {
            "sku": sku,
            "status": result.status_code,
            "passed": result.status_code == 200,
        }

    elif scenario == "decrement_failure":
        sku = "SKU-1001"
        # Set stock to 0, then try to decrement
        inventory[sku]["stock"] = 0
        result = decrement_stock(sku)
        results["decrement_failure"] = {
            "sku": sku,
            "status": result.status_code,
            "passed": result.status_code == 409,
        }
        # Restore stock
        inventory[sku]["stock"] = 100

    elif scenario == "reserve_release":
        sku = "SKU-1001"
        # Reserve
        reserve_response = reserve_stock(sku)
        if reserve_response.status_code == 200:
            # Release
            release_response = release_reserved(sku)
            results["reserve_release"] = {
                "sku": sku,
                "reserve_status": reserve_response.status_code,
                "release_status": release_response.status_code,
                "passed": reserve_response.status_code == 200
                and release_response.status_code == 200,
            }
        else:
            results["reserve_release"] = {
                "sku": sku,
                "reserve_status": reserve_response.status_code,
                "release_status": None,
                "passed": False,
            }

    else:
        abort(400, description=f"Unknown scenario: {scenario}")

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "inventory_scenarios",
                "scenario": scenario,
                "results": results,
                "summary": {
                    "total": len(results),
                    "passed": sum(
                        1 for r in results.values() if r.get("passed", False)
                    ),
                    "all_passed": all_passed,
                },
            }
        ),
        200,
    )


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8085))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Inventory Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
