# simulators/third-party-mocks/erp/README.md

# ERP Integration Mock (E)

## Overview

End-of-day/batch export of transactions to a mocked ERP endpoint.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Batch Export | ✅ | Export pending transactions |
| End-of-Day Close | ✅ | Complete batch export |
| Export File Generation | ✅ | JSON export file |
| Record Count Validation | ✅ | Validate record structure |
| Export History | ✅ | Track all exports |
| Transaction Queue | ✅ | Pending/exported tracking |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/erp/transaction` | POST | Add transaction to queue |
| `/erp/export` | POST | Trigger export |
| `/erp/export/file/<batch_id>` | GET | Download export file |
| `/erp/export/status` | GET | Export status |
| `/erp/export/history` | GET | Export history |
| `/erp/pending` | GET | Pending transactions |
| `/erp/exported` | GET | Exported transactions |
| `/erp/test/scenario` | POST | Run test scenarios |

## Usage

### Start the Mock

```bash
cd simulators/third-party-mocks/erp
pip install -r requirements.txt
python simulator.py

Add Transaction to Queue
bash
curl -X POST http://localhost:8090/erp/transaction \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "TXN-001", "order_id": "ORD-001", "customer_id": "CUST-001", "amount": 10.00}'
Trigger Export
bash
curl -X POST http://localhost:8090/erp/export
Get Export History
bash
curl http://localhost:8090/erp/export/history
Download Export File
bash
curl http://localhost:8090/erp/export/file/BATCH-20260101-123456
Test Scenarios
Scenario	Description
export	Add transactions → export → verify count
export_empty	Export with no transactions
batch_validation	Validate export file structure
```