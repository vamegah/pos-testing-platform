# docs/environment-setup/rs232_configuration_guide.md

# RS-232 Configuration Guide for Toshiba POS Testing

## Overview

This guide explains the RS-232 configuration parameters used in Toshiba POS diagnostic testing. It covers the `diags2x20.properties` template file and provides guidance on configuring RS-232 peripherals (scanners, printers, scales, PIN pads) for testing.

This guide supports the "real-machine-less evaluation" approach by enabling software-based peripheral simulation alongside physical hardware testing.

---

## What is RS-232?

RS-232 is a serial communication standard widely used in POS systems for connecting peripherals. Common POS peripherals that use RS-232 include:

- **Barcode Scanners** - Read product codes
- **Receipt Printers** - Print customer receipts
- **Scales** - Weigh products (deli, produce)
- **PIN Pads** - Process card payments
- **Cash Drawers** - Open during transactions

---

## Configuration File: diags2x20.properties

The `diags2x20.properties` file configures RS-232 communication for the Toshiba Diagnostics Minimal UI (2x20 display mode). It must be placed in the **root directory** of the Diagnostics USB key.

### File Location
USB_ROOT/
├── diags2x20.properties # <-- Place here
├── utilities/
│ └── diags2x20/
│ ├── diags2x20.properties # Source template
│ └── ...
└── ...


### Parameter Reference

#### Communication Parameters

| Parameter | Description | Options | Default |
|-----------|-------------|---------|---------|
| `baudrate` | Communication speed (bits per second) | 9600, 19200, 38400, 57600, 115200 | 9600 |
| `databits` | Number of data bits per character | 7, 8 | 8 |
| `stopbits` | Number of stop bits per character | 1, 2 | 1 |
| `parity` | Error checking method | None, Even, Odd | None |
| `protocol` | Flow control handshake method | XON/XOFF, DTR/DSR | XON/XOFF |

#### Why These Matter

- **Baud Rate**: Must match the peripheral's configured baud rate. If mismatched, data will be garbled or not received.
- **Data Bits**: Most modern POS peripherals use 8 data bits.
- **Stop Bits**: 1 stop bit is standard for most applications.
- **Parity**: "None" is most common; parity adds error detection but slows communication.
- **Protocol**: XON/XOFF is software flow control; DTR/DSR is hardware flow control.

#### Peripheral Settings

| Parameter | Description | Options | Default |
|-----------|-------------|---------|---------|
| `printer.model` | Printer model for diagnostics | TCx80, TCx60, SurePOS, Generic | TCx80 |
| `scanner.model` | Scanner model for diagnostics | Honeywell, Motorola, Datalogic, Generic | Honeywell |
| `scale.model` | Scale model for diagnostics | Micros, Mettler, Generic | Micros |

#### Diagnostic Settings

| Parameter | Description | Default |
|-----------|-------------|---------|
| `diagnostic.timeout` | Time to wait for peripheral response (seconds) | 30 |
| `diagnostic.retries` | Number of retry attempts for failed tests | 3 |
| `iButton.authentication.required` | Require iButton authentication | true |

#### Display Settings (Minimal UI)

| Parameter | Description | Default |
|-----------|-------------|---------|
| `display.lines` | Number of display lines (fixed) | 2 |
| `display.characters` | Characters per line (fixed) | 20 |
| `display.menu.timeout` | Time before returning to main menu (0 = never) | 60 |

#### Logging

| Parameter | Description | Default |
|-----------|-------------|---------|
| `log.level` | Detail level for diagnostic logs | INFO |
| `log.file` | Path where diagnostic logs are written | /tmp/diagnostics.log |

---

## Setup Procedure

### Step 1: Create the Configuration File

1. Copy `diags2x20.properties.template` from the project repository:
   ```bash
   cp hardware-emulation/virtual-com-ports/diags2x20.properties.template diags2x20.properties

Open the file and adjust parameters for your peripheral:

properties
baudrate=9600
databits=8
stopbits=1
parity=None
protocol=XON/XOFF
Step 2: Copy to Diagnostics USB Key
Insert your Toshiba Diagnostics USB key.

Copy the diags2x20.properties file to the root directory:

bash
cp diags2x20.properties /media/usb/
Step 3: Boot the POS System
Insert the USB key into the POS terminal.

Power on or restart the system.

Press the boot menu key (usually F12) to select USB boot.

The system will boot into Toshiba Diagnostics Minimal UI with your RS-232 settings.

Step 4: Verify Communication
Navigate to the peripheral test menu.

Send a test command (e.g., scan a barcode, print a test receipt).

Verify the expected response is received.

Common Configuration Scenarios
Scenario 1: Scanner with Standard Settings
properties
baudrate=9600
databits=8
stopbits=1
parity=None
protocol=XON/XOFF
scanner.model=Honeywell
Scenario 2: Printer with Hardware Flow Control
properties
baudrate=19200
databits=8
stopbits=1
parity=None
protocol=DTR/DSR
printer.model=TCx80
Scenario 3: Scale with Custom Baud Rate
properties
baudrate=38400
databits=8
stopbits=1
parity=Even
protocol=XON/XOFF
scale.model=Micros
Scenario 4: Test Mode (Software Simulation)
properties
baudrate=9600
databits=8
stopbits=1
parity=None
protocol=XON/XOFF
test.mode=true
test.data.path=/media/usb/test_data/
Troubleshooting
Issue: No Communication
Symptom	Possible Cause	Solution
No response from peripheral	Baud rate mismatch	Verify baud rate matches peripheral configuration
Garbled data	Data bits or parity mismatch	Check databits and parity settings
Timeout errors	Incorrect protocol	Try switching between XON/XOFF and DTR/DSR
Issue: iButton Authentication Fails
Symptom	Possible Cause	Solution
iButton not recognized	Physical connection issue	Check iButton seating and contacts
Authentication fails	Wrong iButton type	Use correct iButton for the terminal
System asks for iButton every time	Authentication required	Set iButton.authentication.required=false for testing
Issue: Logs Not Being Written
Symptom	Possible Cause	Solution
No log file created	Directory doesn't exist	Ensure /tmp/ is writable
Logs not detailed enough	Log level too high	Set log.level=DEBUG for more detail
Testing RS-232 with Virtual COM Ports
For development and automated testing, you can use the socat_rs232_simulator.sh script to create virtual COM ports:

bash
# Start virtual COM port simulator
./hardware-emulation/virtual-com-ports/socat_rs232_simulator.sh start

# Configure diags2x20.properties to use the virtual device
# device.path=/tmp/pty-sim-1

# Run diagnostics against the virtual peripheral
This enables testing without physical hardware, following the "real-machine-less evaluation" approach.

Related Documents
Toshiba Diagnostics Minimal UI Setup Guide

Environmental Test Case Templates

RS-232 Peripheral Test Cases

Revision History
Date	Version	Author	Changes
2026-01-15	1.0	POS Test Team	Initial creation
