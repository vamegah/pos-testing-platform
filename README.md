# POS Cloud-Native & Hardware Test Platform

## ⚠️ DISCLAIMER

This is an **independent portfolio/interview-prep project**, inspired by publicly posted job
requirements and industry patterns for retail POS testing. It is **not official Toshiba
software** and must not claim Toshiba/TGCS affiliation, use Toshiba logos, or present output as
produced by Toshiba.

Product names (TCx810, TCx700, SurePOS, ELERA, etc.) are used only as **compatibility labels**
in comments/docs to show domain knowledge — never hard-code them into anything that looks like
a redistribution of Toshiba's proprietary diagnostic software or firmware.

**No real payment data, ever.** Payment gateway = fully mocked, clearly labeled `mock_`, returns
canned approve/decline responses. No PANs, no real tokenization, no PCI-scope code.

**No real target endpoints.** All services run on `localhost` or `mock-pos-services` / an env
var you set.

---

## Project Purpose

This project demonstrates a comprehensive test engineering platform for retail POS systems,
covering:

- **Cloud-native POS testing** (microservices, Kubernetes, performance/load testing)
- **Hardware emulation** (RS-232 peripherals, USB-IP, virtual COM ports)
- **Test automation** (API layer validation with RestAssured + TestNG)
- **Diagnostic validation** (simulated VPD, iButton, RS-232 configuration)
- **Swarm-style load orchestration** (coordinator + worker pool pattern)
- **CI/CD integration** (Jenkins pipeline with automated test gates)
- **Monitoring & dashboards** (Grafana, Prometheus, Datadog configs)
- **Infrastructure as Code** (Terraform for AWS/Azure — generated, not executed)
- **Test management artifacts** (test cases, traceability, defect templates)
- **Mentorship materials** (training modules for POS testing)

---

## Quick Start (Local Development)

### Prerequisites

- **Docker & Docker Compose** (for local service mocks)
- **Java 11+** (for test automation)
- **Maven 3.8+** (for building tests)
- **kubectl** (optional — for K8s manifest validation)
- **JMeter** (optional — for performance test scripts)

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/pos-cloud-test-platform.git
cd pos-cloud-test-platform