# docs/framework/QUALITY_BAR.md
# Quality Bar for POS Test Framework

**Version:** 1.0  
**Date:** 2026-07-08  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document defines the quality bar for the POS Test Framework. All code must meet the quality standards described here before being merged.

---

## 2. Static Analysis Tools

### 2.1 Checkstyle

| Tool | Purpose | Configuration |
|------|---------|---------------|
| **Checkstyle** | Code style enforcement | `config/checkstyle/checkstyle.xml` |
| **Severity** | Warning (build fails on violations) | |

**Rules:**
- All public methods must have Javadoc
- Line length: max 120 characters
- No star imports
- Consistent naming conventions
- No magic numbers (except constants)

**Quality Bar:**
- ✅ Build passes with no Checkstyle violations
- ✅ Grandfathering allowed: Existing violations are excluded
- ❌ New violations fail the build

### 2.2 SpotBugs (FindBugs)

| Tool | Purpose | Configuration |
|------|---------|---------------|
| **SpotBugs** | Bug pattern detection | `config/spotbugs/exclude.xml` |
| **Severity** | Medium (build fails on new critical/high findings) | |

**Rules:**
- No null pointer dereferences
- No unclosed resources
- No insecure deserialization
- No information exposure

**Quality Bar:**
- ✅ Build passes with no new critical/high findings
- ✅ Grandfathering allowed: Existing findings are excluded
- ❌ New critical/high findings fail the build

---

## 3. Quality Bar Rules

### 3.1 New Code

| Aspect | Requirement |
|--------|-------------|
| **Compilation** | Must compile without errors |
| **Checkstyle** | Must pass Checkstyle validation |
| **SpotBugs** | Must pass SpotBugs (no new critical/high findings) |
| **Unit Tests** | Must have unit tests (covered in Phase 18+) |
| **Javadoc** | All public APIs must be documented |
| **Code Review** | Must pass at least one code review |

### 3.2 Existing Code

| Aspect | Requirement |
|--------|-------------|
| **Grandfathering** | Existing violations are allowed (excluded) |
| **Refactoring** | Refactored code must meet the quality bar |
| **Cleanup** | Teams encouraged to fix existing violations |

### 3.3 Build Failures

| Scenario | Action |
|----------|--------|
| **New Checkstyle violation** | ❌ Build fails |
| **New SpotBugs critical/high** | ❌ Build fails |
| **Existing violation (excluded)** | ✅ Build passes |
| **New SpotBugs low/medium** | ⚠️ Warning, build continues |

---

## 4. Running Static Analysis

### 4.1 Local Execution

```bash
# Run Checkstyle only
mvn checkstyle:check

# Run SpotBugs only
mvn spotbugs:check

# Run both
mvn validate verify

4.2 CI Integration
Static analysis runs automatically in the Jenkins pipeline:

Stage	Tools	When
PR Stage	Checkstyle, SpotBugs (new findings only)	Every PR
Nightly	Checkstyle, SpotBugs (full scan)	Nightly
5. Violation Handling
5.1 Excluding Violations
Violations can be excluded in the following ways:

Checkstyle: Add to config/checkstyle/checkstyle.xml (grandfathering)

SpotBugs: Add to config/spotbugs/exclude.xml (grandfathering)

5.2 Fixing Violations
When fixing a violation:

Remove the exclusion from the config

Update the code to comply

Verify the fix in CI

5.3 New Violations Policy
Critical/High: Must be fixed immediately

Medium/Low: Can be addressed in a follow-up PR

Style: Must be fixed before merging

6. Metrics Tracking
Metric	Target	Measurement
Checkstyle violations	0 (new code)	CI report
SpotBugs critical/high	0 (new code)	CI report
Test coverage	>80% (target)	JaCoCo report
7. Revision History
Version	Date	Author	Changes
1.0	2026-07-08	POS Test Engineering Team	Initial creation
