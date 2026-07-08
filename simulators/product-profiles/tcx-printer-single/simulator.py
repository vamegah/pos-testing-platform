# simulators/product-profiles/tcx-printer-single/simulator.py

"""
TCx® Single Station Printer Simulator

One virtual print station (receipt only), reuses the socat RS-232/virtual-port
simulator from Phase 2.

Supports:
  - Single station receipt printing
  - Paper-out simulation
  - Jam simulation
  - RS-232 communication via virtual COM ports
  - Receipt formatting

All data is mocked — no real printer hardware is involved.
"""

import os
import json
import logging
import subprocess
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

# Virtual COM port paths (from Phase 2 socat simulator)
# These should be configured to match the socat simulator output
VIRTUAL_PORT = os.environ.get("VIRTUAL_PORT", "/tmp/pty-sim-1")
VIRTUAL_PORT_PERIPHERAL = os.environ.get("VIRTUAL_PORT_PERIPHERAL", "/tmp/pty-sim-2")

# Printer state
printer_state = {
    "paper_status": "available",  # available, out, jam
    "last_receipt": None,
    "print_count": 0,
    "error_count": 0,
}

# Receipt storage (in-memory)
receipts = []


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return (
        jsonify({"status": "healthy", "service": "tcx-printer-single-simulator"}),
        200,
    )


@app.route("/tcx-printer-single/status", methods=["GET"])
def get_printer_status():
    """Get the current printer status."""
    return (
        jsonify(
            {
                "paper_status": printer_state["paper_status"],
                "print_count": printer_state["print_count"],
                "error_count": printer_state["error_count"],
                "virtual_port": VIRTUAL_PORT,
                "connected": os.path.exists(VIRTUAL_PORT),
            }
        ),
        200,
    )


@app.route("/tcx-printer-single/paper/status", methods=["GET"])
def get_paper_status():
    """Get the current paper status."""
    return (
        jsonify(
            {
                "paper_status": printer_state["paper_status"],
                "status_code": 0 if printer_state["paper_status"] == "available" else 1,
                "message": (
                    "Paper available"
                    if printer_state["paper_status"] == "available"
                    else f"Paper {printer_state['paper_status']}"
                ),
            }
        ),
        200,
    )


@app.route("/tcx-printer-single/paper/out/simulate", methods=["POST"])
def simulate_paper_out():
    """
    Simulate a paper-out condition.

    Expected payload:
    {
        "simulate": true,
        "duration_seconds": 10  # optional, auto-recover after duration
    }
    """
    data = request.get_json() or {}
    simulate = data.get("simulate", True)
    duration = data.get("duration_seconds", 0)

    if simulate:
        printer_state["paper_status"] = "out"
        logger.warning("Paper-out condition simulated")

        # Auto-recover if duration specified
        if duration > 0:

            def recover():
                time.sleep(duration)
                if printer_state["paper_status"] == "out":
                    printer_state["paper_status"] = "available"
                    logger.info("Paper-out auto-recovered after %d seconds", duration)

            import threading

            threading.Thread(target=recover, daemon=True).start()

        return (
            jsonify(
                {
                    "status": "paper_out_simulated",
                    "paper_status": "out",
                    "auto_recover": duration > 0,
                    "recovery_seconds": duration if duration > 0 else None,
                }
            ),
            200,
        )
    else:
        printer_state["paper_status"] = "available"
        return jsonify({"status": "paper_restored", "paper_status": "available"}), 200


@app.route("/tcx-printer-single/jam/simulate", methods=["POST"])
def simulate_jam():
    """
    Simulate a paper jam condition.

    Expected payload:
    {
        "simulate": true,
        "duration_seconds": 10  # optional, auto-recover after duration
    }
    """
    data = request.get_json() or {}
    simulate = data.get("simulate", True)
    duration = data.get("duration_seconds", 0)

    if simulate:
        printer_state["paper_status"] = "jam"
        logger.warning("Paper jam condition simulated")

        if duration > 0:

            def recover():
                time.sleep(duration)
                if printer_state["paper_status"] == "jam":
                    printer_state["paper_status"] = "available"
                    logger.info("Paper jam auto-recovered after %d seconds", duration)

            import threading

            threading.Thread(target=recover, daemon=True).start()

        return (
            jsonify(
                {
                    "status": "jam_simulated",
                    "paper_status": "jam",
                    "auto_recover": duration > 0,
                    "recovery_seconds": duration if duration > 0 else None,
                }
            ),
            200,
        )
    else:
        printer_state["paper_status"] = "available"
        return jsonify({"status": "jam_cleared", "paper_status": "available"}), 200


@app.route("/tcx-printer-single/print/receipt", methods=["POST"])
def print_receipt():
    """
    Print a receipt to the virtual RS-232 port.

    Expected payload:
    {
        "transaction_id": "txn-12345",
        "items": [
            {"sku": "SKU-1001", "name": "Milk", "price": 2.99, "quantity": 1, "total": 2.99}
        ],
        "total": 2.99,
        "tax": 0.22,
        "subtotal": 2.77
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    # Check paper status
    if printer_state["paper_status"] != "available":
        printer_state["error_count"] += 1
        return (
            jsonify(
                {
                    "status": "error",
                    "error": f"Paper {printer_state['paper_status']}",
                    "paper_status": printer_state["paper_status"],
                }
            ),
            503,
        )

    transaction_id = data.get("transaction_id", f"txn-{datetime.utcnow().timestamp()}")
    items = data.get("items", [])
    total = data.get("total", 0.0)
    tax = data.get("tax", 0.0)
    subtotal = data.get("subtotal", 0.0)

    # Format receipt
    receipt_lines = []
    receipt_lines.append("=" * 40)
    receipt_lines.append("          RECEIPT")
    receipt_lines.append("=" * 40)
    receipt_lines.append(f"  Transaction: {transaction_id}")
    receipt_lines.append(f"  Date: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')}")
    receipt_lines.append("-" * 40)
    receipt_lines.append("  QTY  SKU      ITEM           PRICE")
    receipt_lines.append("-" * 40)

    for item in items:
        qty = item.get("quantity", 1)
        sku = item.get("sku", "")
        name = item.get("name", sku)[:15]
        price = item.get("price", 0.0) * qty
        receipt_lines.append(f"  {qty:2}   {sku:8} {name:15} ${price:7.2f}")

    receipt_lines.append("-" * 40)
    receipt_lines.append(f"  Subtotal: ${subtotal:8.2f}")
    receipt_lines.append(f"  Tax:      ${tax:8.2f}")
    receipt_lines.append("-" * 40)
    receipt_lines.append(f"  TOTAL:    ${total:8.2f}")
    receipt_lines.append("=" * 40)
    receipt_lines.append("  Thank you for your purchase!")
    receipt_lines.append("=" * 40)

    receipt_text = "\n".join(receipt_lines)

    # Write to virtual port if it exists
    virtual_printed = False
    if os.path.exists(VIRTUAL_PORT):
        try:
            with open(VIRTUAL_PORT, "w") as port:
                port.write(receipt_text + "\n\n")
                port.flush()
            virtual_printed = True
            logger.info(f"Receipt printed to {VIRTUAL_PORT}")
        except Exception as e:
            logger.error(f"Failed to write to virtual port: {e}")
            virtual_printed = False
    else:
        logger.warning(f"Virtual port {VIRTUAL_PORT} not found")

    # Store receipt
    receipt = {
        "transaction_id": transaction_id,
        "items": items,
        "total": total,
        "tax": tax,
        "subtotal": subtotal,
        "receipt_text": receipt_text,
        "virtual_printed": virtual_printed,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    receipts.append(receipt)
    printer_state["print_count"] += 1
    printer_state["last_receipt"] = receipt

    logger.info(f"Receipt printed for {transaction_id} ({len(items)} items, ${total})")

    return (
        jsonify(
            {
                "status": "printed",
                "transaction_id": transaction_id,
                "receipt": receipt_text,
                "virtual_printed": virtual_printed,
                "virtual_port": VIRTUAL_PORT if virtual_printed else None,
                "paper_status": printer_state["paper_status"],
                "print_count": printer_state["print_count"],
                "timestamp": receipt["timestamp"],
            }
        ),
        200,
    )


@app.route("/tcx-printer-single/print/history", methods=["GET"])
def get_print_history():
    """Get the print history."""
    return jsonify({"receipts": receipts, "count": len(receipts)}), 200


@app.route("/tcx-printer-single/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction and print a receipt.

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

    # Check paper status
    if printer_state["paper_status"] != "available":
        return (
            jsonify(
                {
                    "status": "failed",
                    "step": "printer",
                    "error": f"Paper {printer_state['paper_status']}",
                }
            ),
            503,
        )

    try:
        # Step 1: Get prices
        subtotal = 0.0
        priced_items = []
        for item in items:
            sku = item.get("sku")
            quantity = item.get("quantity", 1)

            # Use requests to pricing service
            import requests

            price_response = requests.get(
                f"{os.environ.get('PRICING_SERVICE_URL', 'http://pricing-service:8081')}/price/{sku}",
                timeout=5,
            )
            if price_response.status_code != 200:
                return (
                    jsonify(
                        {
                            "status": "failed",
                            "step": "pricing",
                            "sku": sku,
                            "error": "Price lookup failed",
                        }
                    ),
                    400,
                )

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
        import requests

        tax_response = requests.post(
            f"{os.environ.get('TAX_SERVICE_URL', 'http://tax-service:8083')}/tax",
            json={"subtotal": subtotal, "region": region},
            timeout=5,
        )
        if tax_response.status_code != 200:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "tax",
                        "error": "Tax calculation failed",
                    }
                ),
                400,
            )

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
                "merchant_id": "TCX_PRINTER_SINGLE",
                "order_id": f"PRINTER-{datetime.utcnow().timestamp()}",
            },
            timeout=5,
        )

        if payment_response.status_code not in [200, 402]:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "payment",
                        "error": "Payment authorization failed",
                    }
                ),
                400,
            )

        payment_data = payment_response.json()

        # Step 4: Print receipt
        print_response = print_receipt(
            {
                "transaction_id": payment_data.get("transaction_id"),
                "items": priced_items,
                "total": total,
                "tax": tax_amount,
                "subtotal": subtotal,
            }
        )

        print_data = (
            print_response.get_json() if hasattr(print_response, "get_json") else {}
        )

        result = {
            "status": (
                "completed" if payment_data.get("status") == "approved" else "declined"
            ),
            "profile": "tcx_printer_single",
            "subtotal": subtotal,
            "tax": tax_amount,
            "total": total,
            "items": priced_items,
            "payment": {
                "status": payment_data.get("status"),
                "transaction_id": payment_data.get("transaction_id"),
            },
            "receipt": print_data.get("receipt"),
            "receipt_printed": print_data.get("status") == "printed",
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

        logger.info(f"TCx Single Printer transaction: {result['status']}")
        return jsonify(result), 200 if result["status"] == "completed" else 402

    except Exception as e:
        logger.error(f"Transaction failed: {e}")
        return jsonify({"status": "failed", "error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5007))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
