# hardware-emulation/card-reader/README.md

# Card Reader Peripheral Simulator (B2)

## Overview

This simulator models the physical EMV dip/tap/swipe + PIN-entry device protocol. It is **distinct from** the backend payment-gateway service it routes to.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| EMV Dip | ✅ | Insert card into reader |
| EMV Tap | ✅ | Contactless tap |
| EMV Swipe | ✅ | Magnetic stripe swipe |
| PIN Entry | ✅ | 4-6 digit PIN entry |
| PIN Blocking | ✅ | Block after 3 failed attempts |
| Tender Events | ✅ | Emits events for each tender action |

## Architecture
┌─────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
│ Card Reader │ │ Card Reader │ │ mock-payment- │
│ Simulator │────▶│ Simulator │────▶│ gateway │
│ (B2) │ │ (tender events) │ │ (Phase 1.4) │
└─────────────────┘ └─────────────────────┘ └─────────────────────┘

## Test Card Sentinels

| Card Number | Status | PIN Required |
|-------------|--------|--------------|
| 4111111111111111 | Approved | No |
| 4111111111110000 | Declined | No |
| 4111111111112222 | Approved | No |
| 4111111111113333 | PIN Required | 1234 |
| 4111111111114444 | Declined | No |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/card-reader/state` | GET | Get current state |
| `/card-reader/insert` | POST | Insert a card |
| `/card-reader/pin` | POST | Enter PIN |
| `/card-reader/process` | POST | Process payment |
| `/card-reader/eject` | POST | Eject card |
| `/card-reader/events` | GET | Get tender events |
| `/card-reader/clear` | POST | Clear state |

## Usage

### Start the Simulator

```bash
cd hardware-emulation/card-reader
pip install -r requirements.txt
python simulator.py

Insert a Card (PIN not required)
bash
curl -X POST http://localhost:5009/card-reader/insert \
  -H "Content-Type: application/json" \
  -d '{"card_number": "4111111111111111", "entry_method": "dip"}'
Insert a Card (PIN required)
bash
curl -X POST http://localhost:5009/card-reader/insert \
  -H "Content-Type: application/json" \
  -d '{"card_number": "4111111111113333", "entry_method": "tap"}'
Enter PIN
bash
curl -X POST http://localhost:5009/card-reader/pin \
  -H "Content-Type: application/json" \
  -d '{"pin": "1234"}'
Get Tender Events
bash
curl http://localhost:5009/card-reader/events
Tender Events
Event Type	Description
pin_required	PIN entry required
pin_accepted	PIN entered correctly
pin_blocked	PIN blocked (too many attempts)
approved	Payment approved
declined	Payment declined
Testing
bash
# Happy path
curl -X POST http://localhost:5009/card-reader/insert \
  -d '{"card_number": "4111111111111111"}' -H "Content-Type: application/json"
curl http://localhost:5009/card-reader/state

# PIN required path
curl -X POST http://localhost:5009/card-reader/insert \
  -d '{"card_number": "4111111111113333"}' -H "Content-Type: application/json"
curl -X POST http://localhost:5009/card-reader/pin \
  -d '{"pin": "1234"}' -H "Content-Type: application/json"

# Declined path
curl -X POST http://localhost:5009/card-reader/insert \
  -d '{"card_number": "4111111111110000"}' -H "Content-Type: application/json"