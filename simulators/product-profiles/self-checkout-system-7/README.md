# simulators/product-profiles/self-checkout-system-7/README.md

# Self Checkout System 7 Simulator

## Overview

This simulator implements the Self Checkout System 7 capability profile — full-basket, attended/unattended self-service lane with scanner, scale, bagging, printer, payment, and transaction awareness light.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Full-Basket | ✅ | Multi-item transactions |
| Attended/Unattended | ✅ | Mode toggle with override |
| Bagging Verification | ✅ | Weight verification |
| Awareness Light | ✅ | Transaction status events |
| Payment | ✅ | Card/cash/gift card (mock) |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/self-checkout/mode` | GET/POST | Get/set mode |
| `/self-checkout/transaction/start` | POST | Start transaction |
| `/self-checkout/scan` | POST | Scan item |
| `/self-checkout/bagging/verify` | POST | Verify bagging weight |
| `/self-checkout/attended/override` | POST | Attendant override |
| `/self-checkout/awareness/event` | POST | Awareness event |
| `/self-checkout/awareness/events` | GET | Get events |
| `/self-checkout/transaction/complete` | POST | Complete transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/self-checkout-system-7
pip install -r requirements.txt
python simulator.py