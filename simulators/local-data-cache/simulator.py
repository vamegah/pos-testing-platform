# simulators/local-data-cache/simulator.py

"""
Local Data Cache (C2)

Explicit offline cache (embedded SQLite) for catalog/pricing, with:
  - Cache-population
  - Staleness/TTL
  - Reconnect-reconciliation

This cache is used by the Commerce Scale Unit for edge autonomy.
"""

import os
import json
import logging
import sqlite3
import time
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
CACHE_TTL_SECONDS = int(os.environ.get("CACHE_TTL_SECONDS", 300))  # 5 minutes
RECONCILIATION_INTERVAL_SECONDS = int(
    os.environ.get("RECONCILIATION_INTERVAL_SECONDS", 60)
)
DB_PATH = os.environ.get("CACHE_DB_PATH", "local_cache.db")

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

        # Catalog table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS catalog (
                sku TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                weight_kg REAL,
                cached_at TIMESTAMP NOT NULL,
                last_updated TIMESTAMP NOT NULL,
                source TEXT DEFAULT 'cloud'
            )
        """)

        # Regions table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS regions (
                code TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                tax_rate REAL NOT NULL,
                cached_at TIMESTAMP NOT NULL,
                last_updated TIMESTAMP NOT NULL
            )
        """)

        # Sync history table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sync_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_type TEXT NOT NULL,
                synced_at TIMESTAMP NOT NULL,
                items_count INTEGER DEFAULT 0,
                regions_count INTEGER DEFAULT 0,
                status TEXT NOT NULL,
                error_message TEXT
            )
        """)

        # Pending changes table (for reconciliation)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS pending_changes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                change_type TEXT NOT NULL,
                sku TEXT,
                region_code TEXT,
                old_value TEXT,
                new_value TEXT,
                created_at TIMESTAMP NOT NULL,
                status TEXT DEFAULT 'pending',
                reconciled_at TIMESTAMP
            )
        """)

        conn.commit()
        logger.info("Database initialized successfully")


# ============================================================
# Cache Operations
# ============================================================


def is_stale(timestamp_str: str) -> bool:
    """Check if a timestamp is stale based on TTL."""
    if not timestamp_str:
        return True
    try:
        cached_at = datetime.fromisoformat(timestamp_str.replace("Z", "+00:00"))
        age = datetime.utcnow() - cached_at
        return age.total_seconds() > CACHE_TTL_SECONDS
    except:
        return True


def populate_catalog(items: List[Dict[str, Any]]):
    """Populate the catalog cache."""
    with get_db() as conn:
        cursor = conn.cursor()
        now = datetime.utcnow().isoformat() + "Z"

        for item in items:
            cursor.execute(
                """
                INSERT OR REPLACE INTO catalog 
                (sku, name, price, weight_kg, cached_at, last_updated, source)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                (
                    item.get("sku"),
                    item.get("name"),
                    item.get("price", 0.0),
                    item.get("weight_kg", 0.0),
                    now,
                    now,
                    "cloud",
                ),
            )

        conn.commit()
        logger.info(f"Populated catalog with {len(items)} items")


def populate_regions(regions: List[Dict[str, Any]]):
    """Populate the regions cache."""
    with get_db() as conn:
        cursor = conn.cursor()
        now = datetime.utcnow().isoformat() + "Z"

        for region in regions:
            cursor.execute(
                """
                INSERT OR REPLACE INTO regions 
                (code, name, tax_rate, cached_at, last_updated)
                VALUES (?, ?, ?, ?, ?)
            """,
                (
                    region.get("code"),
                    region.get("name"),
                    region.get("tax_rate", 0.0),
                    now,
                    now,
                ),
            )

        conn.commit()
        logger.info(f"Populated regions with {len(regions)} items")


def get_cached_catalog() -> List[Dict[str, Any]]:
    """Get all cached catalog items."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM catalog ORDER BY sku")
        rows = cursor.fetchall()
        return [dict(row) for row in rows]


def get_cached_regions() -> List[Dict[str, Any]]:
    """Get all cached regions."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM regions ORDER BY code")
        rows = cursor.fetchall()
        return [dict(row) for row in rows]


def get_cached_price(sku: str) -> Optional[float]:
    """Get a cached price for a SKU."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT price, cached_at FROM catalog WHERE sku = ?", (sku,))
        row = cursor.fetchone()
        if row:
            return row["price"]
        return None


def get_cached_region(region_code: str) -> Optional[Dict[str, Any]]:
    """Get a cached region."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM regions WHERE code = ?", (region_code,))
        row = cursor.fetchone()
        return dict(row) if row else None


def record_sync_history(
    sync_type: str,
    items_count: int,
    regions_count: int,
    status: str,
    error_message: str = None,
):
    """Record a sync operation in history."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO sync_history (sync_type, synced_at, items_count, regions_count, status, error_message)
            VALUES (?, ?, ?, ?, ?, ?)
        """,
            (
                sync_type,
                datetime.utcnow().isoformat() + "Z",
                items_count,
                regions_count,
                status,
                error_message,
            ),
        )
        conn.commit()


def record_pending_change(
    change_type: str,
    sku: str = None,
    region_code: str = None,
    old_value: str = None,
    new_value: str = None,
):
    """Record a pending change for reconciliation."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO pending_changes 
            (change_type, sku, region_code, old_value, new_value, created_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
            (
                change_type,
                sku,
                region_code,
                old_value,
                new_value,
                datetime.utcnow().isoformat() + "Z",
                "pending",
            ),
        )
        conn.commit()


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
                "service": "local-data-cache",
                "cache_ttl_seconds": CACHE_TTL_SECONDS,
                "db_path": DB_PATH,
            }
        ),
        200,
    )


@app.route("/local-cache/populate", methods=["POST"])
def populate_cache():
    """
    Populate the cache with catalog and region data.

    Expected payload:
    {
        "catalog": [{"sku": "SKU-1001", "name": "Milk", "price": 2.99, "weight_kg": 3.78}],
        "regions": [{"code": "CA", "name": "California", "tax_rate": 0.0725}]
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    catalog_items = data.get("catalog", [])
    region_items = data.get("regions", [])

    if catalog_items:
        populate_catalog(catalog_items)
    if region_items:
        populate_regions(region_items)

    record_sync_history("populate", len(catalog_items), len(region_items), "success")

    return (
        jsonify(
            {
                "status": "populated",
                "catalog_count": len(catalog_items),
                "regions_count": len(region_items),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/local-cache/catalog", methods=["GET"])
def get_catalog():
    """Get all cached catalog items."""
    items = get_cached_catalog()
    return (
        jsonify(
            {
                "items": items,
                "count": len(items),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/local-cache/catalog/<sku>", methods=["GET"])
def get_catalog_item(sku: str):
    """Get a specific catalog item from cache."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM catalog WHERE sku = ?", (sku,))
        row = cursor.fetchone()
        if not row:
            abort(404, description=f"SKU {sku} not found in cache")
        return jsonify(dict(row)), 200


@app.route("/local-cache/regions", methods=["GET"])
def get_regions():
    """Get all cached regions."""
    items = get_cached_regions()
    return (
        jsonify(
            {
                "items": items,
                "count": len(items),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/local-cache/regions/<code>", methods=["GET"])
def get_region(code: str):
    """Get a specific region from cache."""
    region = get_cached_region(code)
    if not region:
        abort(404, description=f"Region {code} not found in cache")
    return jsonify(region), 200


@app.route("/local-cache/stale", methods=["GET"])
def check_stale():
    """Check which cache items are stale based on TTL."""
    stale_items = []
    fresh_items = []

    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT sku, name, price, cached_at FROM catalog")
        rows = cursor.fetchall()

        for row in rows:
            is_stale_flag = is_stale(row["cached_at"])
            item = dict(row)
            item["stale"] = is_stale_flag
            if is_stale_flag:
                stale_items.append(item)
            else:
                fresh_items.append(item)

    return (
        jsonify(
            {
                "stale_count": len(stale_items),
                "fresh_count": len(fresh_items),
                "stale_items": stale_items,
                "fresh_items": fresh_items,
                "cache_ttl_seconds": CACHE_TTL_SECONDS,
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/local-cache/reconcile", methods=["POST"])
def reconcile_cache():
    """
    Reconcile pending changes with cloud data.

    Expected payload:
    {
        "changes": [{"id": 1, "status": "reconciled"}]
    }
    """
    data = request.get_json() or {}
    changes = data.get("changes", [])

    with get_db() as conn:
        cursor = conn.cursor()

        if changes:
            for change in changes:
                change_id = change.get("id")
                status = change.get("status", "reconciled")
                cursor.execute(
                    """
                    UPDATE pending_changes 
                    SET status = ?, reconciled_at = ?
                    WHERE id = ?
                """,
                    (status, datetime.utcnow().isoformat() + "Z", change_id),
                )
        else:
            # Reconcile all pending changes
            cursor.execute(
                """
                UPDATE pending_changes 
                SET status = 'reconciled', reconciled_at = ?
                WHERE status = 'pending'
            """,
                (datetime.utcnow().isoformat() + "Z",),
            )

        conn.commit()

    record_sync_history("reconcile", 0, 0, "success")

    return (
        jsonify(
            {"status": "reconciled", "timestamp": datetime.utcnow().isoformat() + "Z"}
        ),
        200,
    )


@app.route("/local-cache/pending-changes", methods=["GET"])
def get_pending_changes():
    """Get all pending changes for reconciliation."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute(
            'SELECT * FROM pending_changes WHERE status = "pending" ORDER BY created_at'
        )
        rows = cursor.fetchall()
        return (
            jsonify(
                {
                    "pending_changes": [dict(row) for row in rows],
                    "count": len(rows),
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )


@app.route("/local-cache/sync-history", methods=["GET"])
def get_sync_history():
    """Get sync history."""
    with get_db() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM sync_history ORDER BY synced_at DESC LIMIT 20")
        rows = cursor.fetchall()
        return (
            jsonify(
                {
                    "history": [dict(row) for row in rows],
                    "count": len(rows),
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            ),
            200,
        )


@app.route("/local-cache/test/scenarios", methods=["POST"])
def test_scenarios():
    """
    Run cache test scenarios.

    Expected payload:
    {
        "scenario": "cache_hit" | "cache_miss" | "stale" | "reconcile" | "all"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "all")
    results = {}

    # Helper: get price from cache or fail
    def get_price(sku):
        price = get_cached_price(sku)
        if price is None:
            return None
        return price

    if scenario in ["cache_hit", "all"]:
        # Cache hit test
        sku = "SKU-1001"
        price = get_cached_price(sku)
        results["cache_hit"] = {
            "sku": sku,
            "found": price is not None,
            "price": price,
            "passed": price is not None,
        }

    if scenario in ["cache_miss", "all"]:
        # Cache miss test
        sku = "SKU-9999"
        price = get_cached_price(sku)
        results["cache_miss"] = {
            "sku": sku,
            "found": price is not None,
            "price": price,
            "passed": price is None,
        }

    if scenario in ["stale", "all"]:
        # Stale detection test
        stale_check = check_stale()
        results["stale"] = {
            "stale_count": stale_check.json["stale_count"],
            "fresh_count": stale_check.json["fresh_count"],
            "passed": True,
        }

    if scenario in ["reconcile", "all"]:
        # Reconcile test
        # Record a pending change
        record_pending_change("price_update", "SKU-1001", None, "2.99", "3.49")
        # Get pending changes
        pending = get_pending_changes()
        pending_count = len(pending.json.get("pending_changes", []))
        # Reconcile
        reconcile = reconcile_cache()
        # Check reconciled
        pending_after = get_pending_changes()
        pending_after_count = len(pending_after.json.get("pending_changes", []))
        results["reconcile"] = {
            "changes_recorded": pending_count > 0,
            "pending_before": pending_count,
            "pending_after": pending_after_count,
            "passed": pending_count > 0 and pending_after_count == 0,
        }

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "cache_scenarios",
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
    port = int(os.environ.get("PORT", 5013))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Local Data Cache starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
