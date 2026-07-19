# docs/framework/offline_fault_injection_strategy.md
# Offline/Network-Fault Injection Strategy

**Version:** 1.0  
**Date:** 2026-07-14  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document reconciles the two offline/network-fault mechanisms in the codebase:

1. **OfflineSyncTest.java** (Phase 3.3) — Java TestNG test with simulated offline queue
2. **offline_sync_test.py** (Phase 7.3) — Python script with Docker network manipulation

---

## 2. Decision

### Authoritative Mechanism for CI

**OfflineSyncTest.java** is the authoritative mechanism for CI.

**Rationale:**
- Runs as part of the Maven test suite
- Can be executed in the existing CI pipeline without additional dependencies
- Uses the same testing framework as other tests (TestNG)
- Faster and more reliable (no Docker network manipulation required)
- More portable (works in any environment)

### Secondary Mechanism (Python Script)

**offline_sync_test.py** is demoted to a **manual/local-only helper**.

**Rationale:**
- Provides more comprehensive network fault injection for manual testing
- Useful for local development when full network isolation is needed
- Uses Docker network manipulation which is not suitable for CI
- Remains in the codebase as a manual tool

---

## 3. Implementation Details

### 3.1 OfflineSyncTest.java (Authoritative)

**Location:** `test-automation/src/test/java/com/toshiba/pos/api-tests/OfflineSyncTest.java`

**Mechanism:**
- Simulates offline behavior using a boolean flag
- Queue is stored in-memory (ConcurrentLinkedQueue)
- Does not manipulate actual Docker networks
- Runs as part of the Maven test suite

**Tags:**
```java
@Test(groups = {"e2e", "regression", "product:all"})

3.2 offline_sync_test.py (Manual/Local-Only)
Location: scripts/offline_sync_test.py

Mechanism:

Uses Docker network disconnect/connect commands

Real network fault injection

Requires Docker and docker-compose

Not suitable for CI

Usage:

bash
# Manual execution only
python scripts/offline_sync_test.py --test-only
python scripts/offline_sync_test.py --duration 30
4. Migration Plan
Step	Action	Status
1	Document authoritative mechanism	✅ Done
2	Update OfflineSyncTest.java to be self-contained	✅ Done
3	Update offline_sync_test.py with --test-only mode	✅ Done
4	Add warning header to Python script	✅ Done
5	Update CI pipeline to use only Java test	✅ Done
6	Remove Docker network manipulation from CI	✅ Done
5. Usage Guidelines
For CI/CD
Use mvn test -Dgroups=offline,regression to run offline tests

The Java test should be the primary validation

For Local Development
Use python scripts/offline_sync_test.py --test-only for quick testing

Use python scripts/offline_sync_test.py with Docker for full network fault injection

6. Revision History
Version	Date	Author	Changes
1.0	2026-07-14	POS Test Engineering Team	Initial creation
