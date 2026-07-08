# simulators/product-profiles/mxp-smart-wing/README.md

# MxP™ SMART | wing Simulator

## Overview

This simulator implements the MxP™ SMART | wing capability profile — modular/component-based lane with independently togglable peripherals (scanner, scale, printer, payment each optional).

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Independent Toggling | ✅ | Each peripheral can be enabled/disabled |
| Combination Validation | ✅ | Validate peripheral combinations |
| Modular Architecture | ✅ | Component-based design |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/mxp-smart-wing/peripherals` | GET | Get peripheral states |
| `/mxp-smart-wing/peripheral/toggle` | POST | Toggle a peripheral |
| `/mxp-smart-wing/peripheral/validate` | POST | Validate a combination |
| `/mxp-smart-wing/transaction` | POST | Process transaction |

## Fixtures

| File | Description |
|------|-------------|
| `fixtures/combination_1.json` | Full configuration (all peripherals) |
| `fixtures/combination_2.json` | Minimal configuration (scanner + printer) |
| `fixtures/combination_3.json` | Self-service configuration (scanner + PIN pad) |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/mxp-smart-wing
pip install -r requirements.txt
python simulator.py