# simulators/pos-services/order-processing/README.md

# Order Processing Microservice (D2)

## Overview

Order lifecycle state machine: created → paid → fulfilled/void, with an illegal-transition negative test.

## State Machine

┌─────────┐
│ CREATED │
└────┬────┘
│
┌────┴────┐
│ │
▼ ▼
┌───────┐ ┌──────┐
│ PAID │ │ VOID │
└───┬───┘ └──────┘
│
▼
┌──────────┐
│FULFILLED │
└──────────┘


## Valid Transitions

| From | To |
|------|----|
| CREATED | PAID, VOID |
| PAID | FULFILLED, VOID |
| FULFILLED | (terminal) |
| VOID | (terminal) |

## Illegal Transitions (Rejected)

- FULFILLED → CREATED
- FULFILLED → PAID
- FULFILLED → VOID
- VOID → CREATED
- VOID → PAID
- VOID → FULFILLED
- PAID → CREATED

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/order/state-machine` | GET | Get state machine |
| `/order/create` | POST | Create order |
| `/order/<order_id>` | GET | Get order |
| `/order/<order_id>/pay` | POST | Mark as paid |
| `/order/<order_id>/fulfill` | POST | Mark as fulfilled |
| `/order/<order_id>/void` | POST | Void order |
| `/order/history` | GET | Order history |
| `/order/test/illegal-transition` | POST | Illegal transition test |
| `/order/test/scenario` | POST | Test scenarios |

## Usage

### Start the Service

```bash
cd simulators/pos-services/order-processing
pip install -r requirements.txt
python simulator.py

Create Order
bash
curl -X POST http://localhost:8086/order/create \
  -H "Content-Type: application/json" \
  -d '{"items": [{"sku": "SKU-1001", "quantity": 2, "price": 2.99}], "customer_id": "cust-001"}'
Mark as Paid
bash
curl -X POST http://localhost:8086/order/ORD-000001/pay \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-12345", "amount": 5.98}'
Illegal Transition Test
bash
curl -X POST http://localhost:8086/order/test/illegal-transition \
  -H "Content-Type: application/json" \
  -d '{"from_state": "fulfilled", "to_state": "created"}'