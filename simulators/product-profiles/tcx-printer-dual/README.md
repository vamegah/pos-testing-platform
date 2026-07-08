# simulators/product-profiles/tcx-printer-dual/README.md

# TCx® Dual Station Printer Simulator

## Overview

This simulator implements the TCx® Dual Station Printer capability profile — two independent virtual print stations (customer receipt + merchant/journal copy), each with its own paper-out/jam simulation flag.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Customer Receipt | ✅ | Print to station 1 |
| Merchant Journal | ✅ | Print to station 2 |
| Simultaneous Print | ✅ | Print to both stations independently |
| Independent Faults | ✅ | One station's fault doesn't block the other |
| Paper-Out Simulation | ✅ | Per-station paper-out |
| Jam Simulation | ✅ | Per-station jam |

## Station Configuration

| Station | Virtual Port | Purpose |
|---------|--------------|---------|
| Customer | `/tmp/pty-sim-1` | Customer receipt |
| Merchant | `/tmp/pty-sim-2` | Merchant journal copy |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/tcx-printer-dual/stations` | GET | Get all station statuses |
| `/tcx-printer-dual/station/status/<station>` | GET | Get specific station status |
| `/tcx-printer-dual/station/simulate` | POST | Simulate paper-out/jam per station |
| `/tcx-printer-dual/print/customer` | POST | Print customer receipt |
| `/tcx-printer-dual/print/merchant` | POST | Print merchant journal |
| `/tcx-printer-dual/print/both` | POST | Print to both stations |
| `/tcx-printer-dual/transaction` | POST | Process transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/tcx-printer-dual
pip install -r requirements.txt
python simulator.py