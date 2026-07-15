# simulators/local-data-cache/README.md

# Local Data Cache (C2)

## Overview

Explicit offline cache (embedded SQLite) for catalog/pricing, with cache-population, staleness/TTL, and reconnect-reconciliation tests.

## Architecture
┌─────────────────────┐
│ Commerce Scale │
│ Unit (C1) │
└──────────┬──────────┘
│
▼
┌─────────────────────┐
│ Local Data Cache │
│ (C2) │
│ ┌───────────────┐ │
│ │ SQLite │ │
│ │ Database │ │
│ └───────────────┘ │
└─────────────────────┘

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Catalog Cache | ✅ | SKU → price/name/weight |
| Region Cache | ✅ | Region code → tax rate |
| TTL/Staleness | ✅ | Configurable TTL (default 5 min) |
| Cache Hit/Miss | ✅ | Returns price or null |
| Sync History | ✅ | Track sync operations |
| Pending Changes | ✅ | Track changes for reconciliation |
| Reconciliation | ✅ | Resolve pending changes |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/local-cache/populate` | POST | Populate cache |
| `/local-cache/catalog` | GET | Get all catalog items |
| `/local-cache/catalog/<sku>` | GET | Get specific catalog item |
| `/local-cache/regions` | GET | Get all regions |
| `/local-cache/regions/<code>` | GET | Get specific region |
| `/local-cache/stale` | GET | Check stale items |
| `/local-cache/reconcile` | POST | Reconcile pending changes |
| `/local-cache/pending-changes` | GET | Get pending changes |
| `/local-cache/sync-history` | GET | Get sync history |
| `/local-cache/test/scenarios` | POST | Run cache test scenarios |

## Usage

### Start the Cache

```bash
cd simulators/local-data-cache
pip install -r requirements.txt
python simulator.py

Populate Cache
bash
curl -X POST http://localhost:5013/local-cache/populate \
  -H "Content-Type: application/json" \
  -d '{
    "catalog": [
      {"sku": "SKU-1001", "name": "Milk", "price": 2.99, "weight_kg": 3.78},
      {"sku": "SKU-1002", "name": "Bread", "price": 1.49, "weight_kg": 0.45}
    ],
    "regions": [
      {"code": "CA", "name": "California", "tax_rate": 0.0725},
      {"code": "TX", "name": "Texas", "tax_rate": 0.0625}
    ]
  }'
Get Catalog
bash
curl http://localhost:5013/local-cache/catalog
Check Stale Items
bash
curl http://localhost:5013/local-cache/stale
Run Cache Test Scenarios
bash
curl -X POST http://localhost:5013/local-cache/test/scenarios \
  -H "Content-Type: application/json" \
  -d '{"scenario": "all"}'
Test Scenarios
Scenario	Description
cache_hit	Verify price exists in cache
cache_miss	Verify missing SKU returns null
stale	Verify stale detection works
reconcile	Verify reconciliation works
Example Test Output
json
{
  "test": "cache_scenarios",
  "scenario": "all",
  "results": {
    "cache_hit": {"sku": "SKU-1001", "found": true, "price": 2.99, "passed": true},
    "cache_miss": {"sku": "SKU-9999", "found": false, "passed": true},
    "stale": {"stale_count": 0, "fresh_count": 2, "passed": true},
    "reconcile": {"pending_before": 1, "pending_after": 0, "passed": true}
  },
  "summary": {"total": 4, "passed": 4, "all_passed": true}
}
Configuration
Environment Variable	Default	Description
CACHE_TTL_SECONDS	300	Cache TTL in seconds
CACHE_DB_PATH	local_cache.db	SQLite database path