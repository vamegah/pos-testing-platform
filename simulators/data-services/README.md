# simulators/data-services/README.md

# Data Services Layer (D3)

## Overview

Transactional persistence check (an order write is retrievable afterward) + a lightweight analytics/data-lake event sink.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Order Persistence | ✅ | Write/read orders from SQLite |
| Analytics Event Sink | ✅ | Collect analytics events |
| Event Count Verification | ✅ | One event per completed sale |
| Data Lake Simulation | ✅ | Analytics data storage |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/data-services/order` | POST | Store order |
| `/data-services/order/<order_id>` | GET | Get order |
| `/data-services/analytics/event` | POST | Store analytics event |
| `/data-services/analytics/events` | GET | Get analytics events |
| `/data-services/analytics/summary` | GET | Get analytics summary |
| `/data-services/test/scenario` | POST | Run test scenarios |

## Test Scenarios

| Scenario | Description |
|----------|-------------|
| `persistence` | Write order → read it back |
| `analytics_sink` | Store analytics event → verify count |
| `event_count_verification` | 3 sales → 3 events |

## Usage

### Start the Service

```bash
cd simulators/data-services
pip install -r requirements.txt
python simulator.py

Store an Order
bash
curl -X POST http://localhost:8088/data-services/order \
  -H "Content-Type: application/json" \
  -d '{"order_id": "ORD-001", "customer_id": "CUST-001", "items": [{"sku": "SKU-1001", "quantity": 2, "price": 2.99}], "total": 5.98, "state": "created"}'
Get an Order
bash
curl http://localhost:8088/data-services/order/ORD-001
Store Analytics Event
bash
curl -X POST http://localhost:8088/data-services/analytics/event \
  -H "Content-Type: application/json" \
  -d '{"event_type": "sale_completed", "order_id": "ORD-001", "customer_id": "CUST-001", "event_data": {"amount": 5.98}}'
Run Test Scenarios
bash
curl -X POST http://localhost:8088/data-services/test/scenario \
  -d '{"scenario": "all"}' -H "Content-Type: application/json"