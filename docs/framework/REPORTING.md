# docs/framework/REPORTING.md
# Test Reporting and Trend Analysis

**Version:** 1.0  
**Date:** 2026-07-08  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document describes the test reporting and historical trend analysis capabilities of the POS Test Framework.

---

## 2. Reporting Architecture
┌─────────────────────────────────────────────────────────────────────────────┐
│ Test Execution (Jenkins) │
│ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Surefire Test Reports (XML) │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ JSON Trend Data (history/YYYY-MM-DD.json) │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Trend Report (HTML + SVG Chart) │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘


---

## 3. Report Types

### 3.1 Test Execution Reports

| Report Type | Format | Description |
|-------------|--------|-------------|
| **API Tests** | HTML | Results of API test execution |
| **E2E Tests** | HTML | Results of product E2E tests |
| **UI Tests** | HTML | Results of UI test execution |
| **Performance** | HTML | JMeter performance test results |
| **Flaky Tests** | HTML | Quarantined flaky test results |

### 3.2 Trend Reports

| Report Type | Format | Description |
|-------------|--------|-------------|
| **Pass/Fail Trend** | HTML + JSON | Historical pass/fail rates over time |
| **Duration Trend** | HTML + JSON | Test execution duration over time |
| **Flaky Trend** | HTML + JSON | Flaky test failure rates over time |

---

## 4. Trend Data Format

### 4.1 JSON Schema

```json
{
  "timestamp": "2026-07-08_12-00-00",
  "build_number": "123",
  "tests": {},
  "summary": {
    "total": 100,
    "passed": 95,
    "failed": 3,
    "skipped": 2
  }
}

4.2 Storage Location
Environment	Path
Jenkins	test-results/trends/history/YYYY-MM-DD.json
Local	target/trends/history/YYYY-MM-DD.json
5. Accessing Reports
5.1 In Jenkins
Open the Jenkins job

Click on "Test Report" in the sidebar

Navigate to the "Trends" section

5.2 Locally
bash
# Run tests and generate reports
mvn test

# View trend report
open target/trends/trend-report.html
6. Report Configuration
6.1 Environment Variables
Variable	Description	Default
TREND_ENABLED	Enable trend data collection	true
TREND_HISTORY_PATH	Path to history directory	test-results/trends/history
TREND_REPORT_PATH	Path to report output	test-results/trends/trend-report.html
6.2 Maven Properties
xml
<properties>
    <trend.enabled>true</trend.enabled>
    <trend.history.path>${project.build.directory}/trends/history</trend.history.path>
    <trend.report.path>${project.build.directory}/trends/trend-report.html</trend.report.path>
</properties>
7. Adding New Trend Views
7.1 Custom Trend Metrics
To add a custom trend metric:

Extend the trend data JSON

Update the trend report HTML

Add the metric to the chart

7.2 External Tools
Tool	Purpose	Integration
Allure	Advanced reporting	allure-maven-plugin
Cucumber Reports	BDD reporting	cucumber-reports plugin
Grafana	Dashboards	Import JSON data
8. Maintenance
8.1 Retention Policy
Data Type	Retention
Trend JSON	90 days
Test Reports	30 days
Artifacts	14 days
8.2 Cleanup
bash
# Clean up old trend data (keep 90 days)
find test-results/trends/history -name "*.json" -mtime +90 -delete
9. Troubleshooting
Issue	Solution
Trend data not showing	Check TREND_ENABLED is true
JSON parsing errors	Validate JSON format in history files
Missing trend report	Ensure test execution completed successfully
Chart not rendering	Check browser console for JavaScript errors
10. Revision History
Version	Date	Author	Changes
1.0	2026-07-08	POS Test Engineering Team	Initial creation