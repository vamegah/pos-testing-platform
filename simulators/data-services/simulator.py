# simulators/data-services/simulator.py

"""
Data Services Layer (D3)

Transactional persistence check (an order write is retrievable afterward)
+ a lightweight analytics/data-lake event sink, with a test verifying
one analytics event per completed sale.

Capabilities:
  - Order persistence: write/read orders
  - Analytics event sink: collect events from completed sales
  - Event count verification: one event per completed sale
  - Data lake simulation: analytics data storage
"""

import os
import json
import logging
import time
import requests
import sqlite3
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
DB_PATH = os.environ.get("DATA_SERVICES_DB_PATH", "data_services.db")

# ============================================================
# Database Setup
# ============================================================


def get_db() -> sqlite3.Connection:
    """Get a database connection."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    """Initialize the database schema."""
    with get_db() as conn:
        cursor = conn.cursor()

        # Orders table (transactional persistence)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS orders (
                order_id TEXT PRIMARY KEY,
                customer_id TEXT NOT NULL,
                items TEXT NOT NULL,
                total REAL NOT NULL,
                state TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                payment_transaction_id TEXT,
                amount REAL,
                fulfilled_at TIMESTAMP
            )
        """)

        # Order items table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id TEXT NOT NULL,
                sku TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                price REAL NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders(order_id)
            )
        """)

        # Analytics events table (data lake)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS analytics_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                order_id TEXT,
                customer_id TEXT,
                event_data TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                processed BOOLEAN DEFAULT 0
            )
        """)

        # Analytics summaries
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS analytics_summary (
                metric_name TEXT PRIMARY KEY,
                value INTEGER DEFAULT 0,
                last_updated TIMESTAMP NOT NULL
            )
        """)

        conn.commit()
        logger.info("Data Services database initialized")


# ============================================================
# Helper Functions
# ============================================================


def generate_event_id() -> str:
    """Generate a unique event ID."""
    return f"EVT-{int(time.time())}-{os.urandom(4).hex().upper()}"


def store_order(order_data: Dict[str, Any]):
    """Store an order in the database."""
    with get_db() as conn:
        cursor = conn.cursor()

        # Insert order
        cursor.execute(
            """
            INSERT OR REPLACE INTO orders 
            (order_id, customer_id, items, total, state, created_at, updated_at,
             payment_transaction_id, amount, fulfilled_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
            (
                order_data.get("order_id"),
                order_data.get("customer_id", "guest"),
                json.dumps(order_data.get("items", [])),
                order_data.get("total", 0.0),
                order_data.get("state", "created"),
                order_data.get("created_at", datetime.utcnow().isoformat() + "Z"),
                order_data.get("updated_at", datetime.utcnow().isoformat() + "Z"),
                order_data.get("payment_transaction_id"),
                order_data.get("amount"),
                order_data.get("fulfilled_at"),
            ),
        )

        # Insert order items
        for item in order_data.get("items", []):
            cursor.execute(
                """
                INSERT INTO order_items (order_id, sku, quantity, price)
                VALUES (?, ?, ?, ?)
            """,
                (
                    order_data.get("order_id"),
                    item.get("sku"),
                    item.get("quantity", 1),
                    item.get("price", 0.0),
                ),
            )

        conn.commit()
        logger.info(f"Order stored: {order_data.get('order_id')}")


def get_order_by_id(order_id: str) -> Optional[Dict[str, Any]]:
    """Retrieve an order by ID."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM orders WHERE order_id = ?", (order_id,))
        row = cursor.fetchone()
        if not row:
            return None

        order = dict(row)
        order["items"] = json.loads(order["items"])

        # Get order items
        cursor.execute("SELECT * FROM order_items WHERE order_id = ?", (order_id,))
        rows = cursor.fetchall()
        order["order_items"] = [dict(row) for row in rows]

        return order


def store_analytics_event(
    event_type: str, order_id: str, customer_id: str, event_data: Dict[str, Any]
):
    """Store an analytics event."""
    event_id = generate_event_id()
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO analytics_events 
            (event_id, event_type, order_id, customer_id, event_data, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """,
            (
                event_id,
                event_type,
                order_id,
                customer_id,
                json.dumps(event_data),
                datetime.utcnow().isoformat() + "Z",
            ),
        )
        conn.commit()

        # Update summary metrics
        cursor.execute("""
            INSERT INTO analytics_summary (metric_name, value, last_updated)
            VALUES ('total_events', 1, datetime('now'))
            ON CONFLICT(metric_name) DO UPDATE SET 
                value = value + 1,
                last_updated = datetime('now')
        """)

        if event_type == "sale_completed":
            cursor.execute("""
                INSERT INTO analytics_summary (metric_name, value, last_updated)
                VALUES ('completed_sales', 1, datetime('now'))
                ON CONFLICT(metric_name) DO UPDATE SET 
                    value = value + 1,
                    last_updated = datetime('now')
            """)

        conn.commit()
        logger.info(f"Analytics event stored: {event_type} (id={event_id})")


def get_analytics_summary() -> Dict[str, int]:
    """Get analytics summary metrics."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT metric_name, value FROM analytics_summary")
        rows = cursor.fetchall()
        return {row["metric_name"]: row["value"] for row in rows}


# ============================================================
# Endpoints
# ============================================================


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    summary = get_analytics_summary()
    return (
        jsonify(
            {
                "status": "healthy",
                "service": "data-services",
                "db_path": DB_PATH,
                "analytics_summary": summary,
            }
        ),
        200,
    )


@app.route("/data-services/order", methods=["POST"])
def store_order_endpoint():
    """
    Store an order (transactional persistence).

    Expected payload: Order data (same as order-processing order)
    """
    data = request.get_json() or {}
    if not data or "order_id" not in data:
        abort(400, description="Missing 'order_id' in order data")

    store_order(data)

    return (
        jsonify(
            {
                "status": "stored",
                "order_id": data["order_id"],
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        201,
    )


@app.route("/data-services/order/<order_id>", methods=["GET"])
def get_order_endpoint(order_id: str):
    """Retrieve an order by ID (persistence check)."""
    order = get_order_by_id(order_id)
    if not order:
        abort(404, description=f"Order {order_id} not found")

    return (
        jsonify(
            {
                "status": "found",
                "order": order,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/data-services/analytics/event", methods=["POST"])
def store_analytics_event_endpoint():
    """
    Store an analytics event.

    Expected payload:
    {
        "event_type": "sale_completed",
        "order_id": "ORD-000001",
        "customer_id": "CUST-000001",
        "event_data": {"amount": 10.00, "items": [...]}
    }
    """
    data = request.get_json() or {}
    event_type = data.get("event_type")
    order_id = data.get("order_id")
    customer_id = data.get("customer_id", "guest")
    event_data = data.get("event_data", {})

    if not event_type:
        abort(400, description="Missing 'event_type' field")

    store_analytics_event(event_type, order_id, customer_id, event_data)

    return (
        jsonify(
            {
                "status": "stored",
                "event_type": event_type,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        201,
    )


@app.route("/data-services/analytics/events", methods=["GET"])
def get_analytics_events():
    """Get analytics events."""
    limit = request.args.get("limit", 50, type=int)
    event_type = request.args.get("event_type")
    order_id = request.args.get("order_id")

    with get_db() as conn:
        cursor = conn.cursor()
        query = "SELECT * FROM analytics_events"
        params = []

        conditions = []
        if event_type:
            conditions.append("event_type = ?")
            params.append(event_type)
        if order_id:
            conditions.append("order_id = ?")
            params.append(order_id)

        if conditions:
            query += " WHERE " + " AND ".join(conditions)

        query += " ORDER BY created_at DESC LIMIT ?"
        params.append(limit)

        cursor.execute(query, params)
        rows = cursor.fetchall()
        events = [dict(row) for row in rows]

        return (
            jsonify(
                {
                    "events": events,
                    "count": len(events),
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )


@app.route("/data-services/analytics/summary", methods=["GET"])
def get_analytics_summary_endpoint():
    """Get analytics summary."""
    summary = get_analytics_summary()
    return (
        jsonify({"summary": summary, "timestamp": datetime.utcnow().isoformat() + "Z"}),
        200,
    )


@app.route("/data-services/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run data services test scenarios.

    Expected payload:
    {
        "scenario": "persistence" | "analytics_sink" | "all"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "all")
    results = {}

    if scenario in ["persistence", "all"]:
        # Persistence test: write order, read it back
        test_order = {
            "order_id": f"ORD-TEST-{int(time.time())}",
            "customer_id": "CUST-TEST-001",
            "items": [{"sku": "SKU-1001", "quantity": 2, "price": 2.99}],
            "total": 5.98,
            "state": "created",
            "created_at": datetime.utcnow().isoformat() + "Z",
            "updated_at": datetime.utcnow().isoformat() + "Z",
        }

        # Write
        store_order(test_order)

        # Read
        retrieved = get_order_by_id(test_order["order_id"])

        results["persistence"] = {
            "order_id": test_order["order_id"],
            "written": True,
            "retrieved": retrieved is not None,
            "passed": retrieved is not None,
        }

    if scenario in ["analytics_sink", "all"]:
        # Analytics test: store events and verify count
        before_events = get_analytics_summary().get("total_events", 0)

        # Store a sale_completed event
        store_analytics_event(
            "sale_completed",
            f"ORD-TEST-{int(time.time())}",
            "CUST-TEST-001",
            {"amount": 10.00, "items": []},
        )

        after_events = get_analytics_summary().get("total_events", 0)
        after_sales = get_analytics_summary().get("completed_sales", 0)

        results["analytics_sink"] = {
            "events_before": before_events,
            "events_after": after_events,
            "sales_count": after_sales,
            "passed": after_events > before_events,
        }

    if scenario == "event_count_verification":
        # Specific test: one analytics event per completed sale
        # Store multiple completed sales
        completed_sales_before = get_analytics_summary().get("completed_sales", 0)

        for i in range(3):
            store_analytics_event(
                "sale_completed",
                f"ORD-COUNT-{i}",
                "CUST-TEST-001",
                {"amount": 10.00 + i, "items": []},
            )

        completed_sales_after = get_analytics_summary().get("completed_sales", 0)
        events_delta = completed_sales_after - completed_sales_before

        results["event_count_verification"] = {
            "sales_added": 3,
            "events_delta": events_delta,
            "passed": events_delta == 3,
        }

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "data_services_scenarios",
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
# Initialization
# ============================================================

init_db()

# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8088))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Data Services starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
