# simulators/product-profiles/tcx-display/README.md

# TCx® Display Simulator

## Overview

This simulator implements the TCx® Display capability profile — a display capability matrix with size range, orientation, and resolution validation.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Size Validation | ✅ | Validates display size (10.1, 12.1, 15.6, 21.5) |
| Orientation Validation | ✅ | Validates landscape/portrait orientation |
| Resolution Validation | ✅ | Validates resolution for orientation |
| Size-Orientation Matrix | ✅ | Validates combos against matrix |

## Supported Matrix

| Size | Landscape | Portrait |
|------|-----------|----------|
| 10.1" | ✅ | ✅ |
| 12.1" | ✅ | ✅ |
| 15.6" | ✅ | ✅ |
| 21.5" | ✅ | ❌ |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/tcx-display/capabilities` | GET | Get capability matrix |
| `/tcx-display/state` | GET | Get current display state |
| `/tcx-display/render/validate` | POST | Validate display configuration |
| `/tcx-display/validation/history` | GET | Get validation history |
| `/tcx-display/transaction` | POST | Process with display validation |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/tcx-display
pip install -r requirements.txt
python simulator.py