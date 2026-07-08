# simulators/product-profiles/mxp-smart-hybrid/README.md

# MxP™ SMART | hybrid Simulator

## Overview

This simulator implements the MxP™ SMART | hybrid capability profile with cashier↔self-service mode toggle (models the "rotate 180°" assisted mode).

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Self-Service Mode | ✅ | Customer self-checkout |
| Assisted Mode | ✅ | Cashier-assisted (rotated 180°) |
| Mode Toggle | ✅ | API toggle simulating physical rotation |
| Mode Events | ✅ | Mode change history logging |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/mxp-smart-hybrid/mode` | GET | Get current mode |
| `/mxp-smart-hybrid/toggle` | POST | Toggle between modes |
| `/mxp-smart-hybrid/mode/event` | GET | Get mode change history |
| `/mxp-smart-hybrid/transaction` | POST | Process transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/mxp-smart-hybrid
pip install -r requirements.txt
python simulator.py