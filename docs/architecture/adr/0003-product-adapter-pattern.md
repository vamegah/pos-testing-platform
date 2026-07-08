# docs/architecture/adr/0003-product-adapter-pattern.md
# ADR 0003: Product-Adapter Pattern

**Date:** 2026-07-08  
**Status:** Accepted  
**Authors:** POS Test Engineering Team

---

## Context

The framework needed to support multiple POS products (ELERA, MxP Vision Kiosk, MxP SMART hybrid/wall/wing, Self Checkout System 7, TCx Display, TCx Single/Dual Printer). Initially, each product had hand-written E2E test classes (Phase 13) and UI test classes (Phase 16), resulting in significant duplication.

**Challenges:**
- Duplicated test code across products
- Hard to add new products (copy-paste)
- Inconsistent test coverage
- High maintenance overhead

**Requirements:**
- Support new products with minimal effort
- Consistent test coverage across products
- Shared test logic
- Config-driven behavior

---

## Decision

We adopted a **Product-Adapter Pattern** where each product implements a common `ProductAdapter` interface, and a generic test engine drives all products.

### Architecture Diagram
┌─────────────────────────────────────────────────────────────────────────────┐
│ Generic Test Engine │
│ (ProductE2EEngine + TestNG) │
│ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Adapter Registry │ │
│ │ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │ │
│ │ │ ELERA │ │ Vision │ │ Hybrid │ │ Wall │ │ Wing │ │ │
│ │ │ Adapter │ │ Kiosk │ │ Adapter │ │ Adapter │ │ Adapter │ │ │
│ │ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │ │
│ │ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │ │
│ │ │ SCS7 │ │ Display │ │ Single │ │ Dual │ │ │
│ │ │ Adapter │ │ Adapter │ │ Printer │ │ Printer │ │ │
│ │ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ │ │
│ ▼ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ Product Profile JSON/YAML │ │
│ │ (capabilities, peripherals, payment methods, display matrix) │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘


### Interface Definition

```java
public interface ProductAdapter {
    ProductProfile getProfile();
    PeripheralCapabilities getPeripheralCapabilities();
    UiScreenSet getUiScreenSet();
    E2EFlowSteps getE2EFlowSteps();
    String getProductId();
    String getDisplayName();
    void validate();
    void load();
}

Consequences
Positive
New products = new adapter + manifest (config, not code)

Consistent test execution across all products

Centralized test logic in ProductE2EEngine

Easy to add/remove products

Clear separation of concerns

Negative
Adapter implementation required for each product

Less flexibility for product-specific test variations

Generic engine may not cover all edge cases

Requires discipline to maintain adapter interface

Decision Context
Aspect	Detail
When made	Phase 18 (Framework Architecture)
Re-evaluated	Phase 19 (Packaging), Phase 21 (Governance)
Constraints	Must work with existing Phase 12 profiles
Trade-offs	Flexibility vs. consistency
Implementation Details
Adapter Registry
The AdapterRegistry discovers and manages all product adapters via service loader or manual registration.

AbstractProductAdapter
Base class that handles common loading and validation logic, reducing boilerplate for individual adapters.

ProductE2EEngine
Generic test engine that iterates the adapter registry and runs the standard E2E flow for each product.

Scaffold Generator
The scaffold_new_product.sh script creates a new product scaffold from templates, reducing onboarding friction.

Products Supported
Product	Adapter Class	Status
ELERA	EleraAdapter	✅
MxP Vision Kiosk	MxpVisionKioskAdapter	✅
MxP SMART | hybrid	MxpSmartHybridAdapter	✅
MxP SMART | wall	MxpSmartWallAdapter	✅
MxP SMART | wing	MxpSmartWingAdapter	✅
Self Checkout System 7	SelfCheckoutSystem7Adapter	✅
TCx Display	TcxDisplayAdapter	✅
TCx Single Station Printer	TcxPrinterSingleAdapter	✅
TCx Dual Station Printer	TcxPrinterDualAdapter	✅
Alternatives Considered
Hand-Written Tests per Product
Pros: Full flexibility, product-specific customization

Cons: Duplication, maintenance burden, inconsistent coverage

Inheritance Hierarchy
Pros: Reuse through inheritance

Cons: Rigid hierarchy, difficult to add new products

Composition with Delegates
Pros: Flexible, easier to maintain

Cons: More complex than adapter pattern

Notes
The adapter pattern was introduced in Phase 18 as part of the framework refactor

The scaffold_new_product.sh script reduces new product onboarding to minutes

The adapter interface is designed to be stable with minimal changes

Product-specific variations can be handled through the ProductProfile capabilities

References
Phase 18.1: ProductAdapter Interface

Phase 18.3: ProductE2EEngine

Phase 19.3: Scaffold Generator

ADP 0001: Test Framework Choice