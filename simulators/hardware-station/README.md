# simulators/hardware-station/README.md

# Hardware Station Service (B3)

## Overview

The Hardware Station is the mediator the POS App actually calls, which fans out to Printer, Cash Drawer, Scanner, and Card Reader peripherals. Includes fault-isolation: one peripheral down doesn't block the others.

## Architecture
┌─────────────┐
│ POS App │
│ (B1) │
└──────┬──────┘
│
▼
┌─────────────┐
│ Hardware │─────▶ Printer (12.8)
│ Station │─────▶ Cash Drawer (23.2)
│ (B3) │─────▶ Scanner (12.4)
│ │─────▶ Card Reader (23.1)
└─────────────┘

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Scanner Mediation | ✅ | Routes scan requests to scanner |
| Printer Mediation | ✅ | Routes print requests to printer |
| Cash Drawer Mediation | ✅ | Routes drawer open requests |
| Card Reader Mediation | ✅ | Routes card processing requests |
| Fault Isolation | ✅ | One peripheral down doesn't block others |
| Peripheral Status | ✅ | Track availability of each peripheral |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/hardware-station/peripherals/status` | GET | Get all peripheral statuses |
| `/hardware-station/peripherals/status/<peripheral>` | GET | Get specific peripheral status |
| `/hardware-station/peripherals/<peripheral>/simulate-down` | POST | Simulate a peripheral down |
| `/hardware-station/scan` | POST | Scan an item |
| `/hardware-station/print` | POST | Print a receipt |
| `/hardware-station/cash-drawer/open` | POST | Open cash drawer |
| `/hardware-station/card-reader/process` | POST | Process card payment |
| `/hardware-station/fault-isolation` | POST | Run fault-isolation test |

## Usage

### Start the Hardware Station

```bash
cd simulators/hardware-station
pip install -r requirements.txt
python simulator.py

Scan an Item
bash
curl -X POST http://localhost:5011/hardware-station/scan \
  -H "Content-Type: application/json" \
  -d '{"sku": "SKU-1001", "session_id": "txn-001"}'
Print a Receipt
bash
curl -X POST http://localhost:5011/hardware-station/print \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "txn-001",
    "items": [{"sku": "SKU-1001", "name": "Milk", "price": 2.99, "quantity": 1}],
    "total": 2.99,
    "session_id": "txn-001"
  }'
Open Cash Drawer
bash
curl -X POST http://localhost:5011/hardware-station/cash-drawer/open \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-001", "tender_type": "cash", "amount": 10.00, "session_id": "txn-001"}'
Simulate a Peripheral Down
bash
curl -X POST http://localhost:5011/hardware-station/peripherals/printer/simulate-down \
  -H "Content-Type: application/json" \
  -d '{"simulate": true, "duration_seconds": 60}'
Run Fault-Isolation Test
bash
curl -X POST http://localhost:5011/hardware-station/fault-isolation \
  -H "Content-Type: application/json" \
  -d '{"down_peripheral": "printer"}'
Fault-Isolation Test
The fault-isolation test verifies that when one peripheral is down, other peripherals continue to work. The test simulates a specific peripheral as down and attempts all operations, verifying that only the down peripheral is blocked.

Example output:

json
{
  "test": "fault_isolation",
  "down_peripheral": "printer",
  "results": {
    "scanner": "success",
    "printer": "blocked",
    "cash_drawer": "success",
    "card_reader": "success"
  },
  "summary": {
    "blocked": 1,
    "success": 3,
    "passed": true
  }
}
Peripheral URL Configuration
Environment Variable	Default	Description
PRINTER_URL	http://localhost:5007	Printer service URL
CASH_DRAWER_URL	http://localhost:5010	Cash drawer service URL
SCANNER_URL	http://localhost:5003	Scanner service URL
CARD_READER_URL	http://localhost:5009	Card reader service URL