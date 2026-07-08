# simulators/product-profiles/tcx-printer-dual/simulator.py

"""
TCx® Dual Station Printer Simulator

Two independent virtual print stations (customer receipt + merchant/journal copy),
each with its own paper-out/jam simulation flag.

Supports:
  - Two independent print stations
  - Customer receipt printing
  - Merchant journal copy printing
  - Simultaneous printing to both stations
  - Independent paper-out/jam simulation per station
  - Independent station status monitoring
  - One station's fault doesn't block the other

All data is mocked — no real printer hardware is involved.
"""

import os
import json
import logging
import time
from typing import Dict, Any, Optional, List
from datetime import datetime
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Virtual COM port paths for each station
# Station 1: Customer receipt
VIRTUAL_PORT_CUSTOMER = os.environ.get("VIRTUAL_PORT_CUSTOMER", "/tmp/pty-sim-1")
# Station 2: Merchant journal
VIRTUAL_PORT_MERCHANT = os.environ.get("VIRTUAL_PORT_MERCHANT", "/tmp/pty-sim-2")

# Station configurations
STATIONS = {
    "customer": {
        "name": "Customer Receipt",
        "virtual_port": VIRTUAL_PORT_CUSTOMER,
        "paper_status": "available",  # available, out, jam
        "print_count": 0,
        "error_count": 0,
        "last_receipt": None,
    },
    "merchant": {
        "name": "Merchant Journal",
        "virtual_port": VIRTUAL_PORT_MERCHANT,
        "paper_status": "available",
        "print_count": 0,
        "error_count": 0,
        "last_receipt": None,
    },
}

# Receipt storage
receipts = []


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "tcx-printer-dual-simulator"}), 200


@app.route("/tcx-printer-dual/stations", methods=["GET"])
def get_stations():
    """Get all station statuses."""
    return (
        jsonify(
            {
                "stations": {
                    "customer": {
                        "status": STATIONS["customer"]["paper_status"],
                        "print_count": STATIONS["customer"]["print_count"],
                        "error_count": STATIONS["customer"]["error_count"],
                        "virtual_port": STATIONS["customer"]["virtual_port"],
                        "connected": os.path.exists(
                            STATIONS["customer"]["virtual_port"]
                        ),
                    },
                    "merchant": {
                        "status": STATIONS["merchant"]["paper_status"],
                        "print_count": STATIONS["merchant"]["print_count"],
                        "error_count": STATIONS["merchant"]["error_count"],
                        "virtual_port": STATIONS["merchant"]["virtual_port"],
                        "connected": os.path.exists(
                            STATIONS["merchant"]["virtual_port"]
                        ),
                    },
                }
            }
        ),
        200,
    )


@app.route("/tcx-printer-dual/station/status/<station>", methods=["GET"])
def get_station_status(station):
    """Get status for a specific station."""
    if station not in STATIONS:
        abort(
            404, description=f"Station '{station}' not found. Valid: customer, merchant"
        )

    station_data = STATIONS[station]
    return (
        jsonify(
            {
                "station": station,
                "name": station_data["name"],
                "paper_status": station_data["paper_status"],
                "print_count": station_data["print_count"],
                "error_count": station_data["error_count"],
                "virtual_port": station_data["virtual_port"],
                "connected": os.path.exists(station_data["virtual_port"]),
                "status_code": 0 if station_data["paper_status"] == "available" else 1,
            }
        ),
        200,
    )


@app.route("/tcx-printer-dual/station/simulate", methods=["POST"])
def simulate_station_condition():
    """
    Simulate paper-out or jam for a specific station.

    Expected payload:
    {
        "station": "customer",
        "condition": "out" | "jam",
        "simulate": true,
        "duration_seconds": 10  # optional, auto-recover after duration
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    station = data.get("station")
    condition = data.get("condition", "out")
    simulate = data.get("simulate", True)
    duration = data.get("duration_seconds", 0)

    if not station:
        abort(400, description="Missing 'station' field")
    if station not in STATIONS:
        abort(
            404, description=f"Station '{station}' not found. Valid: customer, merchant"
        )

    if condition not in ["out", "jam"]:
        abort(
            400, description=f"Invalid condition: {condition}. Must be 'out' or 'jam'"
        )

    station_data = STATIONS[station]

    if simulate:
        station_data["paper_status"] = condition
        logger.warning(f"Station {station}: {condition} condition simulated")

        if duration > 0:

            def recover():
                time.sleep(duration)
                if station_data["paper_status"] == condition:
                    station_data["paper_status"] = "available"
                    logger.info(
                        f"Station {station}: auto-recovered from {condition} after {duration}s"
                    )

            import threading

            threading.Thread(target=recover, daemon=True).start()

        return (
            jsonify(
                {
                    "status": f"{condition}_simulated",
                    "station": station,
                    "paper_status": condition,
                    "auto_recover": duration > 0,
                    "recovery_seconds": duration if duration > 0 else None,
                }
            ),
            200,
        )
    else:
        station_data["paper_status"] = "available"
        return (
            jsonify(
                {"status": "restored", "station": station, "paper_status": "available"}
            ),
            200,
        )


def _format_receipt(
    transaction_id: str,
    items: List[Dict],
    total: float,
    tax: float,
    subtotal: float,
    station_name: str,
    receipt_type: str,
) -> str:
    """Format a receipt for a specific station."""
    lines = []
    lines.append("=" * 40)
    lines.append(f"     {station_name.upper()}")
    lines.append("=" * 40)
    lines.append(f"  Type: {receipt_type}")
    lines.append(f"  Transaction: {transaction_id}")
    lines.append(f"  Date: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("-" * 40)
    lines.append("  QTY  SKU      ITEM           PRICE")
    lines.append("-" * 40)

    for item in items:
        qty = item.get("quantity", 1)
        sku = item.get("sku", "")
        name = item.get("name", sku)[:15]
        price = item.get("price", 0.0) * qty
        lines.append(f"  {qty:2}   {sku:8} {name:15} ${price:7.2f}")

    lines.append("-" * 40)
    lines.append(f"  Subtotal: ${subtotal:8.2f}")
    lines.append(f"  Tax:      ${tax:8.2f}")
    lines.append("-" * 40)
    lines.append(f"  TOTAL:    ${total:8.2f}")
    lines.append("=" * 40)
    lines.append("  Thank you for your purchase!")
    lines.append("=" * 40)

    return "\n".join(lines)


def _print_to_station(station: str, receipt_text: str) -> Dict[str, Any]:
    """Print a receipt to a specific station."""
    station_data = STATIONS[station]

    # Check paper status
    if station_data["paper_status"] != "available":
        station_data["error_count"] += 1
        return {
            "status": "error",
            "station": station,
            "error": f"Paper {station_data['paper_status']}",
            "paper_status": station_data["paper_status"],
        }

    virtual_port = station_data["virtual_port"]
    virtual_printed = False

    if os.path.exists(virtual_port):
        try:
            with open(virtual_port, "w") as port:
                port.write(receipt_text + "\n\n")
                port.flush()
            virtual_printed = True
            logger.info(f"Receipt printed to {virtual_port} ({station})")
        except Exception as e:
            logger.error(f"Failed to write to {virtual_port} ({station}): {e}")
            virtual_printed = False
    else:
        logger.warning(f"Virtual port {virtual_port} ({station}) not found")

    station_data["print_count"] += 1

    return {
        "status": "printed" if virtual_printed else "printed_simulated",
        "station": station,
        "virtual_printed": virtual_printed,
        "virtual_port": virtual_port if virtual_printed else None,
        "paper_status": station_data["paper_status"],
        "print_count": station_data["print_count"],
    }


@app.route("/tcx-printer-dual/print/customer", methods=["POST"])
def print_customer_receipt():
    """
    Print a customer receipt (station 1).

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [...],
        "total": 2.99,
        "tax": 0.22,
        "subtotal": 2.77
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    total = data.get("total", 0.0)
    tax = data.get("tax", 0.0)
    subtotal = data.get("subtotal", 0.0)

    receipt_text = _format_receipt(
        transaction_id, items, total, tax, subtotal, "Customer Receipt", "CUSTOMER COPY"
    )

    result = _print_to_station("customer", receipt_text)
    result["transaction_id"] = transaction_id
    result["receipt_text"] = receipt_text
    result["timestamp"] = datetime.utcnow().isoformat() + "Z"

    # Store receipt
    receipts.append(
        {
            "transaction_id": transaction_id,
            "station": "customer",
            "receipt_text": receipt_text,
            "timestamp": result["timestamp"],
        }
    )

    return jsonify(result), 200 if result["status"] != "error" else 503


@app.route("/tcx-printer-dual/print/merchant", methods=["POST"])
def print_merchant_journal():
    """
    Print a merchant journal copy (station 2).

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [...],
        "total": 2.99,
        "tax": 0.22,
        "subtotal": 2.77
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    total = data.get("total", 0.0)
    tax = data.get("tax", 0.0)
    subtotal = data.get("subtotal", 0.0)

    receipt_text = _format_receipt(
        transaction_id, items, total, tax, subtotal, "Merchant Journal", "MERCHANT COPY"
    )

    result = _print_to_station("merchant", receipt_text)
    result["transaction_id"] = transaction_id
    result["receipt_text"] = receipt_text
    result["timestamp"] = datetime.utcnow().isoformat() + "Z"

    receipts.append(
        {
            "transaction_id": transaction_id,
            "station": "merchant",
            "receipt_text": receipt_text,
            "timestamp": result["timestamp"],
        }
    )

    return jsonify(result), 200 if result["status"] != "error" else 503


@app.route("/tcx-printer-dual/print/both", methods=["POST"])
def print_both_stations():
    """
    Print to both stations simultaneously.
    One station's fault doesn't block the other.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [...],
        "total": 2.99,
        "tax": 0.22,
        "subtotal": 2.77
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    total = data.get("total", 0.0)
    tax = data.get("tax", 0.0)
    subtotal = data.get("subtotal", 0.0)

    # Print to both stations independently
    customer_result = _print_to_station(
        "customer",
        _format_receipt(
            transaction_id,
            items,
            total,
            tax,
            subtotal,
            "Customer Receipt",
            "CUSTOMER COPY",
        ),
    )

    merchant_result = _print_to_station(
        "merchant",
        _format_receipt(
            transaction_id,
            items,
            total,
            tax,
            subtotal,
            "Merchant Journal",
            "MERCHANT COPY",
        ),
    )

    results = {
        "transaction_id": transaction_id,
        "customer": customer_result,
        "merchant": merchant_result,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }

    logger.info(
        f"Print both: customer={customer_result['status']}, merchant={merchant_result['status']}"
    )

    return jsonify(results), 200


@app.route("/tcx-printer-dual/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction and print to both stations.

    Expected payload:
    {
        "items": [{"sku": "SKU-1001", "quantity": 2}],
        "region": "CA",
        "payment": {"card_number": "4111111111111111"}
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})

    if not items:
        abort(400, description="Missing 'items' field")

    try:
        import requests

        # Step 1: Get prices
        subtotal = 0.0
        priced_items = []
        for item in items:
            sku = item.get("sku")
            quantity = item.get("quantity", 1)

            price_response = requests.get(
                f"{os.environ.get('PRICING_SERVICE_URL', 'http://pricing-service:8081')}/price/{sku}",
                timeout=5,
            )
            if price_response.status_code != 200:
                return jsonify({"status": "failed", "step": "pricing", "sku": sku}), 400

            price_data = price_response.json()
            item_price = price_data.get("price", 0)
            subtotal += item_price * quantity
            priced_items.append(
                {
                    "sku": sku,
                    "quantity": quantity,
                    "name": price_data.get("name", sku),
                    "price": item_price,
                    "total": item_price * quantity,
                }
            )

        # Step 2: Calculate tax
        tax_response = requests.post(
            f"{os.environ.get('TAX_SERVICE_URL', 'http://tax-service:8083')}/tax",
            json={"subtotal": subtotal, "region": region},
            timeout=5,
        )
        if tax_response.status_code != 200:
            return jsonify({"status": "failed", "step": "tax"}), 400

        tax_data = tax_response.json()
        tax_amount = tax_data.get("tax_amount", 0)
        total = tax_data.get("total", subtotal)

        # Step 3: Authorize payment
        card_number = payment.get("card_number", "4111111111111111")
        payment_response = requests.post(
            f"{os.environ.get('PAYMENT_GATEWAY_URL', 'http://payment-gateway:8084')}/payment/authorize",
            json={
                "amount": total,
                "currency": "USD",
                "card_number": card_number,
                "card_expiry": payment.get("expiry", "12/25"),
                "cvv": payment.get("cvv", "123"),
                "merchant_id": "TCX_PRINTER_DUAL",
                "order_id": f"DUAL-{datetime.utcnow().timestamp()}",
            },
            timeout=5,
        )

        if payment_response.status_code not in [200, 402]:
            return jsonify({"status": "failed", "step": "payment"}), 400

        payment_data = payment_response.json()

        # Step 4: Print to both stations
        print_payload = {
            "transaction_id": payment_data.get("transaction_id"),
            "items": priced_items,
            "total": total,
            "tax": tax_amount,
            "subtotal": subtotal,
        }

        # Use internal print_both
        with app.test_request_context(
            "/tcx-printer-dual/print/both", method="POST", json=print_payload
        ):
            print_response = print_both_stations()
            print_data = (
                print_response.get_json() if hasattr(print_response, "get_json") else {}
            )

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "profile": "tcx_printer_dual",
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "printing": print_data,
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"TCx Dual Printer transaction: {result['status']}")
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except Exception as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5008))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
