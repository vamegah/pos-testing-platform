# simulators/product-profiles/tcx-printer-single/README.md

# TCx® Single Station Printer Simulator

## Overview

This simulator implements the TCx® Single Station Printer capability profile — one virtual print station (receipt only), reusing the `socat` RS-232/virtual-port simulator from Phase 2.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Receipt Printing | ✅ | Print to virtual RS-232 port |
| Paper-Out Simulation | ✅ | Simulate paper-out condition |
| Jam Simulation | ✅ | Simulate paper jam condition |
| RS-232 Virtual Port | ✅ | Reuses Phase 2 socat simulator |

## Dependencies

- Requires `socat_rs232_simulator.sh` from Phase 2 (hardware-emulation/virtual-com-ports/)
- Virtual port path: `/tmp/pty-sim-1` (configurable via `VIRTUAL_PORT` env var)

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/tcx-printer-single/status` | GET | Get printer status |
| `/tcx-printer-single/paper/status` | GET | Get paper status |
| `/tcx-printer-single/paper/out/simulate` | POST | Simulate paper-out |
| `/tcx-printer-single/jam/simulate` | POST | Simulate paper jam |
| `/tcx-printer-single/print/receipt` | POST | Print a receipt |
| `/tcx-printer-single/print/history` | GET | Get print history |
| `/tcx-printer-single/transaction` | POST | Process transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/tcx-printer-single
pip install -r requirements.txt

# Start the virtual COM port simulator (from Phase 2)
../../hardware-emulation/virtual-com-ports/socat_rs232_simulator.sh start

# Start the printer simulator
python simulator.py