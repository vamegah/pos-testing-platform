# simulators/pos-services/payment/app.py

"""
mock-payment-gateway — Fully mocked payment gateway for POS testing.
Returns approve/decline based on test card sentinel values.
NO REAL PAYMENT DATA — mock only, clearly labeled.
"""

import os
import json
import logging
import uuid
from datetime import datetime
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Mock transaction storage (in-memory, for testing only)
MOCK_TRANSACTIONS = {}

# Test card sentinel values
# Cards ending in 0000 -> decline
# Cards ending in 1111 -> approve with warning (e.g., AVS mismatch)
# All other cards -> approve
TEST_CARD_RULES = {
    "0000": {"status": "declined", "reason": "Insufficient funds"},
    "1111": {"status": "approved", "reason": "AVS mismatch warning"},
}

# Mock merchant configurations
MOCK_MERCHANTS = {
    "TEST_MERCHANT_001": {
        "name": "Test Store #001",
        "address": "123 Test St, Test City, TS 12345",
        "merchant_id": "TEST_MERCHANT_001",
    }
}


def determine_payment_status(card_number):
    """
    Determine payment approval/decline based on test card sentinel values.

    Args:
        card_number (str): The card number (masked or full)

    Returns:
        tuple: (status, reason) where status is 'approved' or 'declined'
    """
    # Extract last 4 digits for testing
    # For security, we only use last 4 digits in logs
    last_4 = card_number[-4:] if card_number and len(card_number) >= 4 else "0000"

    # Check if last 4 match any test rules
    if last_4 in TEST_CARD_RULES:
        rule = TEST_CARD_RULES[last_4]
        return rule["status"], rule["reason"]

    # Default: approve
    return "approved", "Transaction approved"


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "mock-payment-gateway"}), 200


@app.route("/payment/authorize", methods=["POST"])
def authorize_payment():
    """
    Authorize a payment transaction.

    Expected payload:
    {
        "amount": 107.25,
        "currency": "USD",
        "card_number": "4111111111111111",  # Test card (mock only)
        "card_expiry": "12/25",
        "cvv": "123",
        "merchant_id": "TEST_MERCHANT_001",
        "order_id": "ORDER-12345",
        "customer": {
            "name": "John Doe",
            "email": "john@example.com"
        }
    }

    Returns:
        JSON: {
            "transaction_id": str,
            "status": "approved" | "declined",
            "amount": float,
            "currency": str,
            "timestamp": str,
            "auth_code": str,
            "reason": str (if declined)
        }

    Examples:
        POST /payment/authorize
        { "amount": 107.25, "card_number": "4111111111111111", ... }
        -> { "transaction_id": "txn-123", "status": "approved", ... }

        POST /payment/authorize
        { "amount": 107.25, "card_number": "4111111111110000", ... }
        -> { "transaction_id": "txn-124", "status": "declined", "reason": "Insufficient funds", ... }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    # Extract required fields
    amount = data.get("amount")
    currency = data.get("currency", "USD")
    card_number = data.get("card_number")
    card_expiry = data.get("card_expiry")
    cvv = data.get("cvv")
    merchant_id = data.get("merchant_id")
    order_id = data.get("order_id", f"ORDER-{uuid.uuid4().hex[:8].upper()}")
    customer = data.get("customer", {})

    # Validate required fields
    if amount is None:
        abort(400, description="Missing 'amount' field")

    try:
        amount = float(amount)
    except (TypeError, ValueError):
        abort(400, description="'amount' must be a number")

    if amount <= 0:
        abort(400, description="'amount' must be greater than 0")

    if not card_number:
        abort(400, description="Missing 'card_number' field")

    # Mask card number for logging (show first 4 and last 4 only)
    masked_card = (
        f"{card_number[:4]}...{card_number[-4:]}"
        if len(card_number) >= 8
        else "XXXX...XXXX"
    )
    logger.info(
        f"Payment authorization request: amount=${amount:.2f}, card={masked_card}, merchant={merchant_id}"
    )

    if not merchant_id:
        abort(400, description="Missing 'merchant_id' field")

    if merchant_id not in MOCK_MERCHANTS:
        abort(404, description=f"Merchant '{merchant_id}' not found")

    # Determine payment status based on test card sentinel values
    status, reason = determine_payment_status(card_number)

    # Generate transaction ID
    transaction_id = f"txn-{uuid.uuid4().hex[:12]}"

    # Generate auth code if approved
    auth_code = None
    if status == "approved":
        auth_code = f"AUTH-{uuid.uuid4().hex[:8].upper()}"

    # Store transaction (in-memory, for testing only)
    transaction = {
        "transaction_id": transaction_id,
        "status": status,
        "amount": amount,
        "currency": currency,
        "merchant_id": merchant_id,
        "order_id": order_id,
        "customer": customer,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "auth_code": auth_code,
        "reason": reason if status == "declined" else None,
        "card_last_4": card_number[-4:] if len(card_number) >= 4 else "XXXX",
    }
    MOCK_TRANSACTIONS[transaction_id] = transaction

    # Log the outcome
    logger.info(f"Payment authorization result: {status} (txn: {transaction_id})")

    # Return response
    response = {
        "transaction_id": transaction_id,
        "status": status,
        "amount": amount,
        "currency": currency,
        "timestamp": transaction["timestamp"],
        "merchant_id": merchant_id,
    }

    if auth_code:
        response["auth_code"] = auth_code

    if status == "declined":
        response["reason"] = reason

    return jsonify(response), 200 if status == "approved" else 402


@app.route("/payment/void", methods=["POST"])
def void_payment():
    """
    Void a previously authorized payment.

    Expected payload:
    {
        "transaction_id": "txn-123456789012",
        "merchant_id": "TEST_MERCHANT_001"
    }

    Returns:
        JSON: {
            "void_id": str,
            "status": "voided",
            "transaction_id": str,
            "timestamp": str
        }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id")
    merchant_id = data.get("merchant_id")

    if not transaction_id:
        abort(400, description="Missing 'transaction_id' field")

    if not merchant_id:
        abort(400, description="Missing 'merchant_id' field")

    # Check if transaction exists
    if transaction_id not in MOCK_TRANSACTIONS:
        abort(404, description=f"Transaction '{transaction_id}' not found")

    # Check if transaction can be voided (only approved transactions)
    transaction = MOCK_TRANSACTIONS[transaction_id]
    if transaction["status"] != "approved":
        abort(
            400,
            description=f"Transaction '{transaction_id}' cannot be voided (status: {transaction['status']})",
        )

    # Void the transaction
    void_id = f"void-{uuid.uuid4().hex[:12]}"
    transaction["status"] = "voided"
    transaction["void_id"] = void_id
    transaction["voided_at"] = datetime.utcnow().isoformat() + "Z"

    logger.info(f"Payment voided: {transaction_id} -> {void_id}")

    return (
        jsonify(
            {
                "void_id": void_id,
                "status": "voided",
                "transaction_id": transaction_id,
                "timestamp": transaction["voided_at"],
                "merchant_id": merchant_id,
            }
        ),
        200,
    )


@app.route("/payment/refund", methods=["POST"])
def refund_payment():
    """
    Refund a previously authorized payment.

    Expected payload:
    {
        "transaction_id": "txn-123456789012",
        "merchant_id": "TEST_MERCHANT_001",
        "amount": 107.25  # Optional, defaults to full amount
    }

    Returns:
        JSON: {
            "refund_id": str,
            "status": "refunded",
            "transaction_id": str,
            "amount": float,
            "timestamp": str
        }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    transaction_id = data.get("transaction_id")
    merchant_id = data.get("merchant_id")
    refund_amount = data.get("amount")

    if not transaction_id:
        abort(400, description="Missing 'transaction_id' field")

    if not merchant_id:
        abort(400, description="Missing 'merchant_id' field")

    # Check if transaction exists
    if transaction_id not in MOCK_TRANSACTIONS:
        abort(404, description=f"Transaction '{transaction_id}' not found")

    transaction = MOCK_TRANSACTIONS[transaction_id]

    # Check if transaction can be refunded (only approved or voided transactions)
    if transaction["status"] not in ["approved", "voided"]:
        abort(
            400,
            description=f"Transaction '{transaction_id}' cannot be refunded (status: {transaction['status']})",
        )

    # Default to full amount if not specified
    if refund_amount is None:
        refund_amount = transaction["amount"]
    else:
        try:
            refund_amount = float(refund_amount)
        except (TypeError, ValueError):
            abort(400, description="'amount' must be a number")

        if refund_amount <= 0:
            abort(400, description="'amount' must be greater than 0")

        if refund_amount > transaction["amount"]:
            abort(
                400,
                description=f"Refund amount ${refund_amount:.2f} exceeds transaction amount ${transaction['amount']:.2f}",
            )

    # Process refund
    refund_id = f"ref-{uuid.uuid4().hex[:12]}"
    transaction["status"] = "refunded"
    transaction["refund_id"] = refund_id
    transaction["refund_amount"] = refund_amount
    transaction["refunded_at"] = datetime.utcnow().isoformat() + "Z"

    logger.info(
        f"Payment refunded: {transaction_id} -> {refund_id} (${refund_amount:.2f})"
    )

    return (
        jsonify(
            {
                "refund_id": refund_id,
                "status": "refunded",
                "transaction_id": transaction_id,
                "amount": refund_amount,
                "timestamp": transaction["refunded_at"],
                "merchant_id": merchant_id,
            }
        ),
        200,
    )


@app.route("/payment/transaction/<transaction_id>", methods=["GET"])
def get_transaction(transaction_id):
    """
    Get transaction details by ID.

    Args:
        transaction_id (str): The transaction ID

    Returns:
        JSON: Transaction details (masked sensitive data)
    """
    if transaction_id not in MOCK_TRANSACTIONS:
        abort(404, description=f"Transaction '{transaction_id}' not found")

    transaction = MOCK_TRANSACTIONS[transaction_id].copy()

    # Mask sensitive data for response
    if "card_last_4" in transaction:
        transaction["card_masked"] = f"XXXX...{transaction['card_last_4']}"

    # Remove sensitive fields
    transaction.pop("card_last_4", None)

    return jsonify(transaction), 200


@app.route("/payment/status", methods=["GET"])
def get_payment_status():
    """
    Get the status of the mock payment gateway service.
    """
    return (
        jsonify(
            {
                "service": "mock-payment-gateway",
                "status": "operational",
                "mode": "mock",
                "test_card_rules": list(TEST_CARD_RULES.keys()),
                "transaction_count": len(MOCK_TRANSACTIONS),
            }
        ),
        200,
    )


@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors with JSON response."""
    return (
        jsonify(
            {
                "error": "Not Found",
                "message": (
                    str(error.description)
                    if hasattr(error, "description")
                    else "Resource not found"
                ),
            }
        ),
        404,
    )


@app.errorhandler(400)
def bad_request(error):
    """Handle 400 errors with JSON response."""
    return (
        jsonify(
            {
                "error": "Bad Request",
                "message": (
                    str(error.description)
                    if hasattr(error, "description")
                    else "Invalid request"
                ),
            }
        ),
        400,
    )


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8084))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
