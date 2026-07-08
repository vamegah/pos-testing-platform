# simulators/kiosk-ui-harness/README.md

# Kiosk UI Simulator

## Overview

A touch-first, data-driven UI harness for POS product profiles. Renders 5 screen states (welcome, basket, payment, receipt, assist) based on loaded Phase-12 capability profiles.

## Features

- **Data-driven:** Load any Phase-12 product profile JSON to toggle UI elements
- **Touch-first:** Minimum 44px tap targets, no hover-dependent UI
- **5 screen states:** Welcome, Basket, Payment, Receipt, Assist/Exception overlay
- **Peripheral indicators:** Shows enabled/disabled peripherals from profile
- **Payment method selection:** Dynamically rendered from profile

## Usage

### Start the UI

```bash
cd simulators/kiosk-ui-harness

# Using Python's built-in server
python3 -m http.server 8080

# Or using the serve script
../../scripts/serve_ui_harness.sh

Load a Profile
Select a product profile from the dropdown

Click "Load"

The UI adapts to the selected profile

Navigation
Welcome → Basket: Click "Start Scanning"

Basket → Payment: Click "Pay Now"

Basket → Assist: Click "Need Help" (if available)

Payment → Receipt: Click "Authorize Payment"

Receipt → Welcome: Click "New Transaction"

Profile Requirements
Each profile JSON must have at minimum:

json
{
  "name": "Product Name",
  "capabilities": {
    "payment": {
      "methods": ["card", "nfc"]
    },
    "peripherals": {
      "scanner": true,
      "printer": true
    }
  }
}
Screens
Screen	Description
Welcome	Branded entry screen with profile info
Basket	Item list, subtotal, peripheral indicators
Payment	Total, payment method selection, authorize
Receipt	Transaction confirmation with itemized receipt
Assist	Overlay for exceptions/assistance
Keyboard Shortcuts
R - Reset to welcome screen

Escape - Dismiss assist overlay

text

```json
// simulators/kiosk-ui-harness/profiles/elera.json
{
  "name": "ELERA",
  "capabilities": {
    "display": { "size": 15.6, "orientation": "landscape" },
    "payment": { "methods": ["card", "mobile_wallet", "gift_card"] },
    "peripherals": { "scanner": true, "printer": true, "scale": true, "pin_pad": true },
    "modes": { "pos": true, "self_service": true },
    "hooks": { "produce_recognition": true, "security_suite": true }
  }
}
json
// simulators/kiosk-ui-harness/profiles/mxp-smart-wall.json
{
  "name": "MxP SMART | wall",
  "capabilities": {
    "display": { "size": 10.1, "orientation": "landscape" },
    "payment": { "methods": ["card", "mobile_wallet"] },
    "peripherals": { "scanner": true, "printer": true, "scale": false, "pin_pad": true },
    "space_constraints": { "max_depth_cm": 10, "max_width_cm": 30, "mounting": "wall" }
  }
}