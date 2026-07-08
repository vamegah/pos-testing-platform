# simulators/pos-worker/worker.py

"""
Virtual POS Worker - Generic load generator for POS testing.
Executes transaction commands from the command bank against local mock services.
"""

import os
import sys
import json
import time
import random
import logging
import requests
from datetime import datetime
from typing import Dict, Any, Optional, List
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Service URLs from environment
PRICING_URL = os.environ.get("PRICING_SERVICE_URL", "http://pricing-service:8081")
PROMOTIONS_URL = os.environ.get(
    "PROMOTIONS_SERVICE_URL", "http://promotions-service:8082"
)
TAX_URL = os.environ.get("TAX_SERVICE_URL", "http://tax-service:8083")
PAYMENT_URL = os.environ.get("PAYMENT_GATEWAY_URL", "http://payment-gateway:8084")

# Worker configuration
WORKER_ID = os.environ.get("WORKER_ID", f"worker-{random.randint(1000, 9999)}")
TRANSACTION_RATE = float(os.environ.get("TRANSACTION_RATE", "1.0"))
OFFLINE_MODE = os.environ.get("OFFLINE_MODE", "false").lower() == "true"

# Path to command bank
COMMAND_BANK_PATH = os.environ.get(
    "COMMAND_BANK_PATH",
    os.path.join(
        os.path.dirname(__file__), "..", "queen-controller", "command_bank.json"
    ),
)


class CommandBank:
    """Load and access transaction commands from the command bank."""

    def __init__(self, path: str = COMMAND_BANK_PATH):
        self.path = path
        self.data = None
        self.load()

    def load(self) -> None:
        """Load command bank from JSON file."""
        try:
            with open(self.path, "r") as f:
                self.data = json.load(f)
            logger.info(f"Loaded command bank from {self.path}")
        except FileNotFoundError:
            logger.warning(f"Command bank not found at {self.path}, using defaults")
            self.data = self._get_default_bank()
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in command bank: {e}")
            self.data = self._get_default_bank()

    def _get_default_bank(self) -> Dict[str, Any]:
        """Return a minimal default command bank."""
        return {
            "commands": {
                "sale": {
                    "templates": {
                        "default": {
                            "sku": "SKU-1001",
                            "region": "CA",
                            "test_card": "4111111111111111",
                        }
                    }
                }
            },
            "test_data": {
                "skus": [{"id": "SKU-1001", "price": 2.99}],
                "regions": [{"code": "CA"}],
                "test_cards": [{"number": "4111111111111111"}],
            },
        }

    def get_command(self, command_type: str) -> Optional[Dict[str, Any]]:
        """Get a command template by type."""
        if not self.data:
            return None
        return self.data.get("commands", {}).get(command_type)

    def get_template(
        self, command_type: str, template_name: str = "default"
    ) -> Optional[Dict[str, Any]]:
        """Get a specific template for a command type."""
        command = self.get_command(command_type)
        if not command:
            return None
        return command.get("templates", {}).get(template_name)

    def get_random_template(self, command_type: str) -> Optional[Dict[str, Any]]:
        """Get a random template for a command type."""
        command = self.get_command(command_type)
        if not command:
            return None
        templates = command.get("templates", {})
        if not templates:
            return None
        template_name = random.choice(list(templates.keys()))
        return templates[template_name]

    def get_test_data(self, data_type: str) -> List[Dict[str, Any]]:
        """Get test data by type."""
        if not self.data:
            return []
        return self.data.get("test_data", {}).get(data_type, [])

    def get_random_sku(self) -> str:
        """Get a random SKU from test data."""
        skus = self.get_test_data("skus")
        if skus:
            return random.choice(skus).get("id", "SKU-1001")
        return "SKU-1001"

    def get_random_region(self) -> str:
        """Get a random region from test data."""
        regions = self.get_test_data("regions")
        if regions:
            return random.choice(regions).get("code", "CA")
        return "CA"

    def get_random_test_card(self) -> str:
        """Get a random test card number."""
        cards = self.get_test_data("test_cards")
        if cards:
            return random.choice(cards).get("number", "4111111111111111")
        return "4111111111111111"


class POSWorker:
    """Simulated POS terminal worker executing commands from the command bank."""

    def __init__(self, worker_id: str):
        self.worker_id = worker_id
        self.transactions_processed = 0
        self.transactions_failed = 0
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

        # Load command bank
        self.command_bank = CommandBank()

        # Statistics
        self.command_stats = {
            "sale": {"total": 0, "failed": 0, "avg_time": 0},
            "refund": {"total": 0, "failed": 0, "avg_time": 0},
            "void": {"total": 0, "failed": 0, "avg_time": 0},
            "offline_sync": {"total": 0, "failed": 0, "avg_time": 0},
        }

        logger.info(f"Worker {worker_id} initialized")
        logger.info(f"  Pricing: {PRICING_URL}")
        logger.info(f"  Promotions: {PROMOTIONS_URL}")
        logger.info(f"  Tax: {TAX_URL}")
        logger.info(f"  Payment: {PAYMENT_URL}")
        logger.info(f"  Rate: {TRANSACTION_RATE} txn/s")
        logger.info(f"  Offline mode: {OFFLINE_MODE}")
        logger.info(f"  Command bank: {COMMAND_BANK_PATH}")

    def _make_request(
        self, method: str, url: str, **kwargs
    ) -> Optional[Dict[str, Any]]:
        """Make an HTTP request with error handling."""
        try:
            response = self.session.request(method, url, timeout=5.0, **kwargs)
            if response.status_code in [200, 201, 402]:
                return response.json()
            else:
                logger.debug(
                    f"Request failed: {method} {url} -> {response.status_code}"
                )
                return None
        except requests.exceptions.Timeout:
            logger.debug(f"Request timeout: {method} {url}")
            return None
        except requests.exceptions.ConnectionError:
            logger.debug(f"Connection error: {method} {url}")
            return None
        except Exception as e:
            logger.debug(f"Request error: {e}")
            return None

    def execute_sale(self, template: Dict[str, Any]) -> Dict[str, Any]:
        """Execute a sale transaction from a template."""
        start_time = time.time()

        # Extract template parameters
        sku = template.get("sku", self.command_bank.get_random_sku())
        region = template.get("region", self.command_bank.get_random_region())
        test_card = template.get("test_card", self.command_bank.get_random_test_card())
        merchant_id = template.get("merchant_id", "TEST_MERCHANT_001")
        order_id = template.get(
            "order_id",
            f"ORDER-{self.worker_id}-{int(time.time())}-{random.randint(100, 999)}",
        )

        result = {
            "command": "sale",
            "worker_id": self.worker_id,
            "order_id": order_id,
            "sku": sku,
            "region": region,
            "status": "pending",
        }

        try:
            # Step 1: Get price
            price_response = self._make_request("GET", f"{PRICING_URL}/price/{sku}")
            if not price_response:
                result["status"] = "failed_pricing"
                return result
            price = price_response.get("price")
            result["price"] = price

            # Step 2: Calculate tax
            tax_response = self._make_request(
                "POST", f"{TAX_URL}/tax", json={"subtotal": price, "region": region}
            )
            if not tax_response:
                result["status"] = "failed_tax"
                return result
            result["tax"] = tax_response.get("tax_amount", 0)
            result["total"] = tax_response.get("total", price)

            # Step 3: Authorize payment
            if OFFLINE_MODE:
                result["status"] = "queued_offline"
                result["offline"] = True
                elapsed = time.time() - start_time
                self._update_stats("sale", True, elapsed)
                return result

            payment_response = self._make_request(
                "POST",
                f"{PAYMENT_URL}/payment/authorize",
                json={
                    "amount": result["total"],
                    "currency": "USD",
                    "card_number": test_card,
                    "card_expiry": "12/25",
                    "cvv": "123",
                    "merchant_id": merchant_id,
                    "order_id": order_id,
                },
            )

            if not payment_response:
                result["status"] = "failed_payment"
                return result

            result["status"] = payment_response.get("status", "unknown")
            result["transaction_id"] = payment_response.get("transaction_id")

        except Exception as e:
            logger.error(f"Sale execution error: {e}")
            result["status"] = "error"
            result["error"] = str(e)

        elapsed = time.time() - start_time
        success = result["status"] in ["approved", "queued_offline"]
        self._update_stats("sale", success, elapsed)

        return result

    def execute_refund(self, template: Dict[str, Any]) -> Dict[str, Any]:
        """Execute a refund transaction from a template."""
        start_time = time.time()

        # Extract template parameters
        transaction_id = template.get(
            "transaction_id", f"txn-{random.randint(100000, 999999)}"
        )
        merchant_id = template.get("merchant_id", "TEST_MERCHANT_001")
        amount = template.get("amount")

        result = {
            "command": "refund",
            "worker_id": self.worker_id,
            "transaction_id": transaction_id,
            "status": "pending",
        }

        try:
            # Process refund
            payload = {"transaction_id": transaction_id, "merchant_id": merchant_id}
            if amount:
                payload["amount"] = amount

            refund_response = self._make_request(
                "POST", f"{PAYMENT_URL}/payment/refund", json=payload
            )

            if not refund_response:
                result["status"] = "failed"
                return result

            result["status"] = refund_response.get("status", "unknown")
            result["refund_id"] = refund_response.get("refund_id")

        except Exception as e:
            logger.error(f"Refund execution error: {e}")
            result["status"] = "error"
            result["error"] = str(e)

        elapsed = time.time() - start_time
        success = result["status"] in ["refunded", "approved"]
        self._update_stats("refund", success, elapsed)

        return result

    def execute_void(self, template: Dict[str, Any]) -> Dict[str, Any]:
        """Execute a void transaction from a template."""
        start_time = time.time()

        # Extract template parameters
        transaction_id = template.get(
            "transaction_id", f"txn-{random.randint(100000, 999999)}"
        )
        merchant_id = template.get("merchant_id", "TEST_MERCHANT_001")

        result = {
            "command": "void",
            "worker_id": self.worker_id,
            "transaction_id": transaction_id,
            "status": "pending",
        }

        try:
            # Process void
            void_response = self._make_request(
                "POST",
                f"{PAYMENT_URL}/payment/void",
                json={"transaction_id": transaction_id, "merchant_id": merchant_id},
            )

            if not void_response:
                result["status"] = "failed"
                return result

            result["status"] = void_response.get("status", "unknown")
            result["void_id"] = void_response.get("void_id")

        except Exception as e:
            logger.error(f"Void execution error: {e}")
            result["status"] = "error"
            result["error"] = str(e)

        elapsed = time.time() - start_time
        success = result["status"] in ["voided", "approved"]
        self._update_stats("void", success, elapsed)

        return result

    def execute_offline_sync(self, template: Dict[str, Any]) -> Dict[str, Any]:
        """Execute an offline sync transaction (simulated)."""
        start_time = time.time()

        # Extract template parameters
        sku = template.get("sku", self.command_bank.get_random_sku())
        quantity = template.get("quantity", 1)
        timestamp = template.get("timestamp", datetime.utcnow().isoformat() + "Z")

        result = {
            "command": "offline_sync",
            "worker_id": self.worker_id,
            "sku": sku,
            "quantity": quantity,
            "status": "pending",
        }

        # Simulate offline store-and-forward
        # In a real implementation, this would interact with a local queue

        try:
            # Simulate offline queuing
            if OFFLINE_MODE:
                result["status"] = "queued_offline"
                result["queued_at"] = timestamp
            else:
                # Process as normal sale when online
                result["status"] = "online_processing"

                # Get price and process as sale
                price_response = self._make_request("GET", f"{PRICING_URL}/price/{sku}")
                if price_response:
                    result["price"] = price_response.get("price")
                    # Simulate sync completion
                    result["status"] = "synced"
                    result["synced_at"] = datetime.utcnow().isoformat() + "Z"

        except Exception as e:
            logger.error(f"Offline sync execution error: {e}")
            result["status"] = "error"
            result["error"] = str(e)

        elapsed = time.time() - start_time
        success = result["status"] in ["queued_offline", "synced", "online_processing"]
        self._update_stats("offline_sync", success, elapsed)

        return result

    def _update_stats(self, command_type: str, success: bool, elapsed: float):
        """Update statistics for a command execution."""
        stats = self.command_stats.get(
            command_type, {"total": 0, "failed": 0, "avg_time": 0}
        )
        stats["total"] += 1
        if not success:
            stats["failed"] += 1
        stats["avg_time"] = (
            (stats["avg_time"] * (stats["total"] - 1)) + elapsed
        ) / stats["total"]
        self.command_stats[command_type] = stats

    def execute_transaction(self, command_type: str = None) -> Dict[str, Any]:
        """Execute a random transaction from the command bank."""
        # Determine command type (weighted by transaction mix)
        if command_type is None:
            # Default mix from command bank
            mix = self.command_bank.data.get(
                "transaction_mix",
                {"sale": 60, "refund": 10, "void": 5, "offline_sync": 25},
            )
            choices = []
            for cmd, weight in mix.items():
                choices.extend([cmd] * weight)
            command_type = random.choice(choices) if choices else "sale"

        # Get a random template for the command
        template = self.command_bank.get_random_template(command_type)
        if not template:
            logger.warning(
                f"No template found for command: {command_type}, using default"
            )
            template = {"sku": self.command_bank.get_random_sku()}

        # Execute the command
        if command_type == "sale":
            return self.execute_sale(template)
        elif command_type == "refund":
            return self.execute_refund(template)
        elif command_type == "void":
            return self.execute_void(template)
        elif command_type == "offline_sync":
            return self.execute_offline_sync(template)
        else:
            logger.warning(f"Unknown command type: {command_type}")
            return {"status": "unknown", "command": command_type}

    def run(self, duration: Optional[int] = None):
        """Run the worker for a specified duration (or indefinitely)."""
        logger.info(f"Worker {self.worker_id} starting...")
        start_time = time.time()

        if duration:
            end_time = start_time + duration
        else:
            end_time = None

        transaction_count = 0

        while True:
            # Check if duration elapsed
            if end_time and time.time() >= end_time:
                break

            # Execute a transaction
            result = self.execute_transaction()
            transaction_count += 1

            # Log result (periodically)
            if transaction_count % 10 == 0:
                logger.info(
                    f"Worker {self.worker_id} - {transaction_count} transactions processed "
                    f"(last: {result.get('command')} -> {result.get('status')})"
                )

            # Sleep based on transaction rate
            sleep_time = 1.0 / TRANSACTION_RATE
            time.sleep(sleep_time)

        # Log summary
        total = self.transactions_processed + self.transactions_failed
        logger.info(
            f"Worker {self.worker_id} completed. "
            f"Processed: {self.transactions_processed}, "
            f"Failed: {self.transactions_failed}, "
            f"Total: {total}"
        )

        # Log command statistics
        logger.info(f"Command statistics:")
        for cmd, stats in self.command_stats.items():
            if stats["total"] > 0:
                success_rate = (
                    (stats["total"] - stats["failed"]) / stats["total"]
                ) * 100
                logger.info(
                    f"  {cmd}: {stats['total']} exec, {success_rate:.1f}% success, avg {stats['avg_time']:.3f}s"
                )


if __name__ == "__main__":
    # Parse command line arguments
    import argparse

    parser = argparse.ArgumentParser(description="POS Worker")
    parser.add_argument("-d", "--duration", type=int, help="Duration to run in seconds")
    parser.add_argument(
        "-c",
        "--command",
        type=str,
        help="Command type to execute (sale/refund/void/offline_sync)",
    )
    parser.add_argument("--config", type=str, help="Path to command bank JSON")
    args = parser.parse_args()

    # Override command bank path if provided
    if args.config:
        os.environ["COMMAND_BANK_PATH"] = args.config

    # Create worker and run
    worker = POSWorker(WORKER_ID)

    # If specific command, execute once and exit
    if args.command:
        result = worker.execute_transaction(args.command)
        print(json.dumps(result, indent=2))
    else:
        worker.run(duration=args.duration)
