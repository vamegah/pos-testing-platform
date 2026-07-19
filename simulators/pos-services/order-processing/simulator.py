# simulators/pos-services/order-processing/simulator.py

"""
Order Processing Microservice (D2)

Order lifecycle state machine: created → paid → fulfilled/void.
Includes an illegal-transition negative test.

Valid transitions:
  - created → paid
  - created → void
  - paid → fulfilled
  - paid → void
  - fulfilled → (terminal state)

Illegal transitions (rejected):
  - fulfilled → created
  - fulfilled → paid
  - void → created
  - void → paid
  - void → fulfilled
  - paid → created
"""

import os
import json
import logging
import time
import requests
from datetime import datetime
from typing import Dict, Any, Optional, List
from enum import Enum
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
INVENTORY_URL = os.environ.get("INVENTORY_URL", "http://inventory:8085")


# ============================================================
# Order State Machine
# ============================================================


class OrderState(Enum):
    CREATED = "created"
    PAID = "paid"
    FULFILLED = "fulfilled"
    VOID = "void"

    def __str__(self):
        return self.value


class OrderStateMachine:
    """Order state machine with valid transitions."""

    VALID_TRANSITIONS = {
        OrderState.CREATED: [OrderState.PAID, OrderState.VOID],
        OrderState.PAID: [OrderState.FULFILLED, OrderState.VOID],
        OrderState.FULFILLED: [],  # Terminal state
        OrderState.VOID: [],  # Terminal state
    }

    @classmethod
    def is_valid_transition(
        cls, current_state: OrderState, target_state: OrderState
    ) -> bool:
        """Check if a transition is valid."""
        if current_state not in cls.VALID_TRANSITIONS:
            return False
        return target_state in cls.VALID_TRANSITIONS[current_state]

    @classmethod
    def get_next_states(cls, current_state: OrderState) -> List[str]:
        """Get valid next states for a given state."""
        if current_state in cls.VALID_TRANSITIONS:
            return [s.value for s in cls.VALID_TRANSITIONS[current_state]]
        return []


# ============================================================
# Order Storage
# ============================================================
orders = {}  # order_id -> order_data
order_counter = 0
order_history = []  # For auditing


def generate_order_id() -> str:
    """Generate a unique order ID."""
    global order_counter
    order_counter += 1
    return f"ORD-{order_counter:06d}"


def create_order(
    items: List[Dict[str, Any]], customer_id: str = None
) -> Dict[str, Any]:
    """Create a new order."""
    order_id = generate_order_id()
    order = {
        "order_id": order_id,
        "customer_id": customer_id or "guest",
        "items": items,
        "state": OrderState.CREATED.value,
        "created_at": datetime.utcnow().isoformat() + "Z",
        "updated_at": datetime.utcnow().isoformat() + "Z",
        "history": [
            {
                "state": OrderState.CREATED.value,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ],
    }
    orders[order_id] = order
    order_history.append(
        {
            "order_id": order_id,
            "action": "created",
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )
    logger.info(f"Order created: {order_id}")
    return order


def transition_order(
    order_id: str, target_state: OrderState, reason: str = None
) -> Dict[str, Any]:
    """Transition an order to a new state."""
    if order_id not in orders:
        abort(404, description=f"Order {order_id} not found")

    order = orders[order_id]
    current_state = OrderState(order["state"])

    # Check if transition is valid
    if not OrderStateMachine.is_valid_transition(current_state, target_state):
        return {
            "status": "rejected",
            "error": f"Illegal transition: {current_state} → {target_state}",
            "valid_transitions": OrderStateMachine.get_next_states(current_state),
            "order_id": order_id,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

    # Perform the transition
    old_state = order["state"]
    order["state"] = target_state.value
    order["updated_at"] = datetime.utcnow().isoformat() + "Z"
    order["history"].append(
        {
            "state": target_state.value,
            "reason": reason,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )

    order_history.append(
        {
            "order_id": order_id,
            "action": f"transition: {old_state} → {target_state.value}",
            "reason": reason,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
    )

    # Publish event if order is completed
    if target_state == OrderState.FULFILLED:
        publish_order_event("order.fulfilled", order_id, order)
    elif target_state == OrderState.VOID:
        publish_order_event("order.voided", order_id, order)

    logger.info(f"Order {order_id} transitioned: {old_state} → {target_state.value}")

    return {
        "status": "transitioned",
        "order_id": order_id,
        "old_state": old_state,
        "new_state": target_state.value,
        "valid_transitions": OrderStateMachine.get_next_states(target_state),
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }


def publish_order_event(event_type: str, order_id: str, order_data: Dict[str, Any]):
    """Publish an order event via the gateway."""
    try:
        event_data = {
            "order_id": order_id,
            "state": order_data["state"],
            "items": order_data["items"],
            "customer_id": order_data["customer_id"],
        }
        requests.post(
            f"{GATEWAY_URL}/gateway/events/publish",
            json={"event_type": event_type, "event_data": event_data},
            timeout=2,
        )
        logger.debug(f"Order event published: {event_type} for {order_id}")
    except Exception as e:
        logger.warning(f"Failed to publish order event: {e}")


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
                "service": "order-processing",
                "orders_count": len(orders),
                "history_count": len(order_history),
            }
        ),
        200,
    )


@app.route("/order/state-machine", methods=["GET"])
def get_state_machine():
    """Get the state machine definition."""
    return (
        jsonify(
            {
                "states": [s.value for s in OrderState],
                "valid_transitions": {
                    state.value: [s.value for s in transitions]
                    for state, transitions in OrderStateMachine.VALID_TRANSITIONS.items()
                },
            }
        ),
        200,
    )


@app.route("/order/create", methods=["POST"])
def create_order_endpoint():
    """
    Create a new order.

    Expected payload:
    {
        "items": [{"sku": "SKU-1001", "quantity": 2, "price": 2.99}],
        "customer_id": "cust-001"
    }
    """
    data = request.get_json() or {}
    items = data.get("items", [])
    customer_id = data.get("customer_id")

    if not items:
        abort(400, description="Missing 'items' field")

    order = create_order(items, customer_id)

    # Reserve inventory
    for item in items:
        try:
            requests.post(
                f"{INVENTORY_URL}/inventory/stock/{item['sku']}/reserve",
                json={
                    "quantity": item.get("quantity", 1),
                    "order_id": order["order_id"],
                },
                timeout=3,
            )
        except Exception as e:
            logger.warning(f"Failed to reserve inventory for {item['sku']}: {e}")

    return jsonify(order), 201


@app.route("/order/<order_id>", methods=["GET"])
def get_order(order_id: str):
    """Get order details."""
    if order_id not in orders:
        abort(404, description=f"Order {order_id} not found")
    return jsonify(orders[order_id]), 200


@app.route("/order/<order_id>/pay", methods=["POST"])
def pay_order(order_id: str):
    """
    Mark an order as paid.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "amount": 10.00
    }
    """
    data = request.get_json() or {}
    transaction_id = data.get("transaction_id")
    amount = data.get("amount")

    if order_id not in orders:
        abort(404, description=f"Order {order_id} not found")

    result = transition_order(order_id, OrderState.PAID, f"Payment: {transaction_id}")

    if result.get("status") == "rejected":
        return jsonify(result), 409

    # Update order with payment info
    order = orders[order_id]
    order["payment"] = {
        "transaction_id": transaction_id,
        "amount": amount,
        "paid_at": datetime.utcnow().isoformat() + "Z",
    }

    return jsonify(result), 200


@app.route("/order/<order_id>/fulfill", methods=["POST"])
def fulfill_order(order_id: str):
    """
    Mark an order as fulfilled.

    Expected payload:
    {
        "shipping_method": "standard",
        "tracking_number": "TRK-12345"
    }
    """
    data = request.get_json() or {}
    shipping_method = data.get("shipping_method", "standard")
    tracking_number = data.get("tracking_number")

    if order_id not in orders:
        abort(404, description=f"Order {order_id} not found")

    result = transition_order(
        order_id, OrderState.FULFILLED, f"Fulfilled: {shipping_method}"
    )

    if result.get("status") == "rejected":
        return jsonify(result), 409

    # Update order with fulfillment info
    order = orders[order_id]
    order["fulfillment"] = {
        "shipping_method": shipping_method,
        "tracking_number": tracking_number,
        "fulfilled_at": datetime.utcnow().isoformat() + "Z",
    }

    # Decrement inventory
    for item in order.get("items", []):
        try:
            requests.post(
                f"{INVENTORY_URL}/inventory/stock/{item['sku']}/decrement",
                json={"quantity": item.get("quantity", 1), "transaction_id": order_id},
                timeout=3,
            )
        except Exception as e:
            logger.warning(f"Failed to decrement inventory for {item['sku']}: {e}")

    return jsonify(result), 200


@app.route("/order/<order_id>/void", methods=["POST"])
def void_order(order_id: str):
    """
    Void an order.

    Expected payload:
    {
        "reason": "Customer requested cancellation"
    }
    """
    data = request.get_json() or {}
    reason = data.get("reason", "Void requested")

    if order_id not in orders:
        abort(404, description=f"Order {order_id} not found")

    result = transition_order(order_id, OrderState.VOID, reason)

    if result.get("status") == "rejected":
        return jsonify(result), 409

    # Release inventory
    order = orders[order_id]
    for item in order.get("items", []):
        try:
            requests.post(
                f"{INVENTORY_URL}/inventory/stock/{item['sku']}/release-reserved",
                json={"quantity": item.get("quantity", 1), "order_id": order_id},
                timeout=3,
            )
        except Exception as e:
            logger.warning(f"Failed to release inventory for {item['sku']}: {e}")

    return jsonify(result), 200


@app.route("/order/history", methods=["GET"])
def get_order_history():
    """Get order history."""
    limit = request.args.get("limit", 50, type=int)
    order_id_filter = request.args.get("order_id")

    history = order_history
    if order_id_filter:
        history = [h for h in history if h["order_id"] == order_id_filter]

    history = history[-limit:]

    return (
        jsonify(
            {"history": history, "count": len(history), "total": len(order_history)}
        ),
        200,
    )


@app.route("/order/test/illegal-transition", methods=["POST"])
def test_illegal_transition():
    """
    Illegal-transition negative test.

    Attempts to perform an illegal transition (e.g., fulfilled → created).
    Expected to fail with 409.
    """
    data = request.get_json() or {}
    from_state = data.get("from_state", "fulfilled")
    to_state = data.get("to_state", "created")

    # Create a test order
    order = create_order(
        [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}], "test-customer"
    )
    order_id = order["order_id"]

    # Move to the starting state
    transitions = {
        "created": lambda: None,
        "paid": lambda: transition_order(order_id, OrderState.PAID, "Test"),
        "fulfilled": lambda: transition_order(order_id, OrderState.FULFILLED, "Test"),
        "void": lambda: transition_order(order_id, OrderState.VOID, "Test"),
    }

    if from_state in transitions:
        transitions[from_state]()

    # Attempt the illegal transition
    target_state = OrderState(to_state)
    result = transition_order(order_id, target_state, "Illegal transition test")

    if result.get("status") == "rejected":
        return (
            jsonify(
                {
                    "test": "illegal_transition_negative",
                    "order_id": order_id,
                    "from_state": from_state,
                    "to_state": to_state,
                    "result": "rejected_as_expected",
                    "valid_transitions": result.get("valid_transitions", []),
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
                    "test": "illegal_transition_negative",
                    "order_id": order_id,
                    "from_state": from_state,
                    "to_state": to_state,
                    "result": "unexpected_success",
                    "passed": False,
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            500,
        )

# simulators/pos-services/order-processing/simulator.py
# Add reset endpoint


@app.route("/test/reset", methods=["POST"])
def reset_state():
    """Reset order state to initial."""
    global orders, order_counter, order_history

    orders = {}
    order_counter = 0
    order_history = []

    logger.info("Order state reset to initial")
    return (
        jsonify(
            {
                "status": "reset",
                "service": "order-processing",
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/order/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run order processing test scenarios.

    Expected payload:
    {
        "scenario": "happy_path" | "illegal_transition" | "void"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "happy_path")
    results = {}

    if scenario == "happy_path":
        # created → paid → fulfilled
        order = create_order(
            [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}], "test-customer"
        )
        order_id = order["order_id"]

        pay_result = transition_order(order_id, OrderState.PAID, "Test payment")
        fulfill_result = transition_order(
            order_id, OrderState.FULFILLED, "Test fulfillment"
        )

        results["happy_path"] = {
            "order_id": order_id,
            "created": True,
            "paid": pay_result.get("status") == "transitioned",
            "fulfilled": fulfill_result.get("status") == "transitioned",
            "passed": pay_result.get("status") == "transitioned"
            and fulfill_result.get("status") == "transitioned",
        }

    elif scenario == "illegal_transition":
        # fulfilled → created (should fail)
        order = create_order(
            [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}], "test-customer"
        )
        order_id = order["order_id"]

        # Go to fulfilled
        transition_order(order_id, OrderState.PAID, "Test")
        transition_order(order_id, OrderState.FULFILLED, "Test")

        # Attempt illegal transition
        illegal_result = transition_order(order_id, OrderState.CREATED, "Illegal test")

        results["illegal_transition"] = {
            "order_id": order_id,
            "attempted": "fulfilled → created",
            "rejected": illegal_result.get("status") == "rejected",
            "passed": illegal_result.get("status") == "rejected",
        }

    elif scenario == "void":
        # created → void
        order = create_order(
            [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}], "test-customer"
        )
        order_id = order["order_id"]

        void_result = transition_order(order_id, OrderState.VOID, "Test void")

        results["void"] = {
            "order_id": order_id,
            "voided": void_result.get("status") == "transitioned",
            "passed": void_result.get("status") == "transitioned",
        }

    else:
        abort(400, description=f"Unknown scenario: {scenario}")

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "order_scenarios",
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
    port = int(os.environ.get("PORT", 8086))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Order Processing Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
