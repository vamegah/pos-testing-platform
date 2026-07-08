# test-management/test-cases/diagnostic/TC_IBUTTON_001.md

# TC_IBUTTON_001 - iButton Authentication Test

## Test Case Information

| Field | Value |
|-------|-------|
| **Test Case ID** | TC_IBUTTON_001 |
| **Test Title** | Verify iButton Authentication via Toshiba Diagnostics Minimal UI |
| **Test Type** | Functional / Hardware Diagnostics |
| **Priority** | High |
| **Estimated Duration** | 5 minutes |
| **Automation Status** | Manual (automation candidate) |
| **Last Updated** | 2026-01-15 |
| **Author** | POS Test Engineering Team |

---

## Description

This test validates that iButton authentication works correctly when accessing the Toshiba Diagnostics Minimal UI. The iButton is a hardware authentication device used to secure diagnostic access to Toshiba POS systems.

**Reference:** Toshiba Diagnostics Minimal UI Setup Guide - iButton Authentication Section

---

## Prerequisites

### Hardware Requirements
- [ ] Toshiba POS terminal (TCx 810, TCx 700, TCx 300, or SurePOS series)
- [ ] Valid iButton device (assigned to the terminal)
- [ ] Toshiba Diagnostics USB key with Minimal UI configuration

### Software Requirements
- [ ] Toshiba Diagnostics Minimal UI bootable USB key
- [ ] `diags2x20.properties` file in the root of the USB key
- [ ] iButton authentication set to `true` in the properties file

### Environment
- [ ] POS terminal powered on and connected to power
- [ ] USB key inserted into a functional USB port
- [ ] 2x20 display connected and operational

---

## Test Steps (Given/When/Then)

### GIVEN

1. The Toshiba Diagnostics USB key is inserted into the POS terminal
2. The terminal is powered off
3. The 2x20 display is connected and functional
4. The `diags2x20.properties` file contains:
   ```properties
   iButton.authentication.required=true
   display.lines=2
   display.characters=20
WHEN
Power on the POS terminal

The terminal boots from the USB key

Minimal UI displays on the 2x20 screen

Navigate to the iButton Authentication menu

Use the defined navigation keys (Up/Down/Enter)

Locate the "iButton Authentication" option in the menu

Insert the iButton device

Physically connect the iButton to the terminal's iButton reader

Wait for the system to detect the device

Execute the authentication

Select the "Authenticate" option

Wait for the system to verify the iButton

THEN
Authentication Success Scenario

The 2x20 display shows "Authentication Passed" or similar success message

Full diagnostic menu becomes available

All diagnostic functions are accessible

No error messages are displayed

Authentication Failure Scenario

The 2x20 display shows "Authentication Failed" or similar error message

Access to diagnostic functions is restricted

The system may prompt to retry or exit

An error code or reason is displayed

No iButton Present Scenario

The 2x20 display shows "iButton Not Found" or similar message

Authentication cannot proceed

The system prompts to insert an iButton

Expected Results
Scenario	Expected Result
Valid iButton	Authentication passes, full diagnostics menu accessible
Invalid iButton	Authentication fails, access restricted
No iButton	System reports iButton not found
Multiple iButtons	Only the correct iButton for the terminal passes
iButton Removed Mid-Session	Session remains active or re-authentication required
Validation Criteria
Authentication passes for valid iButton

Authentication fails for invalid iButton (error message displayed)

No system crashes or freezes during authentication

All menu items are accessible after successful authentication

Error messages are clear and actionable

The 2x20 display shows appropriate messages

Execution Log
Date	Executed By	Result	Comments
Defect Reporting
If this test fails, create a Hardware Defect in JIRA with:

Summary: [TC_IBUTTON_001] iButton authentication failed on [TCx Model]

TCx Model: [Enter model]

Serial Number: [Enter serial]

Firmware Version: [Enter version]

Diagnostic Error Code: [Error code from 2x20 display]

Test Case ID: TC_IBUTTON_001

Steps to Reproduce: [Paste steps from above]

Actual Result: [What happened]

Expected Result: [What should have happened]

Related Documentation
Toshiba Diagnostics Minimal UI Setup Guide

RS-232 Configuration Guide

VPD Validator Test