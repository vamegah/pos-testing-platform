# mentorship/training-modules/04_pci_dss_for_pos.md

# Training Module 04: PCI DSS for POS

## Overview

This module covers Payment Card Industry Data Security Standard (PCI DSS) requirements relevant to POS testing, including security best practices and compliance validation.

**Duration:** 45 minutes  
**Prerequisites:** Module 01, Module 02, Module 03  
**Learning Objectives:**
- Understand PCI DSS requirements for POS
- Identify security risks in POS testing
- Follow secure testing practices
- Validate mock payment implementations

---

## 1. What is PCI DSS?

The Payment Card Industry Data Security Standard (PCI DSS) is a set of security standards designed to ensure that all companies that accept, process, store, or transmit credit card information maintain a secure environment.

### 1.1 Core Requirements

| Requirement | Description |
|-------------|-------------|
| **1** | Install and maintain network security controls |
| **2** | Do not use vendor-supplied defaults for system passwords |
| **3** | Protect stored cardholder data |
| **4** | Encrypt transmission of cardholder data |
| **5** | Protect all systems against malware |
| **6** | Develop and maintain secure systems |
| **7** | Restrict access to cardholder data |
| **8** | Identify and authenticate access to system components |
| **9** | Restrict physical access to cardholder data |
| **10** | Log and monitor all access to system components |
| **11** | Test security systems and processes regularly |
| **12** | Support information security with policies |

### 1.2 Relevance to POS Testing

| Activity | PCI Relevance |
|----------|---------------|
| Payment gateway testing | High – validates secure transactions |
| Mock payment implementation | High – must not expose real data |
| Card data storage | Critical – never store real card data |
| Logging | Medium – must not log sensitive data |
| Environment setup | High – must be isolated and secure |

---

## 2. Security Best Practices for POS Testing

### 2.1 NEVER Use Real Payment Data

| DO | DON'T |
|----|-------|
| Use test card numbers (e.g., 4111111111111111) | Use real credit card numbers |
| Use mock payment gateways | Connect to production payment gateways |
| Use test merchant IDs | Use production merchant IDs |
| Use test tokens | Use real tokens |

### 2.2 Test Card Sentinel Values

Our mock payment gateway uses sentinel values:

| Card Number | Result |
|-------------|--------|
| 4111111111111111 | Approved |
| 4111111111110000 | Declined |

### 2.3 Data Masking

**Always mask sensitive data in logs:**
DO: "Payment authorized for card ending 1111"
DON'T: "Payment authorized for card 4111111111111111"


### 2.4 Environment Isolation

| Environment | Purpose | PCI Status |
|-------------|---------|------------|
| **Local** | Development | No real data |
| **Test** | Integration | No real data |
| **Staging** | Validation | No real data |
| **Production** | Live | PCI compliant |

---

## 3. Mock Payment Gateway Implementation

### 3.1 Design Principles

1. **No real PANs** – Use test card numbers only
2. **No real tokenization** – Use mock tokens
3. **No real merchant data** – Use test merchant IDs
4. **Clear labeling** – Mark all components as "mock"
5. **Logging restrictions** – Never log PANs, CVVs, or PINs

### 3.2 Mock Response Codes

| Scenario | Response |
|----------|----------|
| Approved | 200 OK with auth_code |
| Declined | 402 Payment Required with reason |
| Error | 500 Internal Server Error |

### 3.3 Security Review Checklist

- [ ] All payment data is mocked
- [ ] No real PANs used in tests
- [ ] No real card data in logs
- [ ] Mock components are clearly labeled
- [ ] Test environment is isolated
- [ ] No real merchant IDs used
- [ ] Mock tokens expire quickly

---

## 4. Testing Security Controls

### 4.1 Authentication Testing

**Purpose:** Validate that only authorized users can access diagnostics

**Test:** iButton Authentication (TC_IBUTTON_001)

**Validation:**
- Valid iButton → Access granted
- Invalid iButton → Access denied
- No iButton → Authentication required

### 4.2 Encryption Validation

**Purpose:** Validate that sensitive data is encrypted

**Test:** Service communication encryption

**Validation:**
- HTTPS endpoints for all services
- mTLS for service-to-service communication
- No plaintext sensitive data

### 4.3 Logging Validation

**Purpose:** Validate that sensitive data is not logged

**Test:** Log review for sensitive data

**Validation:**
- No PANs in logs
- No CVVs in logs
- No PINs in logs
- No full card numbers in logs

---

## 5. Incident Response

### 5.1 If Real Data is Exposed

1. **Immediately** stop testing
2. **Isolate** the affected environment
3. **Notify** security team
4. **Preserve** logs and evidence
5. **Follow** incident response procedure

### 5.2 Reporting

Report any security incidents to:
- Security Team: [security@company.com]
- Manager: [manager@company.com]
- PCI Compliance Officer: [pci@company.com]

---

## 6. Hands-On Exercises

### Exercise 1: Test Card Validation
1. Use the mock payment gateway
2. Submit test card 4111111111111111
3. Verify approval
4. Submit test card 4111111111110000
5. Verify decline

### Exercise 2: Log Review
1. Run a transaction test
2. Review the logs
3. Verify no sensitive data is logged
4. Identify any potential data exposure

### Exercise 3: Security Control Validation
1. Test iButton authentication
2. Verify HTTPS endpoints
3. Check for sensitive data in logs
4. Document any security gaps

---

## 7. Resources

- [PCI DSS Documentation](https://www.pcisecuritystandards.org/)
- [Mock Payment Gateway Implementation](../simulators/pos-services/payment/app.py)
- [iButton Authentication Test Case](../test-management/test-cases/diagnostic/TC_IBUTTON_001.md)
- Toshiba Diagnostics Setup Guide (Module 1)

---

## 8. Knowledge Check

1. What are the PCI DSS requirements?
2. Why should you never use real payment data in testing?
3. What are the test card sentinel values?
4. How do you validate that logs don't contain sensitive data?
5. What is the incident response procedure if real data is exposed?