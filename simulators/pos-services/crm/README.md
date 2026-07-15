# simulators/pos-services/crm/README.md

# CRM Microservice (D2)

## Overview

Customer profile lookup/creation tied to a transaction.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Customer Creation | ✅ | Create new customer profile |
| Customer Lookup | ✅ | By ID, email, or phone |
| Guest/New Path | ✅ | Create guest profile for unknown customers |
| Transaction Recording | ✅ | Record customer transactions |
| Loyalty Tier | ✅ | Standard/Silver/Gold based on spending |
| Customer Stats | ✅ | Transaction count, total spent |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/crm/customer` | POST | Create customer |
| `/crm/customer/<id>` | GET | Get customer |
| `/crm/customer/lookup` | POST | Lookup by email/phone |
| `/crm/customer/<id>/update` | PUT | Update customer |
| `/crm/customer/<id>/transaction` | POST | Record transaction |
| `/crm/customer/<id>/transactions` | GET | Get transaction history |
| `/crm/customers` | GET | List all customers |
| `/crm/test/scenario` | POST | Run test scenarios |

## Usage

### Start the Service

```bash
cd simulators/pos-services/crm
pip install -r requirements.txt
python simulator.py

Create Customer
bash
curl -X POST http://localhost:8087/crm/customer \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "email": "john@example.com", "phone": "555-1234"}'
Lookup Customer
bash
curl -X POST http://localhost:8087/crm/customer/lookup \
  -H "Content-Type: application/json" \
  -d '{"email": "john@example.com"}'
Record Transaction
bash
curl -X POST http://localhost:8087/crm/customer/CUST-000001/transaction \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-001", "amount": 10.00, "items": [{"sku": "SKU-1001", "quantity": 1, "price": 2.99}]}'
Run Test Scenarios
bash
curl -X POST http://localhost:8087/crm/test/scenario \
  -d '{"scenario": "create_customer"}' -H "Content-Type: application/json"
Test Scenarios
Scenario	Description
create_customer	Create a new customer
lookup_existing	Look up existing customer
lookup_guest	Look up non-existent (guest)
record_transaction	Record customer transaction
Loyalty Tiers
Tier	Requirement	Bonus Points
Standard	Default	0
Silver	$1000 spent	100
Gold	$5000 spent	200