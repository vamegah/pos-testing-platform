# mentorship/training-modules/03_retail_hardened_testing.md

# Training Module 03: Retail-Hardened Testing

## Overview

This module covers environmental and durability testing for POS systems, including temperature, fluid spills, dust, and electrostatic discharge (ESD) testing.

**Duration:** 60 minutes  
**Prerequisites:** Module 01, Module 02  
**Learning Objectives:**
- Understand retail-hardened testing requirements
- Execute environmental test cases
- Document environmental test results
- Identify environmental failure modes

---

## 1. What is Retail-Hardened Testing?

Retail-hardened testing validates that POS systems can withstand the harsh conditions of retail environments:

| Environment | Challenge |
|-------------|-----------|
| **Temperature** | -10°C to 40°C (cold stores, hot outdoor locations) |
| **Fluid Spills** | Coffee, soda, cleaning solutions |
| **Dust/Lint** | Dust from products, packaging, foot traffic |
| **ESD** | Static discharge from employees, customers |
| **Vibration** | From nearby machinery, high-traffic areas |

---

## 2. Environmental Testing

### 2.1 Temperature Testing

**Purpose:** Validate system operation at temperature extremes

**Test Case:** TC-TEMP-001

**Preconditions:**
- TCx terminal placed in environmental chamber
- Chamber stabilized at target temperature

**Steps:**
1. Stabilize chamber at -10°C
2. Power on the POS terminal
3. Run full diagnostic suite
4. Verify all peripherals function
5. Perform a complete transaction
6. Repeat at 40°C

**Expected Result:**
- System powers on successfully
- All diagnostics pass
- Peripherals function correctly
- Transaction completes successfully

**Failure Modes:**
- Power-on failure (cold)
- Component failure (heat)
- Display issues (slow refresh in cold)

### 2.2 Fluid Resistance Testing

**Purpose:** Validate system can withstand liquid exposure

**Test Case:** TC-FLUID-001

**Preconditions:**
- TCx terminal powered on
- All peripherals connected

**Steps:**
1. Pour 100ml of test fluid onto terminal surface
2. Wait 30 seconds
3. Wipe terminal clean
4. Attempt a standard transaction
5. Check all peripherals

**Expected Result:**
- System remains operational
- No error messages
- Transaction completes successfully
- All peripherals functional

**Failure Modes:**
- Short-circuit
- Touchscreen failure
- Peripheral connectivity loss

### 2.3 Dust and Lint Testing

**Purpose:** Validate system can withstand dust accumulation

**Test Case:** TC-DUST-001

**Preconditions:**
- TCx terminal running transaction simulation

**Steps:**
1. Introduce lint/dust particles into chamber
2. Circulate particles for 10 minutes
3. Continue transaction simulation
4. Complete 10 transactions
5. Inspect vents and components

**Expected Result:**
- All transactions complete successfully
- No error messages
- Cooling vents clear of debris
- Internal components dust-free

**Failure Modes:**
- Overheating (blocked vents)
- Fan failure (dust buildup)
- Component failure (dust ingress)

---

## 3. ESD Testing

### 3.1 What is ESD?

Electrostatic Discharge (ESD) occurs when a charged object comes into contact with the POS system. Common sources:
- Employees walking on carpet
- Customers touching the terminal
- Packing materials

### 3.2 ESD Testing Procedure

**Purpose:** Validate immunity to electrostatic discharge

**Test Case:** TC-ESD-001

**Preconditions:**
- TCx terminal powered on
- All peripherals connected

**Steps:**
1. Apply electrostatic discharge to terminal surface
2. Verify system operation
3. Run diagnostic suite

**Expected Result:**
- System remains operational
- No system resets or errors
- All diagnostics pass

**Failure Modes:**
- System reset
- Display flicker
- Peripheral disconnection

---

## 4. Environmental Test Case Templates

### 4.1 Fluid Resistance Template
Test Case ID: TC-FLUID-XXX
Title: Verify system operation after [fluid type] spill
Objective: Confirm system can withstand [fluid] exposure
Equipment: TCx terminal, [fluid] measured in [volume]
Preconditions: System powered on, peripherals connected

Steps:

Pour [volume]ml of [fluid] onto terminal surface

Wait [duration] seconds

Wipe terminal clean

Attempt standard transaction

Expected Result:

System remains operational

No error messages

Transaction completes successfully

Pass/Fail Criteria:
PASS: System operates normally after cleaning
FAIL: System fails to operate or displays errors


### 4.2 Temperature Template

Test Case ID: TC-TEMP-XXX
Title: Verify system functionality at [temperature]°C
Objective: Ensure system operates reliably at [temperature]
Equipment: TCx terminal, environmental chamber

Preconditions: System placed in environmental chamber

Steps:

Stabilize chamber at [temperature]°C

Power on the POS terminal

Run full diagnostic suite

Expected Result:

System powers on successfully

All diagnostic tests pass

Pass/Fail Criteria:
PASS: System passes all functional tests
FAIL: System fails to power on or pass diagnostics


---

## 5. Documenting Environmental Tests

### 5.1 Test Log Template
Date: [YYYY-MM-DD]
Tester: [Name]
TCx Model: [Model]
Serial Number: [Serial]
Test Type: [Temperature/Fluid/Dust/ESD]
Test Case ID: [ID]

Results:

Pass: [X]

Fail: [Y]

Comments: [Notes]

Evidence:

Photos: [Attach]

Logs: [Attach]

Defects Created:

[Defect ID]: [Description]

### 5.2 Defect Reporting

When environmental tests fail, create a hardware defect with:
- Environmental condition that caused failure
- Specific test case ID
- Detailed steps to reproduce
- Photos of damage/failure

---

## 6. Hands-On Exercises

### Exercise 1: Temperature Test Execution
1. Set up an environmental chamber
2. Run TC-TEMP-001 at -10°C
3. Document the results
4. Run TC-TEMP-001 at 40°C
5. Compare results

### Exercise 2: Fluid Spill Simulation
1. Simulate a coffee spill on a TCx terminal
2. Follow the fluid spill test procedure
3. Document the results
4. Identify any failure modes

### Exercise 3: ESD Test Execution
1. Set up ESD testing equipment
2. Apply electrostatic discharge to the terminal
3. Verify system operation
4. Document results

---

## 7. Resources

- [Environmental Test Case Templates](../test-management/test-cases/environmental/)
- [Toshiba Diagnostics Setup Guide](../docs/environment-setup/toshiba_diagnostics_setup.md)
- TCx Hardware Troubleshooting (Module 2)
- PCI DSS for POS (Module 4)

---

## 8. Knowledge Check

1. What temperatures should POS systems be tested at?
2. What fluids should be used in spill testing?
3. How do you test for dust accumulation?
4. What is ESD and why is it a risk?
5. How do you document an environmental test failure?