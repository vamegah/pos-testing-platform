# simulators/pos-services/crm/simulator.py

"""
CRM Microservice (D2)

Customer profile lookup/creation tied to a transaction.

Capabilities:
  - Customer lookup by ID
  - Customer lookup by email/phone
  - Customer creation (guest/new profile)
  - Transaction history per customer
  - Loyalty tier management
  - Customer profile enrichment
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
LOYALTY_URL = os.environ.get("LOYALTY_URL", "http://loyalty:8087")


# ============================================================
# Customer Database
# ============================================================
customers = {}  # customer_id -> customer_data
customer_counter = 0
transaction_history = []  # customer_id -> list of transactions


def generate_customer_id() -> str:
    """Generate a unique customer ID."""
    global customer_counter
    customer_counter += 1
    return f"CUST-{customer_counter:06d}"


def create_customer(profile: Dict[str, Any]) -> Dict[str, Any]:
    """Create a new customer profile."""
    customer_id = generate_customer_id()
    customer = {
        "customer_id": customer_id,
        "name": profile.get("name", "Guest"),
        "email": profile.get("email", f"guest_{customer_id}@example.com"),
        "phone": profile.get("phone", ""),
        "loyalty_tier": profile.get("loyalty_tier", "standard"),
        "loyalty_points": profile.get("loyalty_points", 0),
        "created_at": datetime.utcnow().isoformat() + "Z",
        "last_activity": datetime.utcnow().isoformat() + "Z",
        "transaction_count": 0,
        "total_spent": 0.0,
    }
    customers[customer_id] = customer
    logger.info(f"Customer created: {customer_id} ({customer['name']})")
    return customer


def find_customer_by_email(email: str) -> Optional[Dict[str, Any]]:
    """Find a customer by email."""
    for customer in customers.values():
        if customer.get("email", "").lower() == email.lower():
            return customer
    return None


def find_customer_by_phone(phone: str) -> Optional[Dict[str, Any]]:
    """Find a customer by phone."""
    for customer in customers.values():
        if customer.get("phone", "") == phone:
            return customer
    return None


def find_customer_by_id(customer_id: str) -> Optional[Dict[str, Any]]:
    """Find a customer by ID."""
    return customers.get(customer_id)


def record_transaction(customer_id: str, transaction_data: Dict[str, Any]):
    """Record a transaction for a customer."""
    entry = {
        "customer_id": customer_id,
        "transaction_id": transaction_data.get("transaction_id"),
        "amount": transaction_data.get("amount", 0.0),
        "items": transaction_data.get("items", []),
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    transaction_history.append(entry)

    # Update customer stats
    if customer_id in customers:
        customer = customers[customer_id]
        customer["transaction_count"] += 1
        customer["total_spent"] += entry["amount"]
        customer["last_activity"] = datetime.utcnow().isoformat() + "Z"

        # Update loyalty tier based on spending
        if customer["total_spent"] >= 1000 and customer["loyalty_tier"] == "standard":
            customer["loyalty_tier"] = "silver"
            customer["loyalty_points"] += 100
            logger.info(f"Customer {customer_id} promoted to silver")
        elif customer["total_spent"] >= 5000 and customer["loyalty_tier"] == "silver":
            customer["loyalty_tier"] = "gold"
            customer["loyalty_points"] += 200
            logger.info(f"Customer {customer_id} promoted to gold")

    logger.info(f"Transaction recorded for customer {customer_id}: ${entry['amount']}")


def publish_customer_event(event_type: str, customer_id: str, data: Dict[str, Any]):
    """Publish a customer event via the gateway."""
    try:
        event_data = {
            "customer_id": customer_id,
            "data": data,
        }
        requests.post(
            f"{GATEWAY_URL}/gateway/events/publish",
            json={"event_type": event_type, "event_data": event_data},
            timeout=2,
        )
        logger.debug(f"Customer event published: {event_type} for {customer_id}")
    except Exception as e:
        logger.warning(f"Failed to publish customer event: {e}")


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
                "service": "crm-service",
                "customers_count": len(customers),
                "transactions_count": len(transaction_history),
            }
        ),
        200,
    )


@app.route("/crm/customer", methods=["POST"])
def create_customer_endpoint():
    """
    Create a new customer.

    Expected payload:
    {
        "name": "John Doe",
        "email": "john@example.com",
        "phone": "555-1234",
        "loyalty_tier": "standard"
    }
    """
    data = request.get_json() or {}

    # Check for existing customer by email
    email = data.get("email")
    if email:
        existing = find_customer_by_email(email)
        if existing:
            return (
                jsonify(
                    {
                        "status": "exists",
                        "customer": existing,
                        "message": "Customer already exists",
                        "timestamp": datetime.utcnow().isoformat() + "Z",
                    }
                ),
                200,
            )

    customer = create_customer(data)
    publish_customer_event("customer.created", customer["customer_id"], customer)

    return (
        jsonify(
            {
                "status": "created",
                "customer": customer,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        201,
    )


@app.route("/crm/customer/<customer_id>", methods=["GET"])
def get_customer(customer_id: str):
    """Get customer details."""
    customer = find_customer_by_id(customer_id)
    if not customer:
        abort(404, description=f"Customer {customer_id} not found")
    return jsonify(customer), 200


@app.route("/crm/customer/lookup", methods=["POST"])
def lookup_customer():
    """
    Lookup a customer by email or phone.

    Expected payload:
    {
        "email": "john@example.com",
        "phone": "555-1234"
    }
    """
    data = request.get_json() or {}
    email = data.get("email")
    phone = data.get("phone")

    if not email and not phone:
        abort(400, description="Either 'email' or 'phone' must be provided")

    customer = None
    if email:
        customer = find_customer_by_email(email)
    if not customer and phone:
        customer = find_customer_by_phone(phone)

    if not customer:
        return (
            jsonify(
                {
                    "status": "not_found",
                    "message": "Customer not found",
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            404,
        )

    return (
        jsonify(
            {
                "status": "found",
                "customer": customer,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/crm/customer/<customer_id>/update", methods=["PUT"])
def update_customer(customer_id: str):
    """
    Update customer profile.

    Expected payload:
    {
        "name": "John Doe",
        "email": "john@example.com",
        "phone": "555-1234"
    }
    """
    if customer_id not in customers:
        abort(404, description=f"Customer {customer_id} not found")

    data = request.get_json() or {}
    customer = customers[customer_id]

    if "name" in data:
        customer["name"] = data["name"]
    if "email" in data:
        customer["email"] = data["email"]
    if "phone" in data:
        customer["phone"] = data["phone"]

    customer["updated_at"] = datetime.utcnow().isoformat() + "Z"

    publish_customer_event("customer.updated", customer_id, customer)

    return (
        jsonify(
            {
                "status": "updated",
                "customer": customer,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/crm/customer/<customer_id>/transaction", methods=["POST"])
def record_customer_transaction(customer_id: str):
    """
    Record a transaction for a customer.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "amount": 10.00,
        "items": [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}]
    }
    """
    if customer_id not in customers:
        abort(404, description=f"Customer {customer_id} not found")

    data = request.get_json() or {}
    transaction_id = data.get("transaction_id", f"txn-{int(time.time())}")
    amount = data.get("amount", 0.0)
    items = data.get("items", [])

    record_transaction(
        customer_id,
        {
            "transaction_id": transaction_id,
            "amount": amount,
            "items": items,
        },
    )

    # Call loyalty service if available
    customer = customers[customer_id]
    if customer.get("loyalty_points", 0) > 0:
        try:
            requests.post(
                f"{LOYALTY_URL}/loyalty/accrue",
                json={"customer_id": customer_id, "amount": amount},
                timeout=2,
            )
        except Exception as e:
            logger.warning(f"Failed to call loyalty service: {e}")

    publish_customer_event(
        "customer.transaction",
        customer_id,
        {
            "transaction_id": transaction_id,
            "amount": amount,
            "items": items,
        },
    )

    return (
        jsonify(
            {
                "status": "recorded",
                "customer_id": customer_id,
                "transaction_id": transaction_id,
                "amount": amount,
                "customer_stats": {
                    "transaction_count": customer["transaction_count"],
                    "total_spent": customer["total_spent"],
                    "loyalty_tier": customer["loyalty_tier"],
                    "loyalty_points": customer["loyalty_points"],
                },
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/crm/customer/<customer_id>/transactions", methods=["GET"])
def get_customer_transactions(customer_id: str):
    """Get transaction history for a customer."""
    if customer_id not in customers:
        abort(404, description=f"Customer {customer_id} not found")

    limit = request.args.get("limit", 50, type=int)
    history = [t for t in transaction_history if t["customer_id"] == customer_id]
    history = history[-limit:]

    customer = customers[customer_id]

    return (
        jsonify(
            {
                "customer_id": customer_id,
                "customer_name": customer["name"],
                "transactions": history,
                "count": len(history),
                "total_spent": customer["total_spent"],
                "transaction_count": customer["transaction_count"],
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/crm/customers", methods=["GET"])
def get_all_customers():
    """Get all customers (limited)."""
    limit = request.args.get("limit", 20, type=int)
    all_customers = list(customers.values())
    all_customers = all_customers[-limit:]

    return (
        jsonify(
            {
                "customers": all_customers,
                "count": len(all_customers),
                "total": len(customers),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/crm/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run CRM test scenarios.

    Expected payload:
    {
        "scenario": "create_customer" | "lookup_existing" | "lookup_guest" | "record_transaction"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "create_customer")
    results = {}

    if scenario == "create_customer":
        # Create a new customer
        customer_data = {
            "name": "Test User",
            "email": "test@example.com",
            "phone": "555-1234",
            "loyalty_tier": "standard",
        }
        response = create_customer(customer_data)
        results["create_customer"] = {
            "customer_id": response.get("customer_id"),
            "name": response.get("name"),
            "passed": response is not None,
        }

    elif scenario == "lookup_existing":
        # Create a customer then look them up
        customer_data = {
            "name": "Lookup Test",
            "email": "lookup@example.com",
            "phone": "555-5678",
        }
        customer = create_customer(customer_data)
        found = find_customer_by_email("lookup@example.com")
        results["lookup_existing"] = {
            "customer_id": customer.get("customer_id"),
            "found": found is not None,
            "passed": found is not None,
        }

    elif scenario == "lookup_guest":
        # Look up a non-existent customer
        found = find_customer_by_email("nonexistent@example.com")
        results["lookup_guest"] = {
            "found": found is not None,
            "expected": False,
            "passed": found is None,
        }

    elif scenario == "record_transaction":
        # Create a customer and record a transaction
        customer = create_customer(
            {
                "name": "Transaction Test",
                "email": "transaction@example.com",
                "phone": "555-9999",
            }
        )
        customer_id = customer.get("customer_id")
        record_transaction(
            customer_id,
            {
                "transaction_id": "txn-test-001",
                "amount": 10.00,
                "items": [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}],
            },
        )
        updated = find_customer_by_id(customer_id)
        results["record_transaction"] = {
            "customer_id": customer_id,
            "transaction_count": updated.get("transaction_count", 0),
            "total_spent": updated.get("total_spent", 0),
            "passed": updated.get("transaction_count", 0) == 1,
        }

    else:
        abort(400, description=f"Unknown scenario: {scenario}")

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "crm_scenarios",
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
# Initialization - Seed some customers
# ============================================================


def seed_customers():
    """Seed some initial customers."""
    seed_data = [
        {
            "name": "Alice Johnson",
            "email": "alice@example.com",
            "phone": "555-0001",
            "loyalty_tier": "gold",
            "loyalty_points": 500,
        },
        {
            "name": "Bob Smith",
            "email": "bob@example.com",
            "phone": "555-0002",
            "loyalty_tier": "silver",
            "loyalty_points": 200,
        },
        {
            "name": "Carol White",
            "email": "carol@example.com",
            "phone": "555-0003",
            "loyalty_tier": "standard",
            "loyalty_points": 50,
        },
    ]
    for data in seed_data:
        create_customer(data)
    logger.info(f"Seeded {len(seed_data)} customers")


# Run seeding on startup
seed_customers()


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8087))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"CRM Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
