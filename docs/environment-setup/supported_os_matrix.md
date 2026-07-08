# docs/environment-setup/supported_os_matrix.md

# Supported Operating Systems Matrix

**Version:** 1.0  
**Date:** 2026-01-15  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document defines the supported operating system targets for each product profile in the POS Cloud-Native & Hardware Test Platform. The matrix captures which profiles target Windows IoT Enterprise (LTSC channel) versus Linux-based targets for cloud/ELERA services.

> **Note:** Specific build numbers and version details are intentionally omitted. This matrix expresses capabilities generically — actual version support should be verified against the latest product documentation.

---

## 2. OS Families

| OS Family | Description | Typical Targets |
|-----------|-------------|-----------------|
| **Windows IoT Enterprise LTSC** | Long-term servicing channel for embedded POS devices | TCx terminals, SurePOS, self-checkout systems |
| **Windows Desktop** | Full Windows desktop for development | Developer workstations, test harnesses |
| **Linux (Ubuntu-based)** | Cloud-native services | ELERA platform, microservices |
| **Linux (Embedded)** | Lightweight embedded Linux | Edge devices, some self-checkout variants |

---

## 3. Product Profile OS Matrix

| Product Profile | Primary OS | Secondary OS | Notes |
|-----------------|------------|--------------|-------|
| **ELERA®** | Linux (Ubuntu-based) | N/A | Cloud-native unified-commerce platform |
| **MxP™ Vision Kiosk** | Windows IoT Enterprise LTSC | Linux (Embedded) | Vision kiosk; optional embedded variant |
| **MxP™ SMART \| hybrid** | Windows IoT Enterprise LTSC | Linux (Embedded) | Pedestal/kiosk; embedded variant supported |
| **MxP™ SMART \| wall** | Windows IoT Enterprise LTSC | N/A | Space-constrained wall mount; Windows only |
| **MxP™ SMART \| wing** | Windows IoT Enterprise LTSC | N/A | Modular lane; Windows only |
| **Self Checkout System 7** | Windows IoT Enterprise LTSC | Linux (Embedded) | Full-basket self-checkout; embedded variant available |
| **TCx® Display** | Windows IoT Enterprise LTSC | Linux (Embedded) | Display matrix; multiple OS options |
| **TCx® Single Station Printer** | Windows IoT Enterprise LTSC | Linux (Ubuntu-based) | Printing peripheral; cloud-compatible |
| **TCx® Dual Station Printer** | Windows IoT Enterprise LTSC | Linux (Ubuntu-based) | Printing peripheral; cloud-compatible |

---

## 4. Service Compatibility

| Service | Windows IoT Enterprise LTSC | Linux (Ubuntu-based) | Linux (Embedded) |
|---------|-----------------------------|---------------------|------------------|
| **pricing-service** | ✅ | ✅ | ✅ |
| **promotions-service** | ✅ | ✅ | ✅ |
| **tax-service** | ✅ | ✅ | ✅ |
| **payment-gateway** | ✅ | ✅ | ✅ |
| **ELERA platform** | ❌ | ✅ | ❌ |
| **Hardware Emulation (socat)** | ⚠️ (requires WSL) | ✅ | ⚠️ |
| **Toshiba Diagnostics** | ✅ (via USB key) | ❌ | ❌ |

---

## 5. Test Execution Matrix

| Test Type | Windows IoT Enterprise LTSC | Linux (Ubuntu-based) | Linux (Embedded) |
|-----------|-----------------------------|---------------------|------------------|
| **API Tests** | ✅ (via containerized runner) | ✅ | ✅ |
| **Performance Tests** | ⚠️ (limited) | ✅ | ❌ |
| **Diagnostic Tests** | ✅ (native) | ❌ | ❌ |
| **Hardware Emulation** | ⚠️ (via WSL/Docker) | ✅ | ⚠️ |
| **RS-232 Virtual Port** | ⚠️ (requires WSL) | ✅ | ✅ (if serial support) |
| **Offline Sync Tests** | ✅ | ✅ | ⚠️ |

---

## 6. Development Environment Matrix

| Development Activity | Windows | macOS | Linux |
|----------------------|---------|-------|-------|
| **Local Service Development** | ✅ (Docker Desktop) | ✅ (Docker Desktop) | ✅ |
| **Kubernetes Manifests** | ✅ (minikube/kind) | ✅ (minikube/kind) | ✅ |
| **JMeter Performance Tests** | ✅ | ✅ | ✅ |
| **Python Simulators** | ✅ | ✅ | ✅ |
| **Terraform IaC** | ✅ | ✅ | ✅ |
| **TestNG Automation** | ✅ | ✅ | ✅ |

---

## 7. CI/CD Environment

| Component | OS Target | Notes |
|-----------|-----------|-------|
| **Jenkins Master** | Linux (Ubuntu) | Jenkins controller |
| **Jenkins Agents** | Linux (Ubuntu) | Build and test execution |
| **Container Runtime** | Linux | Docker-in-Docker for test containers |
| **Kubernetes Nodes** | Linux (Ubuntu) | EKS/AKS worker nodes |
| **Test Runner Container** | Linux (Alpine) | ci-cd/Dockerfile.test |

---

## 8. Recommendations

### 8.1 For Development
- **Primary**: Linux (Ubuntu-based) or macOS for cloud service development
- **Windows**: Required for physical hardware testing (TCx, SurePOS)
- **Containerized**: All services can run in Docker on any platform

### 8.2 For Testing
- **API/Integration**: Run in containerized environment (any host)
- **Diagnostics**: Requires Windows machine with hardware access
- **Performance**: Run on Linux-based load generators (EC2/AKS)

### 8.3 For Production
- **Cloud Services**: Linux (Ubuntu-based) on EKS/AKS
- **POS Edge**: Windows IoT Enterprise LTSC for TCx terminals
- **ELERA Platform**: Linux-based targets only

---

## 9. Verification Notes

| Profile | Verified OS | Verification Method |
|---------|-------------|---------------------|
| ELERA® | Linux (Ubuntu 22.04) | Manual deployment |
| MxP™ Vision Kiosk | Windows IoT Enterprise LTSC | Lab validation |
| MxP™ SMART \| wall | Windows IoT Enterprise LTSC | Simulator testing |
| Self Checkout System 7 | Windows IoT Enterprise LTSC | Simulator testing |

---

## 10. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-15 | POS Test Engineering Team | Initial creation |