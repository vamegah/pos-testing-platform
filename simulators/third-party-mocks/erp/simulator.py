# simulators/third-party-mocks/erp/simulator.py

"""
ERP Integration Mock (E)

End-of-day/batch export of transactions to a mocked ERP endpoint.

Capabilities:
  - Batch export of transactions
  - End-of-day close-out
  - Export file generation (JSON/CSV)
  - Record count validation
  - Export history
  - Error handling (partial exports)
"""

import os
import json
import logging
import time
import requests
from datetime import datetime, timedelta
from typing import Dict, Any, Optional, List
from flask import Flask, request, jsonify, abort, send_file

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
EXPORT_DIR = os.environ.get("EXPORT_DIR", "/tmp/erp_exports")

# ============================================================
# ERP State
# ============================================================
# Transactions to export
pending_transactions = []
exported_transactions = []
export_history = []

# Batch state
current_batch_id = None
batch_status = "idle"  # idle, exporting, completed, failed


# ============================================================
# Helper Functions
# ============================================================


def generate_batch_id() -> str:
    """Generate a unique batch ID."""
    return f"BATCH-{datetime.utcnow().strftime('%Y%m%d')}-{int(time.time())}"


def ensure_export_dir():
    """Ensure the export directory exists."""
    os.makedirs(EXPORT_DIR, exist_ok=True)


def add_transaction(transaction_data: Dict[str, Any]):
    """Add a transaction to the pending export queue."""
    entry = {
        "transaction_id": transaction_data.get("transaction_id"),
        "order_id": transaction_data.get("order_id"),
        "customer_id": transaction_data.get("customer_id"),
        "amount": transaction_data.get("amount", 0.0),
        "items": transaction_data.get("items", []),
        "timestamp": transaction_data.get(
            "timestamp", datetime.utcnow().isoformat() + "Z"
        ),
        "erp_exported": False,
        "exported_at": None,
    }
    pending_transactions.append(entry)
    logger.info(f"Transaction added to ERP queue: {entry['transaction_id']}")
    return entry


def export_batch() -> Dict[str, Any]:
    """Export all pending transactions to ERP."""
    global current_batch_id, batch_status

    if not pending_transactions:
        return {
            "status": "no_transactions",
            "message": "No pending transactions to export",
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

    batch_id = generate_batch_id()
    current_batch_id = batch_id
    batch_status = "exporting"

    # Prepare export data
    export_data = {
        "batch_id": batch_id,
        "exported_at": datetime.utcnow().isoformat() + "Z",
        "transactions": pending_transactions.copy(),
        "total_transactions": len(pending_transactions),
        "total_amount": sum(t["amount"] for t in pending_transactions),
    }

    # Generate export file
    ensure_export_dir()
    filename = f"{batch_id}.json"
    filepath = os.path.join(EXPORT_DIR, filename)

    try:
        with open(filepath, "w") as f:
            json.dump(export_data, f, indent=2)

        # Mark transactions as exported
        for t in pending_transactions:
            t["erp_exported"] = True
            t["exported_at"] = datetime.utcnow().isoformat() + "Z"

        # Move to exported
        exported_transactions.extend(pending_transactions)
        exported_count = len(pending_transactions)
        pending_transactions.clear()

        batch_status = "completed"

        # Record in history
        history_entry = {
            "batch_id": batch_id,
            "filename": filename,
            "transaction_count": exported_count,
            "total_amount": export_data["total_amount"],
            "status": "completed",
            "exported_at": datetime.utcnow().isoformat() + "Z",
        }
        export_history.append(history_entry)

        logger.info(
            f"ERP export completed: {exported_count} transactions in {filename}"
        )

        # Publish event via gateway
        try:
            requests.post(
                f"{GATEWAY_URL}/gateway/events/publish",
                json={
                    "event_type": "erp.export_completed",
                    "event_data": {
                        "batch_id": batch_id,
                        "transaction_count": exported_count,
                        "filename": filename,
                    },
                },
                timeout=2,
            )
        except Exception as e:
            logger.warning(f"Failed to publish ERP event: {e}")

        return {
            "status": "completed",
            "batch_id": batch_id,
            "exported_count": exported_count,
            "total_amount": export_data["total_amount"],
            "filename": filename,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

    except Exception as e:
        batch_status = "failed"
        logger.error(f"ERP export failed: {e}")
        return {
            "status": "failed",
            "batch_id": batch_id,
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }


def get_export_file(batch_id: str) -> Optional[str]:
    """Get the export file path for a batch."""
    filename = f"{batch_id}.json"
    filepath = os.path.join(EXPORT_DIR, filename)
    if os.path.exists(filepath):
        return filepath
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
                "service": "erp-mock",
                "pending_count": len(pending_transactions),
                "exported_count": len(exported_transactions),
                "history_count": len(export_history),
                "batch_status": batch_status,
            }
        ),
        200,
    )


@app.route("/erp/transaction", methods=["POST"])
def add_transaction_endpoint():
    """
    Add a transaction to the ERP export queue.

    Expected payload: Transaction data
    """
    data = request.get_json() or {}
    if not data or "transaction_id" not in data:
        abort(400, description="Missing 'transaction_id' in transaction data")

    entry = add_transaction(data)

    return (
        jsonify(
            {
                "status": "queued",
                "transaction_id": entry["transaction_id"],
                "pending_count": len(pending_transactions),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        201,
    )


@app.route("/erp/export", methods=["POST"])
def export_endpoint():
    """
    Trigger an ERP export batch.

    Expected payload (optional):
    {
        "force": true
    }
    """
    data = request.get_json() or {}
    force = data.get("force", False)

    if not force and batch_status == "exporting":
        abort(409, description="Export already in progress")

    result = export_batch()
    return jsonify(result), 200 if result["status"] == "completed" else 500


@app.route("/erp/export/file/<batch_id>", methods=["GET"])
def get_export_file_endpoint(batch_id: str):
    """Download an export file."""
    filepath = get_export_file(batch_id)
    if not filepath:
        abort(404, description=f"Export file for batch {batch_id} not found")

    return send_file(filepath, as_attachment=True, download_name=f"{batch_id}.json")


@app.route("/erp/export/status", methods=["GET"])
def get_export_status():
    """Get current export status."""
    return (
        jsonify(
            {
                "batch_id": current_batch_id,
                "status": batch_status,
                "pending_count": len(pending_transactions),
                "exported_count": len(exported_transactions),
                "history_count": len(export_history),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/erp/export/history", methods=["GET"])
def get_export_history():
    """Get export history."""
    limit = request.args.get("limit", 20, type=int)
    history = export_history[-limit:]

    return (
        jsonify(
            {
                "history": history,
                "count": len(history),
                "total": len(export_history),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/erp/pending", methods=["GET"])
def get_pending_transactions():
    """Get pending transactions."""
    return (
        jsonify(
            {
                "transactions": pending_transactions,
                "count": len(pending_transactions),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/erp/exported", methods=["GET"])
def get_exported_transactions():
    """Get exported transactions."""
    limit = request.args.get("limit", 50, type=int)
    transactions = exported_transactions[-limit:]

    return (
        jsonify(
            {
                "transactions": transactions,
                "count": len(transactions),
                "total": len(exported_transactions),
                "timestamp": datetime.utcnow().isoformat() + "Z",
            }
        ),
        200,
    )


@app.route("/erp/test/scenario", methods=["POST"])
def test_scenario():
    """
    Run ERP test scenarios.

    Expected payload:
    {
        "scenario": "export" | "export_empty" | "batch_validation"
    }
    """
    data = request.get_json() or {}
    scenario = data.get("scenario", "export")
    results = {}

    if scenario == "export":
        # Add some transactions and export
        for i in range(5):
            add_transaction(
                {
                    "transaction_id": f"TXN-EXP-{i}",
                    "order_id": f"ORD-EXP-{i}",
                    "customer_id": f"CUST-EXP-{i}",
                    "amount": 10.00 + i,
                    "items": [
                        {"sku": f"SKU-{1000+i}", "quantity": 1, "price": 10.00 + i}
                    ],
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            )

        result = export_batch()
        results["export"] = {
            "status": result["status"],
            "exported_count": result.get("exported_count", 0),
            "passed": result["status"] == "completed"
            and result.get("exported_count", 0) == 5,
        }

    elif scenario == "export_empty":
        # Export with no transactions
        # Clear pending first
        pending_transactions.clear()
        result = export_batch()
        results["export_empty"] = {
            "status": result["status"],
            "message": result.get("message", ""),
            "passed": result["status"] == "no_transactions",
        }

    elif scenario == "batch_validation":
        # Add transactions, export, validate file
        for i in range(3):
            add_transaction(
                {
                    "transaction_id": f"TXN-VAL-{i}",
                    "order_id": f"ORD-VAL-{i}",
                    "customer_id": f"CUST-VAL-{i}",
                    "amount": 5.00 + i,
                    "items": [
                        {"sku": f"SKU-{2000+i}", "quantity": 1, "price": 5.00 + i}
                    ],
                    "timestamp": datetime.utcnow().isoformat() + "Z",
                }
            )

        result = export_batch()
        batch_id = result.get("batch_id")

        # Validate file exists
        filepath = get_export_file(batch_id)
        file_valid = filepath is not None and os.path.exists(filepath)

        if file_valid:
            with open(filepath, "r") as f:
                data = json.load(f)
            records = len(data.get("transactions", []))
        else:
            records = 0

        results["batch_validation"] = {
            "batch_id": batch_id,
            "file_exists": file_valid,
            "records": records,
            "passed": file_valid and records == 3,
        }

    else:
        abort(400, description=f"Unknown scenario: {scenario}")

    all_passed = all(r.get("passed", False) for r in results.values())

    return (
        jsonify(
            {
                "test": "erp_scenarios",
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

ensure_export_dir()

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8090))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"ERP Mock Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
