# simulators/pos-services/inventory/README.md

# Inventory Microservice (D2)

## Overview

Stock lookup + decrement-on-sale, with an oversell negative test.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Stock Lookup | ✅ | Get stock by SKU |
| Stock Decrement | ✅ | Decrement on sale |
| Oversell Prevention | ✅ | Reject if insufficient stock |
| Stock Replenishment | ✅ | Add stock |
| Stock Reservation | ✅ | Reserve for pending orders |
| Event Publishing | ✅ | Publish stock events |
| Transaction History | ✅ | Audit log |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/inventory/stock` | GET | Get all stock |
| `/inventory/stock/<sku>` | GET | Get stock for SKU |
| `/inventory/stock/<sku>/replenish` | POST | Replenish stock |
| `/inventory/stock/<sku>/reserve` | POST | Reserve stock |
| `/inventory/stock/<sku>/decrement` | POST | Decrement stock |
| `/inventory/stock/<sku>/release-reserved` | POST | Release reserved |
| `/inventory/transaction/history` | GET | Transaction history |
| `/inventory/test/oversell` | POST | Oversell negative test |
| `/inventory/test/scenario` | POST | Run test scenarios |

## Usage

### Start the Inventory Service

```bash
cd simulators/pos-services/inventory
pip install -r requirements.txt
python simulator.py

Get Stock
bash
curl http://localhost:8085/inventory/stock/SKU-1001
Decrement Stock (Sale)
bash
curl -X POST http://localhost:8085/inventory/stock/SKU-1001/decrement \
  -H "Content-Type: application/json" \
  -d '{"quantity": 2, "transaction_id": "txn-001"}'
Oversell Test
bash
curl -X POST http://localhost:8085/inventory/test/oversell \
  -H "Content-Type: application/json" \
  -d '{"sku": "SKU-1010", "quantity": 100}'
Test Scenarios
Scenario	Description
stock_lookup	Verify stock lookup works
decrement_success	Verify decrement succeeds
decrement_failure	Verify oversell rejected
reserve_release	Verify reserve and release