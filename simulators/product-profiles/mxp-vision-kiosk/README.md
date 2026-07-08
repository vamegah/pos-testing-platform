# simulators/product-profiles/mxp-vision-kiosk/README.md

# MxP™ Vision Kiosk Simulator

## Overview

This simulator implements the MxP™ Vision Kiosk capability profile with mocked computer-vision bulk scan, weight-scale cross-check, and NFC/biometric payment.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Bulk Vision Scan | ✅ | Mock computer vision for bulk item recognition |
| Weight-Scale Cross-Check | ✅ | Verifies item weights against expected values |
| NFC Payment | ✅ | Mock NFC tap-to-pay |
| Biometric Payment | ✅ | Mock fingerprint/face payment |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/mxp-vision/scan` | POST | Bulk vision scan |
| `/mxp-vision/weight/verify` | POST | Weight-scale cross-check |
| `/mxp-vision/payment/biometric` | POST | Biometric payment |
| `/mxp-vision/payment/nfc` | POST | NFC payment |
| `/mxp-vision/transaction` | POST | Full transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/mxp-vision-kiosk
pip install -r requirements.txt
python simulator.py