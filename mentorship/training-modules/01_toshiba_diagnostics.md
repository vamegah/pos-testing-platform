# mentorship/training-modules/01_toshiba_diagnostics.md

# Training Module 01: Toshiba Diagnostics

## Overview

This module introduces the Toshiba Diagnostics suite and its role in POS testing. It covers the Minimal UI mode, diagnostic tools, and common diagnostic procedures.

**Duration:** 60 minutes  
**Prerequisites:** None  
**Learning Objectives:**
- Understand the purpose of Toshiba Diagnostics
- Create and use a bootable Diagnostics USB key
- Navigate the Minimal UI (2x20 display)
- Run basic diagnostic tests
- Interpret diagnostic results

---

## 1. What is Toshiba Diagnostics?

Toshiba Diagnostics is a suite of tools used to test and validate Toshiba POS systems. It provides:

- **Hardware validation** – Tests CPU, memory, storage, and peripherals
- **Firmware management** – Reads and updates firmware versions
- **Vital Product Data (VPD)** – Reads and writes device metadata
- **Peripheral testing** – Validates scanners, printers, PIN pads, and scales
- **iButton authentication** – Secures diagnostic access

### 1.1 When to Use Diagnostics

| Scenario | Use Diagnostics? |
|----------|------------------|
| New POS system deployment | ✅ Yes – validate hardware |
| Firmware upgrade | ✅ Yes – verify upgrade success |
| Hardware failure investigation | ✅ Yes – identify failed components |
| Regular maintenance | ✅ Yes – proactive health checks |
| Software testing | ❌ No – use API tests instead |

---

## 2. Creating a Bootable Diagnostics USB Key

### 2.1 Prerequisites

- Windows PC (for USB key creation)
- Toshiba Diagnostics ISO file
- USB flash drive (2GB minimum)
- Rufus or similar tool

### 2.2 Steps

1. **Download** the Toshiba Diagnostics ISO from the internal portal
2. **Extract** the ISO file
3. **Create bootable USB** using Rufus:
   - Device: Select your USB drive
   - Boot selection: Select the extracted ISO
   - Partition scheme: MBR
   - File system: FAT32
4. **Click Start** and wait for completion

### 2.3 Enabling Minimal UI Mode

The Minimal UI mode supports 2x20 character displays (common on TCx700, TCx300, SurePOS 700/300).

**Steps:**
1. Navigate to `/utilities/diags2x20/` on the USB key
2. Copy `diags2x20.properties` to the root directory
3. Configure RS-232 parameters (see Module 2)
4. Boot the POS system from the USB key

---

## 3. Navigation in Minimal UI

### 3.1 Navigation Keys

| Function | Key | Description |
|----------|-----|-------------|
| **Up** | ↑ | Select item above |
| **Down** | ↓ | Select item below |
| **Enter** | ↵ | Execute selected item |
| **Back** | Esc | Return to previous menu |
| **Home** | Home | Return to main menu |
| **Print** | P | Print current menu/screen |

### 3.2 Main Menu Structure
Main Menu
├── Diagnostics
│ ├── CPU Test
│ ├── Memory Test
│ ├── Storage Test
│ └── Peripheral Test
│ ├── RS-232 Scanner
│ ├── RS-232 Printer
│ ├── RS-232 Scale
│ └── USB Devices
├── VPD Management
│ ├── Read VPD
│ ├── Write VPD
│ └── Verify VPD
├── iButton Authentication
│ ├── Authenticate
│ └── Status
└── System Information
├── Hardware Info
├── Firmware Version
└── Serial Number

---

## 4. Common Diagnostic Tests

### 4.1 iButton Authentication

**Purpose:** Verify the diagnostic user is authorized.

**Steps:**
1. Navigate to `iButton Authentication`
2. Insert iButton device
3. Select `Authenticate`
4. Verify result: Pass/Fail

**Expected Result:** Authentication Passed

**Troubleshooting:**
- If failed: Check iButton connection, try a different iButton
- If not found: Ensure iButton is properly inserted

### 4.2 RS-232 Communication Test

**Purpose:** Verify communication with serial peripherals.

**Steps:**
1. Navigate to `Diagnostics` → `Peripheral Test` → `RS-232`
2. Select the peripheral type (Scanner/Printer/Scale)
3. Send a test command
4. Verify response

**Expected Result:** Data is received and correctly formatted

**Troubleshooting:**
- No response: Check cable, baud rate settings
- Garbled data: Check data bits, parity
- Timeout: Increase diagnostic.timeout setting

### 4.3 VPD Management

**Purpose:** Read or write Vital Product Data (serial, firmware).

**Steps:**
1. Navigate to `VPD Management`
2. Select `Read VPD`
3. Verify fields: serial_number, firmware_version, model_number
4. (Optional) Write VPD for provisioning

**Expected Result:** All fields are present and match expected values

---

## 5. Troubleshooting Common Issues

| Symptom | Possible Cause | Solution |
|---------|---------------|----------|
| USB key not booting | BIOS boot order | Change boot order to USB first |
| 2x20 display blank | .properties file missing | Copy diags2x20.properties to root |
| iButton not recognized | Hardware issue | Check iButton seating and contacts |
| RS-232 no response | Baud rate mismatch | Verify baud rate matches peripheral |
| Diagnostic menu missing | Wrong USB key version | Use correct Diagnostics version |

---

## 6. Hands-On Exercises

### Exercise 1: Create Diagnostics USB Key
1. Obtain a USB flash drive
2. Create a bootable Diagnostics key
3. Enable Minimal UI mode
4. Boot a TCx terminal
5. Verify the 2x20 display shows the main menu

### Exercise 2: iButton Authentication
1. Insert the Diagnostics USB key
2. Boot the terminal
3. Navigate to iButton Authentication
4. Insert an iButton device
5. Verify authentication passes

### Exercise 3: RS-232 Scanner Test
1. Connect an RS-232 scanner
2. Configure diags2x20.properties for the scanner
3. Boot into Minimal UI
4. Navigate to RS-232 Scanner test
5. Scan a test barcode
6. Verify the data displays correctly

---

## 7. Resources

- [Toshiba Diagnostics Minimal UI Setup Guide](../docs/environment-setup/toshiba_diagnostics_setup.md)
- [RS-232 Configuration Guide](../docs/environment-setup/rs232_configuration_guide.md)
- TCx Hardware Troubleshooting (Module 2)
- Retail-Hardened Testing (Module 3)

---

## 8. Knowledge Check

1. What is the purpose of the Minimal UI mode?
2. How do you enable Minimal UI mode on a Diagnostics USB key?
3. What navigation keys are available on a 2x20 display?
4. What does the iButton authentication test validate?
5. How do you test RS-232 communication with a scanner?