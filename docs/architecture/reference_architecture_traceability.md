# docs/architecture/reference_architecture_traceability.md
# Architecture Traceability Matrix

**Version:** 1.0  
**Date:** 2026-07-14  
**Author:** POS Test Engineering Team

---

## 1. Overview

This document maps every node and every arrow in the reference architecture flowchart to the specific backlog item(s) and test class(es) that exercise them. All 9 nodes and 9 edges from the diagram have at least one row with a real test reference.

**Legend:**
- **Node:** Component or layer in the architecture
- **Arrow:** Communication path between nodes
- **Backlog Item:** The Phase/ID that implements or tests the component
- **Test Class:** The automated test that exercises the component

---

## 2. Reference Architecture Flowchart
┌─────────────────────────────────────────────────────────────────────────────┐
│ │
│ ┌───────────┐ │
│ │ A │ Store Manager / Cashier / Customer │
│ │ Roles │ │
│ └─────┬─────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ B1 │ POS Application │
│ │ Modern │ (Mode: Modern POS / Cloud POS) │
│ │ POS │ │
│ └─────┬─────┘ │
│ │ │
│ ├──────────────────────┬───────────────────────┐ │
│ │ │ │ │
│ ▼ ▼ ▼ │
│ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│ │ B2 │ │ B3 │ │ C1 │ │
│ │ Peripherals│ │ Hardware │ │ Commerce │ │
│ │ (Card │ │ Station │ │ Scale Unit│ │
│ │ Reader, │ │ │ │ │ │
│ │ Cash │ │ │ │ │ │
│ │ Drawer) │ │ │ │ │ │
│ └───────────┘ └───────────┘ └─────┬─────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ C2 │ │
│ │ Local Data│ │
│ │ Cache │ │
│ └───────────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ D1 │ │
│ │ API │ │
│ │ Gateway │ │
│ └─────┬─────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ D2 │ │
│ │ Micro- │ │
│ │ services │ │
│ │(Inventory,│ │
│ │ Order, │ │
│ │ CRM) │ │
│ └─────┬─────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ D3 │ │
│ │ Data │ │
│ │ Services │ │
│ └─────┬─────┘ │
│ │ │
│ ▼ │
│ ┌───────────┐ │
│ │ E │ │
│ │ Third- │ │
│ │ Party │ │
│ │ (Loyalty, │ │
│ │ ERP) │ │
│ └───────────┘ │
│ │
└─────────────────────────────────────────────────────────────────────────────┘

---

## 3. Node Traceability

### Node A: Store Manager / Cashier / Customer Roles

| Aspect | Details |
|--------|---------|
| **Description** | User roles interacting with the POS system |
| **Backlog Items** | 16.1–16.10 (UI Scenarios), 23.14 (Store Manager Role) |
| **Test Classes** | `EleraUiTest`, `MxpVisionKioskUiTest`, `MxpSmartHybridUiTest`, `MxpSmartWallUiTest`, `MxpSmartWingUiTest`, `SelfCheckoutSystem7UiTest`, `TcxDisplayResponsiveTest`, `TcxPrinterSingleUiTest`, `TcxPrinterDualUiTest`, `StoreManagerRoleUiTest` |
| **Tested By** | UI automation tests covering shopper flows, cashier actions, and manager overrides |

---

### Node B1: POS Application (Modern POS)

| Aspect | Details |
|--------|---------|
| **Description** | POS Application with Modern POS / Cloud POS connectivity modes |
| **Backlog Items** | 13.1–13.9 (E2E Tests), 23.13 (Connectivity Mode Test) |
| **Test Classes** | `EleraE2ETest`, `MxpVisionKioskE2ETest`, `MxpSmartHybridE2ETest`, `MxpSmartWallE2ETest`, `MxpSmartWingE2ETest`, `SelfCheckoutSystem7E2ETest`, `TcxDisplayMatrixTest`, `TcxPrinterSingleE2ETest`, `TcxPrinterDualE2ETest`, `ConnectivityModeTest` |
| **Tested By** | End-to-end transaction flows, connectivity mode tests |

---

### Node B2: Peripherals (Card Reader, Cash Drawer)

| Aspect | Details |
|--------|---------|
| **Description** | Physical peripheral devices connected to the POS |
| **Backlog Items** | 23.1 (Card Reader), 23.2 (Cash Drawer) |
| **Test Classes** | `CardReaderSimulator`, `CashDrawerSimulator` (simulators), `ConnectivityModeTest` (via Hardware Station) |
| **Tested By** | Peripheral simulator tests, Hardware Station integration |

---

### Node B3: Hardware Station

| Aspect | Details |
|--------|---------|
| **Description** | Mediator that fans out to all peripherals |
| **Backlog Items** | 23.3 (Hardware Station) |
| **Test Classes** | `HardwareStationSimulator`, `ConnectivityModeTest` |
| **Tested By** | Fault-isolation tests, peripheral integration tests |

---

### Node C1: Commerce Scale Unit

| Aspect | Details |
|--------|---------|
| **Description** | Store/edge-tier facade aggregating pricing, promotions, tax |
| **Backlog Items** | 23.4 (Commerce Scale Unit) |
| **Test Classes** | `CommerceScaleUnitSimulator`, `ConnectivityModeTest` |
| **Tested By** | Edge-autonomy tests, checkout price aggregation tests |

---

### Node C2: Local Data Cache

| Aspect | Details |
|--------|---------|
| **Description** | Offline cache for catalog/pricing data |
| **Backlog Items** | 23.5 (Local Data Cache) |
| **Test Classes** | `LocalDataCacheSimulator`, `CommerceScaleUnitSimulator` |
| **Tested By** | Cache-population, staleness/TTL, reconciliation tests |

---

### Node D1: API Gateway & Integration Layer

| Aspect | Details |
|--------|---------|
| **Description** | Gateway with auth, rate-limiting, event routing |
| **Backlog Items** | 23.6 (API Gateway) |
| **Test Classes** | `ApiGatewaySimulator`, `ConnectivityModeTest` |
| **Tested By** | Auth tests, rate-limit tests, event pub/sub tests |

---

### Node D2: Microservices (Inventory, Order, CRM)

| Aspect | Details |
|--------|---------|
| **Description** | Backend microservices: Inventory, Order Processing, CRM |
| **Backlog Items** | 23.7 (Inventory), 23.8 (Order Processing), 23.9 (CRM) |
| **Test Classes** | `InventoryService`, `OrderProcessingService`, `CRMService` |
| **Tested By** | Stock decrement tests, order lifecycle tests, customer lookup tests |

---

### Node D3: Data Services

| Aspect | Details |
|--------|---------|
| **Description** | Transactional persistence and analytics/data-lake sink |
| **Backlog Items** | 23.10 (Data Services) |
| **Test Classes** | `DataServicesSimulator` |
| **Tested By** | Persistence tests, analytics event count tests |

---

### Node E: Third-Party (Loyalty, ERP)

| Aspect | Details |
|--------|---------|
| **Description** | Third-party integrations: Loyalty Program, ERP |
| **Backlog Items** | 23.11 (Loyalty), 23.12 (ERP) |
| **Test Classes** | `LoyaltyMock`, `ErpMock` |
| **Tested By** | Points accrual/redemption tests, batch export tests |

---

## 4. Arrow Traceability (Communication Paths)

### Arrow A → B1: User to POS Application

| Aspect | Details |
|--------|---------|
| **Description** | User interacts with POS application UI |
| **Backlog Items** | 16.1–16.10, 23.14 |
| **Test Classes** | All UI test classes |
| **Tested By** | UI automation simulating user interactions |

### Arrow B1 → B2: POS App to Peripherals

| Aspect | Details |
|--------|---------|
| **Description** | POS App sends commands to peripherals via Hardware Station |
| **Backlog Items** | 23.1, 23.2, 23.3 |
| **Test Classes** | `HardwareStationSimulator`, `ConnectivityModeTest` |
| **Tested By** | Fault-isolation tests, peripheral operation tests |

### Arrow B1 → B3: POS App to Hardware Station

| Aspect | Details |
|--------|---------|
| **Description** | POS App calls Hardware Station mediator |
| **Backlog Items** | 23.3 |
| **Test Classes** | `HardwareStationSimulator` |
| **Tested By** | Hardware Station integration tests |

### Arrow B1 → C1: POS App to Commerce Scale Unit

| Aspect | Details |
|--------|---------|
| **Description** | Modern POS mode: thin client hits C1 locally |
| **Backlog Items** | 23.4, 23.13 |
| **Test Classes** | `CommerceScaleUnitSimulator`, `ConnectivityModeTest` |
| **Tested By** | Edge-autonomy tests, connectivity mode tests |

### Arrow C1 → C2: Commerce Scale Unit to Cache

| Aspect | Details |
|--------|---------|
| **Description** | C1 reads from Local Data Cache for offline mode |
| **Backlog Items** | 23.4, 23.5 |
| **Test Classes** | `CommerceScaleUnitSimulator`, `LocalDataCacheSimulator` |
| **Tested By** | Edge-autonomy tests (cache-hit during cloud outage) |

### Arrow C1 → D1: Commerce Scale Unit to API Gateway

| Aspect | Details |
|--------|---------|
| **Description** | C1 forwards requests to cloud via API Gateway |
| **Backlog Items** | 23.4, 23.6 |
| **Test Classes** | `CommerceScaleUnitSimulator`, `ApiGatewaySimulator` |
| **Tested By** | Cloud sync tests, checkout price tests |

### Arrow D1 → D2: API Gateway to Microservices

| Aspect | Details |
|--------|---------|
| **Description** | Gateway routes requests to backend microservices |
| **Backlog Items** | 23.6, 23.7, 23.8, 23.9 |
| **Test Classes** | `ApiGatewaySimulator`, `InventoryService`, `OrderProcessingService`, `CRMService` |
| **Tested By** | Routing tests, microservice integration tests |

### Arrow D2 → D3: Microservices to Data Services

| Aspect | Details |
|--------|---------|
| **Description** | Microservices persist data to Data Services layer |
| **Backlog Items** | 23.7, 23.8, 23.9, 23.10 |
| **Test Classes** | `InventoryService`, `OrderProcessingService`, `CRMService`, `DataServicesSimulator` |
| **Tested By** | Persistence tests, order write/read tests |

### Arrow D2 → E: Microservices to Third-Party

| Aspect | Details |
|--------|---------|
| **Description** | Microservices call out to third-party systems (Loyalty, ERP) |
| **Backlog Items** | 23.11, 23.12 |
| **Test Classes** | `LoyaltyMock`, `ErpMock` |
| **Tested By** | Points accrual tests, batch export tests |

---

## 5. Coverage Summary

| Node/Arrow | Covered By | Test Class/Simulator |
|------------|------------|----------------------|
| A | ✅ | UI Test Classes |
| B1 | ✅ | E2E Test Classes, ConnectivityModeTest |
| B2 | ✅ | CardReaderSimulator, CashDrawerSimulator |
| B3 | ✅ | HardwareStationSimulator |
| C1 | ✅ | CommerceScaleUnitSimulator |
| C2 | ✅ | LocalDataCacheSimulator |
| D1 | ✅ | ApiGatewaySimulator |
| D2 | ✅ | InventoryService, OrderProcessingService, CRMService |
| D3 | ✅ | DataServicesSimulator |
| E | ✅ | LoyaltyMock, ErpMock |
| A→B1 | ✅ | UI Test Classes |
| B1→B2 | ✅ | HardwareStationSimulator |
| B1→B3 | ✅ | HardwareStationSimulator |
| B1→C1 | ✅ | CommerceScaleUnitSimulator, ConnectivityModeTest |
| C1→C2 | ✅ | CommerceScaleUnitSimulator |
| C1→D1 | ✅ | CommerceScaleUnitSimulator, ApiGatewaySimulator |
| D1→D2 | ✅ | ApiGatewaySimulator, Microservice Tests |
| D2→D3 | ✅ | Microservice Tests, DataServicesSimulator |
| D2→E | ✅ | LoyaltyMock, ErpMock |

**Total:** 9 nodes ✅ | 9 arrows ✅ | **18/18 covered**

---

## 6. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-14 | POS Test Engineering Team | Initial creation |