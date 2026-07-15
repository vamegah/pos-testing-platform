# simulators/third-party-mocks/loyalty/simulator.py

"""
Loyalty Program Third-Party Mock (E)

Points accrual/redemption tied to the CRM service, called out-of-process
to simulate a real third-party boundary.

Capabilities:
  - Points accrual on purchase
  - Points redemption (reduce total)
  - Points balance lookup
  - Points expiration
  - Tier-based multipliers
  - Transaction history
"""

import os
import json
import logging
import time
import requests
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
GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://gateway:5014")
CRM_URL = os.environ.get("CRM_URL", "http://crm:8087")

# ============================================================
# Loyalty State
# ============================================================
# Member data: member_id -> {points, tier, history}
members = {}
# Redemption codes
redemption_codes = {}
# Transaction history
transaction_history = []


# ============================================================
# Helper Functions
# ============================================================


def generate_member_id() -> str:
    """Generate a unique member ID."""
    return f"LOY-{int(time.time())}-{os.urandom(4).hex().upper()}"


def get_points_multiplier(tier: str) -> float:
    """Get points multiplier based on tier."""
    multipliers = {
        "standard": 1.0,
        "silver": 1.5,
        "gold": 2.0,
        "platinum": 3.0,
    }
    return multipliers.get(tier.lower(), 1.0)


def accrue_points(
    customer_id: str, amount: float, tier: str = "standard"
) -> Dict[str, Any]:
    """Accrue loyalty points for a purchase."""
    # Get or create member
    if customer_id not in members:
        members[customer_id] = {
            "member_id": generate_member_id(),
            "customer_id": customer_id,
            "points": 0,
            "tier": tier,
            "history": [],
            "created_at": datetime.utcnow().isoformat() + "Z",
        }

    member = members[customer_id]
    multiplier = get_points_multiplier(tier)
    points_earned = int(
        amount * 10 * multiplier
    )  # 10 points per dollar, multiplied by tier

    member["points"] += points_earned
    member["tier"] = tier

    entry = {
        "type": "accrual",
        "amount": amount,
        "points": points_earned,
        "multiplier": multiplier,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    member["history"].append(entry)
    transaction_history.append(
        {
            "customer_id": customer_id,
            "type": "accrual",
            "amount": amount,
            "points": points_earned,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )

    logger.info(
        f"Accrued {points_earned} points for {customer_id} (${amount} at {multiplier}x)"
    )

    return {
        "customer_id": customer_id,
        "points_earned": points_earned,
        "total_points": member["points"],
        "multiplier": multiplier,
        "tier": tier,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }


def redeem_points(
    customer_id: str, points_to_redeem: int, redemption_reason: str = "purchase"
) -> Dict[str, Any]:
    """Redeem loyalty points for a discount."""
    if customer_id not in members:
        abort(404, description=f"Customer {customer_id} not found in loyalty program")

    member = members[customer_id]

    if points_to_redeem > member["points"]:
        abort(
            400,
            description=f"Insufficient points. Available: {member['points']}, Requested: {points_to_redeem}",
        )

    member["points"] -= points_to_redeem

    # Calculate discount value (100 points = $1)
    discount_amount = points_to_redeem / 100.0

    entry = {
        "type": "redemption",
        "points": points_to_redeem,
        "discount": discount_amount,
        "reason": redemption_reason,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    member["history"].append(entry)

    # Generate redemption code
    code = f"REDEEM-{int(time.time())}-{os.urandom(4).hex().upper()}"
    redemption_codes[code] = {
        "customer_id": customer_id,
        "points": points_to_redeem,
        "discount": discount_amount,
        "used": False,
        "created_at": datetime.utcnow().isoformat() + "Z",
    }

    transaction_history.append(
        {
            "customer_id": customer_id,
            "type": "redemption",
            "points": points_to_redeem,
            "discount": discount_amount,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )

    logger.info(
        f"Redeemed {points_to_redeem} points for {customer_id} (${discount_amount} discount)"
    )

    return {
        "customer_id": customer_id,
        "points_redeemed": points_to_redeem,
        "discount": discount_amount,
        "remaining_points": member["points"],
        "redemption_code": code,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }


def get_member_balance(customer_id: str) -> Dict[str, Any]:
    """Get loyalty balance for a customer."""
    if customer_id not in members:
        return {
            "customer_id": customer_id,
            "member": None,
            "points": 0,
            "tier": "standard",
            "exists": False,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

    member = members[customer_id]
    return {
        "customer_id": customer_id,
        "member_id": member["member_id"],
        "points": member["points"],
        "tier": member["tier"],
        "exists": True,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }


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
                "service": "loyalty-mock",
                "members_count": len(members),
                "transactions_count": len(transaction_history),
                "redemption_codes_count": len(redemption_codes),
            }
        ),
        200,
    )


@app.route("/loyalty/accrue", methods=["POST"])
def accrue_points_endpoint():
    """
    Accrue loyalty points on a purchase.

    Expected payload:
    {
        "customer_id": "CUST-000001",
        "amount": 10.00,
        "tier": "silver"
    }
    """
    data = request.get_json() or {}
    customer_id = data.get("customer_id")
    amount = data.get("amount", 0.0)
    tier = data.get("tier", "standard")

    if not customer_id:
        abort(400, description="Missing 'customer_id' field")

    result = accrue_points(customer_id, amount, tier)

    # Publish event via gateway
    try:
        requests.post(
            f"{GATEWAY_URL}/gateway/events/publish",
            json={
                "event_type": "loyalty.points_accrued",
                "event_data": {
                    "customer_id": customer_id,
                    "points": result["points_earned"],
                    "total_points": result["total_points"],
                    "amount": amount,
                },
            },
            timeout=2,
        )
    except Exception as e:
        logger.warning(f"Failed to publish loyalty event: {e}")

    return jsonify(result), 200


@app.route("/loyalty/redeem", methods=["POST"])
def redeem_points_endpoint():
    """
    Redeem loyalty points for a discount.

    Expected payload:
    {
        "customer_id": "CUST-000001",
        "points": 100,
        "reason": "purchase"
    }
    """
    data = request.get_json() or {}
    customer_id = data.get("customer_id")
    points = data.get("points", 0)
    reason = data.get("reason", "purchase")

    if not customer_id:
        abort(400, description="Missing 'customer_id' field")

    if points <= 0:
        abort(400, description="Points must be greater than 0")

    result = redeem_points(customer_id, points, reason)

    # Publish event via gateway
    try:
        requests.post(
            f"{GATEWAY_URL}/gateway/events/publish",
            json={
                "event_type": "loyalty.points_redeemed",
                "event_data": {
                    "customer_id": customer_id,
                    "points": result["points_redeemed"],
                    "discount": result["discount"],
                    "remaining_points": result["remaining_points"],
                },
            },
            timeout=2,
        )
    except Exception as e:
        logger.warning(f"Failed to publish loyalty event: {e}")

    return jsonify(result), 200


@app.route("/loyalty/balance/<customer_id>", methods=["GET"])
def get_balance(customer_id: str):
    """Get loyalty balance for a customer."""
    return jsonify(get_member_balance(customer_id)), 200


@app.route("/loyalty/redemption/validate/<code>", methods=["GET"])
def validate_redemption_code(code: str):
    """Validate a redemption code."""
    if code not in redemption_codes:
        abort(404, description=f"Redemption code {code} not found")

    redemption = redemption_codes[code]
    if redemption["used"]:
        abort(409, description=f"Redemption code {code} already used")

    return (
        jsonify(
            {
                "code": code,
                "customer_id": redemption["customer_id"],
                "points": redemption["points"],
                "discount": redemption["discount"],
                "valid": True,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/loyalty/redemption/use", methods=["POST"])
def use_redemption_code():
    """
    Use a redemption code.

    Expected payload:
    {
        "code": "REDEEM-12345-ABCD"
    }
    """
    data = request.get_json() or {}
    code = data.get("code")

    if not code:
        abort(400, description="Missing 'code' field")

    if code not in redemption_codes:
        abort(404, description=f"Redemption code {code} not found")

    redemption = redemption_codes[code]
    if redemption["used"]:
        abort(409, description=f"Redemption code {code} already used")

    redemption["used"] = True
    redemption["used_at"] = datetime.utcnow().isoformat() + "Z"

    return (
        jsonify(
            {
                "code": code,
                "customer_id": redemption["customer_id"],
                "points": redemption["points"],
                "discount": redemption["discount"],
                "used": True,
                "used_at": redemption["used_at"],
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/loyalty/history/<customer_id>", methods=["GET"])
def get_member_history(customer_id: str):
    """Get loyalty transaction history for a customer."""
    if customer_id not in members:
        abort(404, description=f"Customer {customer_id} not found in loyalty program")

    member = members[customer_id]
    return (
        jsonify(
            {
                "customer_id": customer_id,
                "member_id": member["member_id"],
                "history": member["history"],
                "count": len(member["history"]),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/loyalty/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run loyalty test scenarios.

    Expected payload:
    {
        "scenario": "accrue" | "redeem" | "redeem_insufficient" | "all"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "all")
    results = {}

    if scenario in ["accrue", "all"]:
        # Accrue points scenario
        customer_id = f"CUST-ACC-{int(time.time())}"
        # Create customer in CRM first
        try:
            requests.post(
                f"{CRM_URL}/crm/customer",
                json={
                    "name": "Loyalty Test",
                    "email": f"{customer_id}@test.com",
                    "phone": "555-0000",
                },
                timeout=2,
            )
        except:
            pass

        result = accrue_points(customer_id, 25.00, "silver")
        results["accrue"] = {
            "customer_id": customer_id,
            "points_earned": result["points_earned"],
            "total_points": result["total_points"],
            "passed": result["points_earned"] == 375,  # 25 * 10 * 1.5 = 375
        }

    if scenario in ["redeem", "all"]:
        # Redeem points scenario
        customer_id = f"CUST-RED-{int(time.time())}"
        try:
            requests.post(
                f"{CRM_URL}/crm/customer",
                json={
                    "name": "Redemption Test",
                    "email": f"{customer_id}@test.com",
                    "phone": "555-0001",
                },
                timeout=2,
            )
        except:
            pass

        # Accrue some points first
        accrue_points(customer_id, 50.00, "gold")

        # Redeem
        result = redeem_points(customer_id, 100, "test redemption")
        results["redeem"] = {
            "customer_id": customer_id,
            "points_redeemed": result["points_redeemed"],
            "remaining_points": result["remaining_points"],
            "discount": result["discount"],
            "passed": result["points_redeemed"] == 100,
        }

    if scenario in ["redeem_insufficient", "all"]:
        # Insufficient points scenario
        customer_id = f"CUST-INS-{int(time.time())}"
        try:
            requests.post(
                f"{CRM_URL}/crm/customer",
                json={
                    "name": "Insufficient Test",
                    "email": f"{customer_id}@test.com",
                    "phone": "555-0002",
                },
                timeout=2,
            )
        except:
            pass

        # Accrue some points
        accrue_points(customer_id, 5.00, "standard")

        # Attempt to redeem more than available
        try:
            result = redeem_points(customer_id, 1000, "test")
            results["redeem_insufficient"] = {
                "customer_id": customer_id,
                "passed": False,
                "error": "Should have failed but succeeded",
            }
        except Exception as e:
            results["redeem_insufficient"] = {
                "customer_id": customer_id,
                "passed": True,
                "error": str(e),
            }

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "loyalty_scenarios",
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
    port = int(os.environ.get("PORT", 8089))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Loyalty Mock Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
