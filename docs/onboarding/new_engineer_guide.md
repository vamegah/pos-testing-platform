# docs/onboarding/new_engineer_guide.md
# New Engineer Onboarding Guide

**Version:** 1.0  
**Date:** 2026-07-08  
**Author:** POS Test Engineering Team

---

## 1. Introduction

Welcome to the POS Test Framework team! This guide will help you go from cloning the repository to running the full test suite locally in one pass.

**Estimated time:** 45 minutes

---

## 2. Prerequisites

Before you begin, ensure you have the following installed:

| Tool | Version | Installation | Verification |
|------|---------|--------------|--------------|
| **Git** | 2.30+ | `brew install git` (macOS) or `apt-get install git` (Linux) | `git --version` |
| **Java** | 11+ | [Adoptium](https://adoptium.net/) or OpenJDK | `java -version` |
| **Maven** | 3.8+ | `brew install maven` (macOS) or `apt-get install maven` (Linux) | `mvn -version` |
| **Docker** | 20.10+ | [Docker Desktop](https://www.docker.com/products/docker-desktop/) | `docker --version` |
| **Python** | 3.8+ | `brew install python3` (macOS) or `apt-get install python3` (Linux) | `python3 --version` |

### 2.1 Optional Tools

| Tool | Purpose | Installation |
|------|---------|--------------|
| **JMeter** | Performance testing | `brew install jmeter` (macOS) |
| **Chrome** | UI testing | [Chrome Download](https://www.google.com/chrome/) |
| **IntelliJ IDEA** | IDE | [IntelliJ Download](https://www.jetbrains.com/idea/download/) |

---

## 3. Clone and Build

### 3.1 Clone the Repository

```bash
git clone git@github.com:your-org/pos-test-framework.git
cd pos-test-framework

3.2 Build the Project
bash
# Build all modules
mvn clean install

# Expected output: BUILD SUCCESS
3.3 Verify the Build
bash
# Check that all modules compiled
ls -la framework-core/target/*.jar
ls -la product-tests/target/*.jar

# Should see .jar files in both directories
4. Start Services
4.1 Start Mock POS Services
bash
# Start all services via docker-compose
docker-compose up -d

# Verify services are running
docker-compose ps

# Should show all 4 services as "Up"
4.2 Verify Service Health
bash
# Check each service
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
curl http://localhost:8084/health

# Each should return {"status":"healthy"}
5. Run the Tests
5.1 Run API Tests
bash
cd test-automation
mvn test -Dtest=TransactionAPITest

# Expected: Tests pass (green)
5.2 Run Product E2E Tests
bash
cd product-tests
mvn test -Dtest=ProductE2EEngine

# Expected: All product E2E tests pass
5.3 Run All Tests
bash
# From the project root
mvn test

# Expected: All tests pass
6. Run UI Tests
6.1 Start UI Harness
bash
# Start the UI harness server
./scripts/serve_ui_harness.sh &

# Wait for server to start
sleep 3

# Verify it's running
curl http://localhost:8080
6.2 Run UI Tests
bash
cd test-automation
mvn test -Dtest=com.toshiba.pos.ui.* -DKIOSK_UI_URL=http://localhost:8080
6.3 Stop UI Harness
bash
# Kill the UI server
pkill -f "python3 -m http.server"
7. Run Performance Tests (Optional)
bash
# Ensure JMeter is installed
jmeter --version

# Run load test
jmeter -n -t perf-tests/load-test.jmx -l results/load-test.csv -JBASE_URL=http://localhost
8. Explore the Project
8.1 Key Directories
Directory	Purpose
framework-core/	Core framework code (adapters, engine, models)
product-tests/	Product-specific test implementations
simulators/	Mock services, product profiles, UI harness
test-automation/	API and UI test automation
ci-cd/	Jenkins pipelines
docs/	Documentation
8.2 Key Files
File	Purpose
pom.xml	Parent Maven POM
docker-compose.yml	Service orchestration
README.md	Project overview
CONTRIBUTING.md	Contribution guidelines
9. Next Steps
9.1 Development Setup
Import into IntelliJ:

File → Open → Select project root

Import as Maven project

Configure Run Configurations:

Create TestNG run configuration

Set working directory to project root

9.2 Common Tasks
Task	Command
Run all tests	mvn test
Run specific test	mvn test -Dtest=ClassName
Run specific group	mvn test -Dgroups=smoke
Build JAR	mvn package
Clean build	mvn clean install
9.3 Troubleshooting
Issue	Solution
Services not starting	Check Docker is running; run docker-compose logs
Tests failing	Check services are healthy; run docker-compose ps
UI tests failing	Ensure UI harness is running: curl http://localhost:8080
Maven build fails	Check Java version: java -version (must be 11+)
10. Getting Help
10.1 Resources
Resource	Location
Documentation	docs/ directory
ADRs	docs/architecture/adr/
Quality Bar	docs/framework/QUALITY_BAR.md
Reporting	docs/framework/REPORTING.md
Contributing	CONTRIBUTING.md
10.2 Contacts
Area	Contact
Framework	Framework Team
Product Tests	Product Team
CI/CD	DevOps Team
11. Verification Checklist
Git cloned successfully

Maven build passes (BUILD SUCCESS)

Docker services are running

All services are healthy (curl checks)

API tests pass

Product E2E tests pass

UI tests pass

Performance test runs

12. Revision History
Version	Date	Author	Changes
1.0	2026-07-08	POS Test Engineering Team	Initial creation
