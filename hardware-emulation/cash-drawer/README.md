# hardware-emulation/cash-drawer/README.md

# Cash Drawer Peripheral Simulator (B2)

## Overview

This simulator models the physical cash drawer open/close signal fired only on completed cash tender, plus a "drawer left open" alert scenario.

## Capabilities

| Capability | Status | Description |
|------------|--------|-------------|
| Open Signal | ✅ | Opens only on completed cash tender |
| Close Signal | ✅ | Manual or automatic close |
| Left Open Alert | ✅ | Alert after configurable timeout |
| State Tracking | ✅ | Open/Closed/Alert states |
| Events | ✅ | Full event history |

## Architecture
┌─────────────────┐ ┌─────────────────────┐
│ Cash Tender │ │ Cash Drawer │
│ Completed │────▶│ Simulator │
│ (cash only) │ │ (B2) │
└─────────────────┘ └─────────────────────┘
│
▼
┌──────────────┐
│ Open Signal │
│ (fired) │
└──────────────┘

## States

| State | Description |
|-------|-------------|
| `closed` | Drawer is closed (default) |
| `open` | Drawer is open |
| `alert` | Drawer left open alert triggered |

## Tender Types

| Type | Opens Drawer |
|------|--------------|
| `cash` | ✅ Yes |
| `card` | ❌ No |
| `nfc` | ❌ No |
| `biometric` | ❌ No |

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/cash-drawer/state` | GET | Get current state |
| `/cash-drawer/open` | POST | Open drawer (cash only) |
| `/cash-drawer/close` | POST | Close drawer |
| `/cash-drawer/alert/status` | GET | Get alert status |
| `/cash-drawer/alert/dismiss` | POST | Dismiss alert |
| `/cash-drawer/events` | GET | Get events |
| `/cash-drawer/clear` | POST | Clear state |
| `/cash-drawer/reset` | POST | Reset drawer |
| `/cash-drawer/test/scenario` | POST | Run test scenario |

## Usage

### Start the Simulator

```bash
cd hardware-emulation/cash-drawer
pip install -r requirements.txt
python simulator.py

Open Drawer (Cash Tender)
bash
curl -X POST http://localhost:5010/cash-drawer/open \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-001", "tender_type": "cash", "amount": 10.00}'
Open Drawer (Non-Cash Tender — Ignored)
bash
curl -X POST http://localhost:5010/cash-drawer/open \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-002", "tender_type": "card", "amount": 10.00}'
Close Drawer
bash
curl -X POST http://localhost:5010/cash-drawer/close \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "txn-001"}'
Run Test Scenarios
bash
# Happy path: cash tender → open → close
curl -X POST http://localhost:5010/cash-drawer/test/scenario \
  -d '{"scenario": "happy_path"}' -H "Content-Type: application/json"

# Left open alert: cash tender → open → leave open
curl -X POST http://localhost:5010/cash-drawer/test/scenario \
  -d '{"scenario": "left_open_alert"}' -H "Content-Type: application/json"

# Non-cash tender: drawer does not open
curl -X POST http://localhost:5010/cash-drawer/test/scenario \
  -d '{"scenario": "non_cash_tender"}' -H "Content-Type: application/json"
Events
Event Type	Description
drawer_opened	Drawer was opened
drawer_closed	Drawer was closed
alert_drawer_left_open	Drawer left open alert
drawer_reset	Drawer was reset
Configuration
Environment Variable	Default	Description
DRAWER_OPEN_ALERT_TIMEOUT	30	Seconds before alert triggers
AUTO_CLOSE_TIMEOUT	300	Seconds before auto-close