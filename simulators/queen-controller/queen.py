# simulators/queen-controller/queen.py

"""
Queen Controller - Generic swarm test coordinator.
Reads a test configuration JSON and spawns N worker processes locally.
"""

import os
import sys
import json
import time
import signal
import logging
import argparse
import subprocess
import multiprocessing
from datetime import datetime
from typing import Dict, Any, List, Optional
from dataclasses import dataclass, field

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


@dataclass
class TestConfig:
    """Configuration for a swarm test run."""

    duration: int = 60  # Test duration in seconds
    workers: int = 5  # Number of workers to spawn
    ramp_up: int = 10  # Ramp-up time in seconds
    transaction_rate: float = 1.0  # Transactions per second per worker
    pos_types: List[str] = field(default_factory=lambda: ["TCx810", "TCx700"])
    transaction_mix: Dict[str, int] = field(
        default_factory=lambda: {
            "sale": 60,
            "refund": 10,
            "void": 5,
            "offline_sync": 25,
        }
    )
    peripheral_simulation: Dict[str, bool] = field(
        default_factory=lambda: {"scanner": True, "printer": True, "pin_pad": True}
    )
    target_endpoint: str = "http://localhost:8081"
    offline_mode: bool = False

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "TestConfig":
        """Create TestConfig from dictionary."""
        return cls(
            duration=data.get("duration", 60),
            workers=data.get("workers", 5),
            ramp_up=data.get("ramp_up", 10),
            transaction_rate=data.get("transaction_rate", 1.0),
            pos_types=data.get("pos_types", ["TCx810", "TCx700"]),
            transaction_mix=data.get(
                "transaction_mix",
                {"sale": 60, "refund": 10, "void": 5, "offline_sync": 25},
            ),
            peripheral_simulation=data.get(
                "peripheral_simulation",
                {"scanner": True, "printer": True, "pin_pad": True},
            ),
            target_endpoint=data.get("target_endpoint", "http://localhost:8081"),
            offline_mode=data.get("offline_mode", False),
        )

    @classmethod
    def from_file(cls, filepath: str) -> "TestConfig":
        """Load TestConfig from JSON file."""
        with open(filepath, "r") as f:
            data = json.load(f)
        return cls.from_dict(data)

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary."""
        return {
            "duration": self.duration,
            "workers": self.workers,
            "ramp_up": self.ramp_up,
            "transaction_rate": self.transaction_rate,
            "pos_types": self.pos_types,
            "transaction_mix": self.transaction_mix,
            "peripheral_simulation": self.peripheral_simulation,
            "target_endpoint": self.target_endpoint,
            "offline_mode": self.offline_mode,
        }


class QueenController:
    """Swarm test controller that spawns and manages worker processes."""

    def __init__(self, config: TestConfig):
        self.config = config
        self.workers = []
        self.start_time = None
        self.shutdown_requested = False

        # Setup signal handlers
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)

    def _signal_handler(self, signum, frame):
        """Handle shutdown signals."""
        logger.info("Received shutdown signal, terminating workers...")
        self.shutdown_requested = True
        self.stop()

    def _get_worker_command(self, worker_id: int) -> List[str]:
        """Build the command to start a worker process."""
        # Use the pos-worker implementation
        script_path = os.path.join(
            os.path.dirname(__file__), "..", "pos-worker", "worker.py"
        )

        # Build environment variables for the worker
        env = os.environ.copy()
        env["WORKER_ID"] = f"worker-{worker_id:04d}"
        env["TRANSACTION_RATE"] = str(self.config.transaction_rate)
        env["OFFLINE_MODE"] = str(self.config.offline_mode).lower()
        env["PRICING_SERVICE_URL"] = self.config.target_endpoint.replace(
            "localhost", "pricing-service"
        )
        env["PROMOTIONS_SERVICE_URL"] = self.config.target_endpoint.replace(
            "localhost", "promotions-service"
        )
        env["TAX_SERVICE_URL"] = self.config.target_endpoint.replace(
            "localhost", "tax-service"
        )
        env["PAYMENT_GATEWAY_URL"] = self.config.target_endpoint.replace(
            "localhost", "payment-gateway"
        )

        # Return command with env
        return ["python", script_path], env

    def _run_worker(self, worker_id: int) -> None:
        """Run a single worker process."""
        cmd, env = self._get_worker_command(worker_id)

        try:
            logger.info(f"Starting worker {worker_id}")
            proc = subprocess.Popen(
                cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
            )
            self.workers.append(
                {"id": worker_id, "process": proc, "start_time": time.time()}
            )

            # Wait for process to complete
            stdout, stderr = proc.communicate(timeout=self.config.duration + 10)

            if proc.returncode != 0:
                logger.warning(f"Worker {worker_id} exited with code {proc.returncode}")
                if stderr:
                    logger.warning(f"Worker {worker_id} stderr: {stderr[:200]}...")
            else:
                logger.info(f"Worker {worker_id} completed successfully")

        except subprocess.TimeoutExpired:
            logger.warning(f"Worker {worker_id} timed out, terminating...")
            proc.kill()
            proc.wait()
        except Exception as e:
            logger.error(f"Error in worker {worker_id}: {e}")

    def _spawn_workers(self) -> None:
        """Spawn all workers with ramp-up."""
        total_workers = self.config.workers
        ramp_up = self.config.ramp_up

        logger.info(f"Spawning {total_workers} workers with {ramp_up}s ramp-up...")

        for i in range(total_workers):
            if self.shutdown_requested:
                break

            # Calculate delay for ramp-up
            if ramp_up > 0 and total_workers > 1:
                delay = (i / (total_workers - 1)) * ramp_up
                logger.debug(f"Worker {i+1} delayed by {delay:.2f}s")
                time.sleep(delay)

            # Start worker in separate process
            p = multiprocessing.Process(target=self._run_worker, args=(i + 1,))
            p.start()
            self.workers.append({"id": i + 1, "process": p, "start_time": time.time()})
            logger.info(f"Worker {i+1} launched (PID: {p.pid})")

    def _monitor_workers(self) -> None:
        """Monitor worker processes and collect results."""
        logger.info("Monitoring workers...")

        # Wait for all workers to complete or duration to expire
        start_time = time.time()
        duration = self.config.duration

        while time.time() - start_time < duration:
            if self.shutdown_requested:
                break

            # Check if all workers are still alive
            active = 0
            for w in self.workers:
                if w["process"].is_alive():
                    active += 1

            if active == 0:
                logger.info("All workers completed")
                break

            # Report status periodically
            elapsed = int(time.time() - start_time)
            if elapsed % 10 == 0:  # Every 10 seconds
                logger.info(
                    f"Active workers: {active}/{len(self.workers)} (elapsed: {elapsed}s/{duration}s)"
                )

            time.sleep(2)

    def start(self) -> None:
        """Start the swarm test."""
        logger.info("=" * 60)
        logger.info("QUEEN CONTROLLER - Starting Swarm Test")
        logger.info("=" * 60)
        logger.info(f"Workers: {self.config.workers}")
        logger.info(f"Duration: {self.config.duration}s")
        logger.info(f"Ramp-up: {self.config.ramp_up}s")
        logger.info(f"Transaction rate: {self.config.transaction_rate}/s/worker")
        logger.info(f"Target: {self.config.target_endpoint}")
        logger.info(f"Offline mode: {self.config.offline_mode}")
        logger.info("=" * 60)

        self.start_time = datetime.now()

        try:
            # Spawn workers with ramp-up
            self._spawn_workers()

            # Monitor workers
            self._monitor_workers()

        except KeyboardInterrupt:
            logger.info("Interrupted by user")
        finally:
            self.stop()

    def stop(self) -> None:
        """Stop all workers gracefully."""
        logger.info("Stopping all workers...")

        for w in self.workers:
            try:
                if w["process"].is_alive():
                    w["process"].terminate()
                    w["process"].join(timeout=5)
                    if w["process"].is_alive():
                        w["process"].kill()
                        w["process"].join(timeout=2)
            except Exception as e:
                logger.error(f"Error stopping worker {w['id']}: {e}")

        elapsed = (
            (datetime.now() - self.start_time).total_seconds() if self.start_time else 0
        )
        logger.info(f"Test completed in {elapsed:.2f}s")
        logger.info("=" * 60)


def generate_default_config(output_path: str) -> None:
    """Generate a default configuration file."""
    config = TestConfig()
    config_dict = config.to_dict()

    with open(output_path, "w") as f:
        json.dump(config_dict, f, indent=2)

    logger.info(f"Default configuration written to {output_path}")


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Queen Controller - Swarm Test Coordinator"
    )
    parser.add_argument(
        "-c", "--config", type=str, help="Path to test configuration JSON file"
    )
    parser.add_argument(
        "-g",
        "--generate-config",
        type=str,
        help="Generate a default configuration file at the specified path",
    )
    parser.add_argument(
        "-w", "--workers", type=int, help="Number of workers (overrides config file)"
    )
    parser.add_argument(
        "-d",
        "--duration",
        type=int,
        help="Test duration in seconds (overrides config file)",
    )
    parser.add_argument(
        "-r",
        "--rate",
        type=float,
        help="Transaction rate per worker (overrides config file)",
    )
    parser.add_argument(
        "-t", "--target", type=str, help="Target endpoint URL (overrides config file)"
    )
    parser.add_argument(
        "-v", "--verbose", action="store_true", help="Enable verbose logging"
    )

    args = parser.parse_args()

    # Set logging level
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    # Generate config if requested
    if args.generate_config:
        generate_default_config(args.generate_config)
        return

    # Load configuration
    if args.config:
        config = TestConfig.from_file(args.config)
    else:
        # Use default config
        logger.info("No config file provided, using defaults")
        config = TestConfig()

    # Apply overrides
    if args.workers:
        config.workers = args.workers
    if args.duration:
        config.duration = args.duration
    if args.rate:
        config.transaction_rate = args.rate
    if args.target:
        config.target_endpoint = args.target

    # Validate config
    if config.workers <= 0:
        logger.error("Number of workers must be greater than 0")
        sys.exit(1)
    if config.duration <= 0:
        logger.error("Duration must be greater than 0")
        sys.exit(1)

    # Run the test
    queen = QueenController(config)
    queen.start()


if __name__ == "__main__":
    main()
