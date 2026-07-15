# simulators/gateway/README.md

# API Gateway & Integration Layer (D1)

## Overview

Lightweight local gateway in front of the cloud microservices with routing, mock auth-token check, basic rate-limiting, and event-routing (pub/sub).

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Routing | ✅ | Routes requests to backend services |
| Auth Check | ✅ | Validates Bearer tokens |
| Rate Limiting | ✅ | Per-client rate limiting |
| Event Routing | ✅ | Pub/sub event distribution |
| Event History | ✅ | All events are logged |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/gateway/auth/token` | POST | Create auth token |
| `/gateway/events/subscribe` | POST | Subscribe to events |
| `/gateway/events` | GET | Get event history |
| `/gateway/events/publish` | POST | Publish an event |
| `/gateway/rate-limit/status` | GET | Rate limit status |
| `/gateway/api/<service>/<path>` | * | Proxy to backend |

## Usage

### Start the Gateway

```bash
cd simulators/gateway
pip install -r requirements.txt
python simulator.py

Get an Auth Token
bash
curl -X POST http://localhost:5014/gateway/auth/token \
  -H "Content-Type: application/json" \
  -d '{"client_id": "client-001", "role": "pos"}'
Make an Authenticated Request
bash
curl http://localhost:5014/gateway/api/pricing/price/SKU-1001 \
  -H "Authorization: Bearer valid-token-123"
Subscribe to Events
bash
curl -X POST http://localhost:5014/gateway/events/subscribe \
  -H "Content-Type: application/json" \
  -d '{"event_type": "payment.completed", "subscriber_url": "http://localhost:9999"}'
Publish an Event
bash
curl -X POST http://localhost:5014/gateway/events/publish \
  -H "Content-Type: application/json" \
  -d '{"event_type": "payment.completed", "event_data": {"transaction_id": "txn-001"}}'
Run Test Scenarios
bash
curl -X POST http://localhost:5014/gateway/test/scenario \
  -d '{"scenario": "auth_success"}' -H "Content-Type: application/json"
Test Scenarios
Scenario	Description
auth_success	Valid token succeeds
auth_failure	Invalid token fails (401)
rate_limit	Rate limit trips after N requests
event_pubsub	Event is published and delivered