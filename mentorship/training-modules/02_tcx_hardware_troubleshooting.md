# mentorship/training-modules/02_tcx_hardware_troubleshooting.md

# Training Module 02: TCx Hardware Troubleshooting

## Overview

This module covers common hardware issues with Toshiba TCx series POS terminals (TCx 810, TCx 700, TCx 300) and their troubleshooting procedures.

**Duration:** 60 minutes  
**Prerequisites:** Module 01 (Toshiba Diagnostics)  
**Learning Objectives:**
- Identify common TCx hardware issues
- Follow systematic troubleshooting procedures
- Use Toshiba Diagnostics for hardware validation
- Document hardware defects correctly

---

## 1. TCx Series Overview

| Model | Form Factor | Display Options | Key Features |
|-------|-------------|-----------------|--------------|
| **TCx 810** | All-in-one | 15.6" touch | Latest generation, powerful CPU |
| **TCx 700** | Modular | 15.6" touch | Flexible configuration |
| **TCx 300** | Compact | 12.1" touch | Small footprint |
| **SurePOS 700** | Traditional | 15" 4:3 | Legacy reliability |

---

## 2. Common Hardware Issues

### 2.1 Power Issues

| Symptom | Possible Causes | Troubleshooting Steps |
|---------|-----------------|----------------------|
| **No power** | Power cable, outlet, PSU | 1. Check outlet has power<br>2. Verify power cable connection<br>3. Test with known-good PSU<br>4. Check internal power connections |
| **Intermittent power** | Loose connections, PSU failing | 1. Check all connections<br>2. Replace PSU<br>3. Monitor with Diagnostics |
| **Unit reboots randomly** | Overheating, PSU issues | 1. Check cooling vents for dust<br>2. Monitor temperature in Diagnostics<br>3. Test with replacement PSU |

### 2.2 Display Issues

| Symptom | Possible Causes | Troubleshooting Steps |
|---------|-----------------|----------------------|
| **No display** | Cable, display panel, GPU | 1. Check display cable connections<br>2. Test with external monitor<br>3. Run display test in Diagnostics |
| **Touch not working** | Calibration, driver, hardware | 1. Run touch calibration<br>2. Check driver status<br>3. Test touch in Diagnostics |
| **Screen flickering** | Cable, refresh rate, GPU | 1. Check cable connections<br>2. Adjust refresh rate<br>3. Test display in Diagnostics |
| **Dead pixels** | Hardware defect | 1. Document in Diagnostics<br>2. Report for warranty replacement |

### 2.3 Peripheral Issues

| Symptom | Possible Causes | Troubleshooting Steps |
|---------|-----------------|----------------------|
| **Printer not printing** | Paper, cable, driver, hardware | 1. Check paper loading<br>2. Verify cable connection<br>3. Run printer test in Diagnostics |
| **Scanner not scanning** | Cable, driver, hardware | 1. Check cable connection<br>2. Run scanner test in Diagnostics<br>3. Test with known-good scanner |
| **PIN Pad not responding** | Cable, power, driver | 1. Check power and connections<br>2. Run PIN Pad test in Diagnostics<br>3. Test with known-good PIN Pad |

---

## 3. Systematic Troubleshooting Approach

### 3.1 The "Isolation" Method

1. **Isolate the problem** – Is it hardware or software?
   - Run Toshiba Diagnostics to test hardware
   - If Diagnostics passes → likely software issue
   - If Diagnostics fails → hardware issue

2. **Isolate the component** – Which component is failing?
   - Disconnect all peripherals
   - Test each peripheral individually
   - Identify the failing component

3. **Isolate the cause** – Why is it failing?
   - Check cables and connections
   - Verify configuration
   - Look for physical damage

### 3.2 Diagnostic Flowchart
┌──────────────────────┐
│ System not working │
└──────────┬───────────┘
│
▼
┌──────────────────────┐
│ Boot Diagnostics │
│ from USB key │
└──────────┬───────────┘
│
▼
┌──────────────────────┐
│ Diagnostics runs? │
└──────────┬───────────┘
│
┌──────┴──────┐
│ │
▼ ▼
┌──────────┐ ┌──────────────────┐
│ YES │ │ NO │
│ Hardware │ │ Hardware │
│ Issue │ │ Failure (PCB, │
│ │ │ CPU, Memory) │
└────┬─────┘ └────────┬─────────┘
│ │
▼ ▼
┌──────────┐ ┌──────────────────┐
│ Identify │ │ Escalate to │
│ Failing │ │ Advanced HW │
│Component │ │ Support │
└────┬─────┘ └──────────────────┘
│
▼
┌──────────────────────┐
│ Replace/Repair │
│ Component │
└──────────────────────┘


---

## 4. Using Diagnostics for Hardware Validation

### 4.1 CPU Test

**Purpose:** Validate CPU functionality

**Steps:**
1. Navigate to `Diagnostics` → `CPU Test`
2. Select test type (Basic/Advanced)
3. Run the test
4. Check results: Pass/Fail

**Expected:** All tests pass

### 4.2 Memory Test

**Purpose:** Validate RAM functionality

**Steps:**
1. Navigate to `Diagnostics` → `Memory Test`
2. Select test pattern (Basic/Advanced)
3. Run the test
4. Check results: Pass/Fail

**Expected:** All tests pass

### 4.3 Storage Test

**Purpose:** Validate storage (SSD/HDD) functionality

**Steps:**
1. Navigate to `Diagnostics` → `Storage Test`
2. Select test type (Read/Write)
3. Run the test
4. Check results: Pass/Fail

**Expected:** All tests pass

---

## 5. Hardware Defect Documentation

### 5.1 Defect Report Template

**Defect ID:** [Auto-generated]  
**TCx Model:** [Enter model]  
**Serial Number:** [Enter serial]  
**Firmware Version:** [Enter version]  
**Peripheral Involved:** [If applicable]  
**Diagnostic Error Code:** [From Diagnostics]  
**Steps to Reproduce:** [Clear steps]  
**Actual Result:** [What happened]  
**Expected Result:** [What should have happened]  
**Log Files:** [Attach]  
**Photos:** [Attach]

### 5.2 Severity Classification

| Severity | Definition |
|----------|------------|
| **Blocker** | System cannot be used; requires immediate fix |
| **Critical** | Core functionality broken; workaround exists |
| **Major** | Non-core functionality broken |
| **Minor** | Cosmetic issue; no functional impact |

---

## 6. Hands-On Exercises

### Exercise 1: Power Issue Diagnosis
1. Simulate a power issue (unplug power cable)
2. Use the isolation method to diagnose
3. Identify the root cause
4. Document the defect

### Exercise 2: Peripheral Diagnosis
1. Connect a faulty peripheral (simulated)
2. Run Diagnostics to isolate the issue
3. Identify the failing component
4. Document the defect

### Exercise 3: Diagnostic Test Run
1. Boot into Toshiba Diagnostics
2. Run CPU, Memory, and Storage tests
3. Document the results
4. Interpret any failures

---

## 7. Resources

- Toshiba Diagnostics Setup Guide (Module 1)
- [TCx Hardware Manuals](internal link)
- [RS-232 Configuration Guide](../docs/environment-setup/rs232_configuration_guide.md)
- Retail-Hardened Testing (Module 3)

---

## 8. Knowledge Check

1. What are the first three steps in the isolation method?
2. How do you test if a display issue is hardware or software?
3. What information should be included in a hardware defect report?
4. What is the difference between Blocker and Critical severity?
5. How do you test RS-232 communication in Diagnostics?