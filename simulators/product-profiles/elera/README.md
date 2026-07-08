# simulators/product-profiles/elera/README.md

# ELERA® Platform Simulator

## Overview

This simulator implements the ELERA® unified-commerce capability profile. It acts as a thin adapter in front of the Phase 1 services, routing transactions through the ELERA platform pipeline.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| POS Mode | ✅ | Cashier-assisted checkout |
| Self-Service Mode | ✅ | Customer self-checkout |
| Produce Recognition | ✅ | Mock computer vision for produce identification |
| Security Suite | ✅ | Security event logging and monitoring |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/elera/mode` | GET/POST | Get/set current mode (pos/self_service) |
| `/elera/produce/recognize` | POST | Mock produce recognition |
| `/elera/security/event` | POST | Log a security event |
| `/elera/security/events` | GET | Get all security events |
| `/elera/transaction` | POST | Process a full transaction |

## Usage

### Start the Simulator

```bash
cd simulators/product-profiles/elera
pip install -r requirements.txt
python simulator.py