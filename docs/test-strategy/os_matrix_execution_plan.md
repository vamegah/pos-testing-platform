# docs/test-strategy/os_matrix_execution_plan.md

# OS Matrix Execution Plan

**Version:** 1.0  
**Date:** 2026-01-15  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document describes how the same test-automation suite would be re-run against each OS target in a real lab environment. The OS matrix from `docs/environment-setup/supported_os_matrix.md` defines which product profiles target which OS families.

**Important:** Cross-OS execution requires real machines or VMs and is out of scope for local automated runs. This plan documents the approach for lab-based execution.

---

## 2. OS Targets

| OS Target | Description | Environment |
|-----------|-------------|-------------|
| **Windows IoT Enterprise LTSC** | Primary POS OS for TCx terminals | Physical hardware or VM |
| **Windows Desktop** | Development and test harness | Developer workstations |
| **Linux (Ubuntu-based)** | Cloud-native services | Containers/Cloud VMs |
| **Linux (Embedded)** | Edge devices | Physical hardware or VM |

---

## 3. Execution Strategy

### 3.1 Test Execution Flow
┌─────────────────────────────────────────────────────────────────────────────┐
│ CI/CD Pipeline (Jenkins) │
│ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Build Test Artifacts │ │
│ │ - Compile test automation (TestNG + RestAssured) │ │
│ │ - Package test runner container │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Run API Tests (Phase 3) on Each OS Target │ │
│ │ │ │
│ │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐ │ │
│ │ │ Windows │ │ Linux │ │ Linux │ │ Windows │ │ │
│ │ │ IoT LTSC │ │ (Ubuntu) │ │ (Embedded) │ │ Desktop │ │ │
│ │ └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Run E2E Tests (Phase 13) on Each OS Target │ │
│ │ │ │
│ │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────┐ │ │
│ │ │ Windows │ │ Linux │ │ Linux │ │ Windows │ │ │
│ │ │ IoT LTSC │ │ (Ubuntu) │ │ (Embedded) │ │ Desktop │ │ │
│ │ └─────────────┘ └─────────────┘ └─────────────┘ └──────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Aggregate Results & Report │ │
│ │ - Per-OS test results │ │
│ │ - Comparison across OS targets │ │
│ │ - Regression detection │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘


### 3.2 Per-OS Test Execution

| OS Target | Execution Method | Test Runner | Notes |
|-----------|------------------|-------------|-------|
| **Windows IoT Enterprise LTSC** | Physical hardware or VM | Maven on Windows | Requires TCx hardware for diagnostic tests |
| **Linux (Ubuntu-based)** | Container or VM | Docker test runner | Runs in container with maven |
| **Linux (Embedded)** | Physical hardware or VM | Maven on Linux | Limited resources, run only API tests |
| **Windows Desktop** | Developer workstation | Maven on Windows | Full test suite for development |

---

## 4. CI/CD Matrix Integration

### 4.1 Jenkins Pipeline Matrix (Placeholder)

The following Jenkins pipeline stanza is a **template** for multi-OS execution. It is commented out and would require real OS agents to be configured.

```groovy
/*
 * OS Matrix Execution Stub
 * 
 * To enable multi-OS testing, uncomment and configure with actual OS agents:
 *   - windows-iot: Windows IoT Enterprise LTSC agent
 *   - linux-ubuntu: Ubuntu-based cloud agent
 *   - linux-embedded: Embedded Linux agent
 *   - windows-desktop: Windows Desktop agent
 * 
 * Requirements:
 *   - Each agent must have Java 11+, Maven, and access to test artifacts
 *   - Windows IoT agents require physical hardware for diagnostic tests
 *   - Network access to POS services under test
 */
/*
pipeline {
    agent none
    
    stages {
        stage('Build') {
            agent any
            steps {
                // Build test artifacts
                dir('test-automation') {
                    sh 'mvn clean compile package -DskipTests'
                }
            }
        }
        
        stage('Test Across OS Matrix') {
            parallel {
                stage('Windows IoT Enterprise LTSC') {
                    agent { label 'windows-iot' }
                    steps {
                        // Run tests on Windows IoT
                        dir('test-automation') {
                            bat 'mvn test -Dgroups=api,diagnostic'
                        }
                    }
                }
                stage('Linux (Ubuntu-based)') {
                    agent { label 'linux-ubuntu' }
                    steps {
                        dir('test-automation') {
                            sh 'mvn test -Dgroups=api,e2e'
                        }
                    }
                }
                stage('Linux (Embedded)') {
                    agent { label 'linux-embedded' }
                    steps {
                        dir('test-automation') {
                            sh 'mvn test -Dgroups=api -Dskip.performance=true'
                        }
                    }
                }
                stage('Windows Desktop') {
                    agent { label 'windows-desktop' }
                    steps {
                        dir('test-automation') {
                            bat 'mvn test'
                        }
                    }
                }
            }
        }
        
        stage('Publish Results') {
            agent any
            steps {
                // Aggregate and publish test results
                publishHTML([
                    reportDir: 'reports',
                    reportFiles: 'index.html',
                    reportName: 'OS Matrix Test Report'
                ])
            }
        }
    }
}
*/

4.2 GitHub Actions Matrix (Alternative)
# .github/workflows/os-matrix.yml (placeholder)
# This is a stub for GitHub Actions multi-OS testing

name: OS Matrix Tests

on:
  workflow_dispatch:
  push:
    branches: [ main ]

jobs:
  test:
    name: Test on ${{ matrix.os }}
    runs-on: ${{ matrix.runner }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: windows-iot
            runner: windows-latest  # Requires custom runner for IoT
            test_groups: api,diagnostic
          - os: ubuntu
            runner: ubuntu-latest
            test_groups: api,e2e
          - os: embedded-linux
            runner: ubuntu-latest  # Custom runner for embedded
            test_groups: api
          - os: windows-desktop
            runner: windows-latest
            test_groups: api,diagnostic,e2e
    steps:
      - uses: actions/checkout@v3
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
      - name: Run Tests
        run: |
          cd test-automation
          mvn test -Dgroups=${{ matrix.test_groups }}
      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: test-results-${{ matrix.os }}
          path: test-automation/target/surefire-reports/

5. Lab Execution Process
5.1 Pre-Execution Checklist
All OS targets are provisioned and accessible

Java 11+ and Maven installed on each target

POS services under test are deployed and accessible

Network connectivity between test runner and services

Test data (test cards, test SKUs) prepared

Hardware (if physical) is connected and powered on

5.2 Execution Steps
Build test artifacts on a build node

Distribute artifacts to each OS target

Run tests on each OS target (parallel or sequential)

Collect results from each target

Aggregate and analyze results

Generate report comparing OS targets

5.3 Post-Execution
Review per-OS test results

Investigate OS-specific failures

Update test cases for OS-specific issues

Document OS-specific behavior

6. Expected Coverage by OS
Test Category	Windows IoT LTSC	Linux (Ubuntu)	Linux (Embedded)	Windows Desktop
API Tests (Phase 3)	✅	✅	✅	✅
E2E Tests (Phase 13)	✅	✅	⚠️	✅
Diagnostic Tests	✅	❌	❌	✅
Hardware Emulation	⚠️	✅	⚠️	⚠️
Performance Tests	⚠️	✅	❌	⚠️
Legend:

✅ = Full support

⚠️ = Limited support (may require additional setup)

❌ = Not supported

7. OS-Specific Considerations
7.1 Windows IoT Enterprise LTSC
Challenges: Physical hardware availability, driver compatibility

Solutions: Use VMs where possible, maintain dedicated test lab

Diagnostic Tests: Requires Toshiba Diagnostics USB key

7.2 Linux (Ubuntu-based)
Challenges: None; primary development target

Solutions: Containerized execution via Docker test runner

Performance Tests: Full support

7.3 Linux (Embedded)
Challenges: Limited resources, slower execution

Solutions: Run only API tests (skip performance)

Test Execution: Maven on device or cross-compile

7.4 Windows Desktop
Challenges: Environment differences from IoT

Solutions: Use as development reference

Test Execution: Full test suite for validation

8. Revision History
Version	Date	Author	Changes
1.0	2026-01-15	POS Test Engineering Team	Initial creation
