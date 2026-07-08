# simulators/pos-worker/transaction_generator.py

"""
Transaction Generator - Creates synthetic transaction commands from the command bank.
Used by the queen controller to generate test workloads.
"""

import os
import sys
import json
import random
import argparse
from datetime import datetime
from typing import List, Dict, Any, Optional
from pathlib import Path


class TransactionGenerator:
    """Generates synthetic transaction commands from the command bank."""

    def __init__(self, command_bank_path: Optional[str] = None):
        if command_bank_path is None:
            command_bank_path = os.path.join(
                os.path.dirname(__file__), "..", "queen-controller", "command_bank.json"
            )

        self.command_bank_path = command_bank_path
        self.command_bank = self._load_command_bank()

        # Load test data
        self.skus = self.command_bank.get("test_data", {}).get("skus", [])
        self.regions = self.command_bank.get("test_data", {}).get("regions", [])
        self.test_cards = self.command_bank.get("test_data", {}).get("test_cards", [])
        self.merchants = self.command_bank.get("test_data", {}).get("merchants", [])
        self.transaction_mix = self.command_bank.get(
            "transaction_mix", {"sale": 60, "refund": 10, "void": 5, "offline_sync": 25}
        )
        self.scenarios = self.command_bank.get("scenarios", {})

    def _load_command_bank(self) -> Dict[str, Any]:
        """Load the command bank from JSON file."""
        try:
            with open(self.command_bank_path, "r") as f:
                return json.load(f)
        except Exception as e:
            print(f"Error loading command bank: {e}", file=sys.stderr)
            return {}

    def get_random_sku(self) -> str:
        """Get a random SKU from test data."""
        if self.skus:
            return random.choice(self.skus).get("id", "SKU-1001")
        return "SKU-1001"

    def get_random_sku_price(self) -> float:
        """Get a random SKU price."""
        if self.skus:
            return random.choice(self.skus).get("price", 2.99)
        return 2.99

    def get_random_region(self) -> str:
        """Get a random region code."""
        if self.regions:
            return random.choice(self.regions).get("code", "CA")
        return "CA"

    def get_random_test_card(self) -> str:
        """Get a random test card number."""
        if self.test_cards:
            return random.choice(self.test_cards).get("number", "4111111111111111")
        return "4111111111111111"

    def get_random_merchant(self) -> str:
        """Get a random merchant ID."""
        if self.merchants:
            return random.choice(self.merchants).get("id", "TEST_MERCHANT_001")
        return "TEST_MERCHANT_001"

    def generate_sale(self) -> Dict[str, Any]:
        """Generate a sale command."""
        return {
            "command": "sale",
            "sku": self.get_random_sku(),
            "region": self.get_random_region(),
            "test_card": self.get_random_test_card(),
            "merchant_id": self.get_random_merchant(),
            "order_id": f"ORDER-{int(datetime.now().timestamp())}-{random.randint(100, 999)}",
        }

    def generate_refund(self) -> Dict[str, Any]:
        """Generate a refund command."""
        return {
            "command": "refund",
            "transaction_id": f"txn-{random.randint(100000, 999999)}",
            "merchant_id": self.get_random_merchant(),
            "amount": None,  # Full refund
        }

    def generate_void(self) -> Dict[str, Any]:
        """Generate a void command."""
        return {
            "command": "void",
            "transaction_id": f"txn-{random.randint(100000, 999999)}",
            "merchant_id": self.get_random_merchant(),
        }

    def generate_offline_sync(self) -> Dict[str, Any]:
        """Generate an offline sync command."""
        return {
            "command": "offline_sync",
            "sku": self.get_random_sku(),
            "quantity": random.choice([1, 1, 1, 2, 3]),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }

    def generate_command(self) -> Dict[str, Any]:
        """Generate a random command based on transaction mix."""
        # Build weighted choices
        choices = []
        for cmd, weight in self.transaction_mix.items():
            choices.extend([cmd] * weight)

        command_type = random.choice(choices) if choices else "sale"

        if command_type == "sale":
            return self.generate_sale()
        elif command_type == "refund":
            return self.generate_refund()
        elif command_type == "void":
            return self.generate_void()
        elif command_type == "offline_sync":
            return self.generate_offline_sync()
        else:
            return self.generate_sale()

    def generate_batch(self, count: int) -> List[Dict[str, Any]]:
        """Generate a batch of commands."""
        return [self.generate_command() for _ in range(count)]

    def get_scenario(self, name: str) -> Dict[str, Any]:
        """Get a scenario configuration by name."""
        return self.scenarios.get(name, {})

    def generate_from_scenario(self, name: str) -> List[Dict[str, Any]]:
        """Generate commands based on a scenario."""
        scenario = self.get_scenario(name)
        if not scenario:
            return []

        workers = scenario.get("workers", 1)
        duration = scenario.get("duration", 60)
        rate = scenario.get("transaction_rate", 1.0)

        # Calculate total transactions
        total = int(workers * duration * rate)

        return self.generate_batch(total)


def main():
    """Main entry point for command line usage."""
    parser = argparse.ArgumentParser(description="Transaction Generator")
    parser.add_argument(
        "-c", "--count", type=int, default=10, help="Number of commands to generate"
    )
    parser.add_argument(
        "-b", "--batch", action="store_true", help="Generate batch output"
    )
    parser.add_argument(
        "-s", "--scenario", type=str, help="Generate commands from a scenario"
    )
    parser.add_argument("-f", "--file", type=str, help="Output to file")
    parser.add_argument("--config", type=str, help="Path to command bank JSON")

    args = parser.parse_args()

    # Create generator
    generator = TransactionGenerator(args.config)

    # Generate commands
    if args.scenario:
        commands = generator.generate_from_scenario(args.scenario)
        print(
            f"Generated {len(commands)} commands from scenario '{args.scenario}'",
            file=sys.stderr,
        )
    else:
        commands = generator.generate_batch(args.count)

    # Output
    output = json.dumps(commands, indent=2)
    if args.file:
        with open(args.file, "w") as f:
            f.write(output)
        print(f"Output written to {args.file}", file=sys.stderr)
    else:
        print(output)


if __name__ == "__main__":
    main()
