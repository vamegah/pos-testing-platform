# simulators/commerce-scale-unit/README.md

# Commerce Scale Unit (C1)

## Overview

The Commerce Scale Unit is a store/edge-tier facade that aggregates the existing pricing, promotions, and tax services into one business-logic unit. It includes edge-autonomy: keeps working when the cloud layer D is unreachable using cached data.

## Architecture
┌─────────────────┐
│ POS App (B1) │
└────────┬────────┘
│
▼
┌─────────────────┐ ┌─────────────────┐
│ Commerce │────▶│ Pricing (D2) │
│ Scale Unit │────▶│ Promotions (D2)│
│ (C1) │────▶│ Tax (D2) │
└─────────────────┘ └─────────────────┘
│
▼
┌─────────────────┐
│ Local Cache │
│ (C2) │
└─────────────────┘


## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Pricing Aggregation | ✅ | Gets prices for basket items |
| Tax Calculation | ✅ | Calculates tax for region |
| Promotion Lookup | ✅ | Applies applicable promotions |
| Edge Autonomy | ✅ | Works when cloud is unreachable |
| Cache Management | ✅ | Local cache for catalog/regions |
| Cloud Sync | ✅ | Periodic sync from cloud |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/commerce-scale-unit/checkout/price` | POST | Get price for basket |
| `/commerce-scale-unit/cloud/status` | GET | Cloud availability status |
| `/commerce-scale-unit/cloud/simulate-unreachable` | POST | Simulate cloud outage |
| `/commerce-scale-unit/cloud/sync` | POST | Sync cloud cache |
| `/commerce-scale-unit/cache` | GET | Get cache status |
| `/commerce-scale-unit/edge-autonomy` | POST | Run edge-autonomy test |

## Usage

### Start the Commerce Scale Unit

```bash
cd simulators/commerce-scale-unit
pip install -r requirements.txt
python simulator.py

Checkout Price
bash
curl -X POST http://localhost:5012/commerce-scale-unit/checkout/price \
  -H "Content-Type: application/json" \
  -d '{"items": [{"sku": "SKU-1001", "quantity": 2}], "region": "CA"}'
Check Cloud Status
bash
curl http://localhost:5012/commerce-scale-unit/cloud/status
Simulate Cloud Unreachable
bash
curl -X POST http://localhost:5012/commerce-scale-unit/cloud/simulate-unreachable \
  -H "Content-Type: application/json" \
  -d '{"unreachable": true, "duration_seconds": 60}'
Run Edge-Autonomy Test
bash
curl -X POST http://localhost:5012/commerce-scale-unit/edge-autonomy
Edge-Autonomy Test
The edge-autonomy test verifies that the Commerce Scale Unit continues to work when the cloud is unreachable. It:

Ensures the local cache is populated

Simulates cloud unreachable

Processes a checkout using cached data

Verifies the checkout succeeds with source="cache"

Restores cloud connectivity

Example output:

json
{
  "test": "edge_autonomy",
  "status": "passed",
  "checkout": {
    "status": "success",
    "subtotal": 5.98,
    "tax": 0.43,
    "total": 6.41,
    "source": "cache"
  },
  "cache_size": 10,
  "regions_cached": 5
}
Configuration
Environment Variable	Default	Description
PRICING_SERVICE_URL	http://pricing-service:8081	Pricing service URL
PROMOTIONS_SERVICE_URL	http://promotions-service:8082	Promotions service URL
TAX_SERVICE_URL	http://tax-service:8083	Tax service URL
CACHE_TTL_SECONDS	300	Cache TTL in seconds
CLOUD_UNREACHABLE	false	Simulate cloud unreachable