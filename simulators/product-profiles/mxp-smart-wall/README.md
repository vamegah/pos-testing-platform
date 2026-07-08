# simulators/product-profiles/mxp-smart-wall/README.md

# MxP™ SMART | wall Simulator

## Overview

This simulator implements the MxP™ SMART | wall capability profile — space-constrained, wall-mounted lane with reduced peripheral set (no bagging-area scale, compact printer only).

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Wall Mount | ✅ | Space-constrained form factor |
| Handheld Scanner | ✅ | Compact scanner for items |
| Compact Printer | ✅ | 3-inch receipt printer |
| No Scale | ✅ | Scale peripheral explicitly unavailable |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/mxp-smart-wall/peripherals` | GET | Get available peripherals |
| `/mxp-smart-wall/scan` | POST | Scan an item |
| `/mxp-smart-wall/print` | POST | Print a receipt |
| `/mxp-smart-wall/transaction` | POST | Process transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/mxp-smart-wall
pip install -r requirements.txt
python simulator.py