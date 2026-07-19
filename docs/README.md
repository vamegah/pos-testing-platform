# docs/README.md
# Documentation Index

**Version:** 1.0  
**Date:** 2026-07-14

Welcome to the POS Cloud-Native & Hardware Test Platform documentation. This index provides a single entry point to all documentation in the repository.

---

## Quick Navigation

| Section | Description |
|---------|-------------|
| [Project Overview](#project-overview) | What this project is about |
| [Getting Started](#getting-started) | New engineer onboarding |
| [Test Strategy](#test-strategy) | Testing approach and plans |
| [Architecture](#architecture) | Architecture decisions and traceability |
| [Framework](#framework) | Framework documentation |
| [Environment Setup](#environment-setup) | Setup guides |
| [Traceability](#traceability) | Requirements traceability |
| [Mentorship](#mentorship) | Training modules |

---

## Project Overview

| File | Description |
|------|-------------|
| [`../README.md`](../README.md) | Project overview, disclaimer, quick start |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | Contribution guidelines |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Version history |

---

## Getting Started

| File | Description |
|------|-------------|
| [`onboarding/new_engineer_guide.md`](onboarding/new_engineer_guide.md) | From clone to passing local run in one pass |

---

## Test Strategy

| File | Description |
|------|-------------|
| [`test-strategy/cloud_test_plan_v1.0.md`](test-strategy/cloud_test_plan_v1.0.md) | Cloud test strategy and plan |
| [`test-strategy/risk_mitigation_log.md`](test-strategy/risk_mitigation_log.md) | Risk register and mitigation plans |
| [`test-strategy/os_matrix_execution_plan.md`](test-strategy/os_matrix_execution_plan.md) | Cross-OS test execution plan |

---

## Architecture

| File | Description |
|------|-------------|
| [`architecture/reference_architecture_traceability.md`](architecture/reference_architecture_traceability.md) | All nodes and arrows mapped to tests |
| [`architecture/adr/0001-test-framework-choice.md`](architecture/adr/0001-test-framework-choice.md) | ADR: TestNG + RestAssured + Selenium |
| [`architecture/adr/0002-mock-first-hardware-strategy.md`](architecture/adr/0002-mock-first-hardware-strategy.md) | ADR: Mock-first hardware approach |
| [`architecture/adr/0003-product-adapter-pattern.md`](architecture/adr/0003-product-adapter-pattern.md) | ADR: Product-Adapter pattern |

---

## Framework

| File | Description |
|------|-------------|
| [`framework/QUALITY_BAR.md`](framework/QUALITY_BAR.md) | Quality standards and gates |
| [`framework/REPORTING.md`](framework/REPORTING.md) | Test reporting and trend analysis |
| [`framework/RELEASE_PROCESS.md`](framework/RELEASE_PROCESS.md) | Release guidelines |
| [`framework/test_authoring_guide.md`](framework/test_authoring_guide.md) | Gherkin style, tags, naming conventions |
| [`framework/DEPENDENCY_MANAGEMENT.md`](framework/DEPENDENCY_MANAGEMENT.md) | Dependency scanning and updates |
| [`framework/offline_fault_injection_strategy.md`](framework/offline_fault_injection_strategy.md) | Offline/network-fault testing strategy |

---

## Environment Setup

| File | Description |
|------|-------------|
| [`environment-setup/toshiba_diagnostics_setup.md`](environment-setup/toshiba_diagnostics_setup.md) | Toshiba Diagnostics USB key guide |
| [`environment-setup/rs232_configuration_guide.md`](environment-setup/rs232_configuration_guide.md) | RS-232 configuration guide |
| [`environment-setup/supported_os_matrix.md`](environment-setup/supported_os_matrix.md) | Supported OS targets per product |

---

## Traceability

| File | Description |
|------|-------------|
| [`../test-management/traceability/requirements_traceability_matrix.csv`](../test-management/traceability/requirements_traceability_matrix.csv) | Requirement ↔ test case ↔ automation status |

---

## Mentorship

| File | Description |
|------|-------------|
| [`../mentorship/training-modules/01_toshiba_diagnostics.md`](../mentorship/training-modules/01_toshiba_diagnostics.md) | Toshiba Diagnostics training |
| [`../mentorship/training-modules/02_tcx_hardware_troubleshooting.md`](../mentorship/training-modules/02_tcx_hardware_troubleshooting.md) | TCx hardware troubleshooting |
| [`../mentorship/training-modules/03_retail_hardened_testing.md`](../mentorship/training-modules/03_retail_hardened_testing.md) | Retail-hardened testing |
| [`../mentorship/training-modules/04_pci_dss_for_pos.md`](../mentorship/training-modules/04_pci_dss_for_pos.md) | PCI DSS for POS |

---

## Adoption Readiness

| File | Description |
|------|-------------|
| [`../ADOPTION_GAP_ANALYSIS.md`](../ADOPTION_GAP_ANALYSIS.md) | Mock vs production replacement table |
| [`../PRODUCTION_READINESS_CHECKLIST.md`](../PRODUCTION_READINESS_CHECKLIST.md) | Steps for production readiness |

---

## Document Map
docs/
├── README.md # ← You are here
├── ADOPTION_GAP_ANALYSIS.md
├── PRODUCTION_READINESS_CHECKLIST.md
├── architecture/
│ ├── reference_architecture_traceability.md
│ └── adr/
│ ├── 0001-test-framework-choice.md
│ ├── 0002-mock-first-hardware-strategy.md
│ └── 0003-product-adapter-pattern.md
├── environment-setup/
│ ├── toshiba_diagnostics_setup.md
│ ├── rs232_configuration_guide.md
│ └── supported_os_matrix.md
├── framework/
│ ├── QUALITY_BAR.md
│ ├── REPORTING.md
│ ├── RELEASE_PROCESS.md
│ ├── test_authoring_guide.md
│ ├── DEPENDENCY_MANAGEMENT.md
│ └── offline_fault_injection_strategy.md
├── onboarding/
│ └── new_engineer_guide.md
└── test-strategy/
├── cloud_test_plan_v1.0.md
├── risk_mitigation_log.md
└── os_matrix_execution_plan.md


---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-14 | POS Test Engineering Team | Initial creation |