# simulators/third-party-mocks/loyalty/README.md

# Loyalty Program Third-Party Mock (E)

## Overview

Points accrual/redemption tied to the CRM service, called out-of-process to simulate a real third-party boundary.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Points Accrual | ✅ | Earn points on purchase |
| Points Redemption | ✅ | Redeem points for discount |
| Balance Lookup | ✅ | Check point balance |
| Tier Multipliers | ✅ | Standard(1x), Silver(1.5x), Gold(2x), Platinum(3x) |
| Redemption Codes | ✅ | Generate and validate codes |
| Transaction History | ✅ | Full audit log |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/loyalty/accrue` | POST | Accrue points |
| `/loyalty/redeem` | POST | Redeem points |
| `/loyalty/balance/<customer_id>` | GET | Get balance |
| `/loyalty/redemption/validate/<code>` | GET | Validate redemption code |
| `/loyalty/redemption/use` | POST | Use redemption code |
| `/loyalty/history/<customer_id>` | GET | Get history |
| `/loyalty/test/scenario` | POST | Run test scenarios |

## Usage

### Start the Mock

```bash
cd simulators/third-party-mocks/loyalty
pip install -r requirements.txt
python simulator.py

### Accrue Points
bash
curl -X POST http://localhost:8089/loyalty/accrue \
  -H "Content-Type: application/json" \
  -d '{"customer_id": "CUST-000001", "amount": 10.00, "tier": "silver"}'

### Redeem Points
bash
curl -X POST http://localhost:8089/loyalty/redeem \
  -H "Content-Type: application/json" \
  -d '{"customer_id": "CUST-000001", "points": 100}'


### Check Balance
bash
curl http://localhost:8089/loyalty/balance/CUST-000001

### Test Scenarios
Scenario	Description
accrue	Accrue points with tier multiplier
redeem	Redeem points successfully
redeem_insufficient	Insufficient points → error