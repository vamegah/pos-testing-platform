# docs/framework/test_authoring_guide.md
# Test Authoring Guide

**Version:** 1.0  
**Date:** 2026-07-08  
**Author:** POS Test Engineering Team

---

## 1. Introduction

This guide provides standards for writing tests in the POS Test Framework. It covers Gherkin style, tagging taxonomy, and naming conventions for new adapters and fixtures.

**Audience:** SDETs, QA Engineers, and Developers writing tests

---

## 2. Gherkin Style Guide

### 2.1 Feature Files

Feature files should follow the **Given-When-Then** format:
Feature: Product E2E Flow
As a customer
I want to complete a purchase
So that I can receive my items

Scenario: Happy path sale
Given the product is in stock
When I scan the item
And I proceed to payment
Then the transaction is approved
And a receipt is printed

text

### 2.2 Given

**Purpose:** Set up the initial state

**Rules:**
- Describe the starting state
- Use clear, specific language
- One Given per logical setup step

**Examples:**
Given the product "SKU-1001" is available
Given the customer has a valid payment method
Given the device is in self-service mode

text

### 2.3 When

**Purpose:** Describe the action being tested

**Rules:**
- Use present tense
- One When per action step
- Include the actor (customer, system, agent)

**Examples:**
When the customer scans the item
When the system calculates the tax
When the cashier overrides the weight mismatch

text

### 2.4 Then

**Purpose:** Describe the expected outcome

**Rules:**
- Use present tense
- One Then per outcome
- Be specific about the result

**Examples:**
Then the transaction is approved
Then the receipt contains all items
Then the total is $10.00

text

### 2.5 And/But

**Purpose:** Chain multiple steps of the same type

**Rules:**
- Use And for additional steps of the same type
- Use But for negative conditions

**Examples:**
Given the product is in stock
And the customer has a valid payment method
When I scan the item
And I proceed to payment
Then the transaction is approved
But the receipt is not printed (paper out)

text

---

## 3. Tagging Taxonomy

### 3.1 Standard Tags

| Tag | Purpose | Usage |
|-----|---------|-------|
| `@smoke` | Critical path tests | Run on every PR (fast) |
| `@regression` | Full regression tests | Run nightly |
| `@flaky` | Quarantined tests | Run separately, non-blocking |
| `@e2e` | End-to-end tests | Full product flow |
| `@api` | API-only tests | No UI required |
| `@ui` | UI tests | Requires Chrome |
| `@performance` | Performance tests | JMeter load/stress tests |
| `@diagnostic` | Hardware diagnostic tests | RS-232, iButton, VPD |

### 3.2 Product Tags

| Tag | Product |
|-----|---------|
| `@product:elera` | ELERA® platform |
| `@product:vision-kiosk` | MxP™ Vision Kiosk |
| `@product:smart-hybrid` | MxP™ SMART \| hybrid |
| `@product:smart-wall` | MxP™ SMART \| wall |
| `@product:smart-wing` | MxP™ SMART \| wing |
| `@product:scs7` | Self Checkout System 7 |
| `@product:tcx-display` | TCx® Display |
| `@product:tcx-printer-single` | TCx® Single Station Printer |
| `@product:tcx-printer-dual` | TCx® Dual Station Printer |

### 3.3 Test Type Tags

| Tag | Purpose |
|-----|---------|
| `@unit` | Unit tests (developer-owned) |
| `@integration` | Integration tests |
| `@acceptance` | Acceptance tests |
| `@negative` | Negative/error path tests |
| `@boundary` | Boundary/edge case tests |

### 3.4 Tag Usage Example

```java
@Test(groups = {"smoke", "e2e", "product:elera"})
public void testEleraHappyPath() {
    // Smoke test that runs on every PR
}

@Test(groups = {"regression", "product:elera"})
public void testEleraEdgeCase() {
    // Full regression test run nightly
}

@Test(groups = {"flaky", "product:vision-kiosk"})
public void testVisionKioskFlaky() {
    // Quarantined, non-blocking
}
4. Naming Conventions
4.1 Test Classes
Type	Pattern	Example
API Tests	{Feature}APITest.java	TransactionAPITest.java
E2E Tests	{Product}E2ETest.java	EleraE2ETest.java
UI Tests	{Product}UiTest.java	MxpVisionKioskUiTest.java
Unit Tests	{Class}Test.java	ProductAdapterTest.java
Integration Tests	{Feature}IT.java	PricingServiceIT.java
4.2 Test Methods
Type	Pattern	Example
Happy path	test{Feature}Success()	testHappyPathSale()
Error path	test{Feature}Failure()	testInvalidSkuFailure()
Boundary	test{Feature}Boundary()	testZeroSubtotalBoundary()
Negative	test{Feature}Negative()	testNegativeAmount()
Data-driven	test{Feature}With{Provider}()	testTaxCalculationWithRegions()
4.3 Adapters
Type	Pattern	Example
Adapter	{Product}Adapter.java	EleraAdapter.java
Abstract	Abstract{Type}Adapter.java	AbstractProductAdapter.java
Registry	{Type}Registry.java	AdapterRegistry.java
4.4 Fixtures
Type	Pattern	Example
Factory	{Type}Factory.java	FixtureFactory.java
Config	{Type}Config.java	TestDataConfig.java
Model	{Type}.java	BasketItem.java
5. Test Organization
5.1 Package Structure
text
com.toshiba.pos/
├── adapter/           # Product adapters
│   ├── elera/
│   ├── mxp/
│   ├── scs/
│   └── tcx/
├── engine/            # Test engine
├── model/             # Data models
├── registry/          # Adapter registry
├── testdata/          # Test data and fixtures
└── ui/                # UI test base classes
5.2 Test Resources
text
src/test/resources/
├── testng.xml         # TestNG suite configuration
├── profiles/          # Product profile JSONs
├── locales/           # Localization JSONs
└── features/          # Gherkin feature files (if using Cucumber)
6. Best Practices
6.1 Test Isolation
Each test should be independent

Tests should not depend on execution order

Clean up test data after execution

6.2 Assertions
Use clear, descriptive assertion messages

Prefer specific assertions over generic

Use assertThat with Hamcrest for readability

java
// Good
assertThat(actual)
    .withFailMessage("Expected total $10.00 but got $%s", actual)
    .isEqualTo(10.00);

// Bad
assertTrue(total == 10.00);
6.3 Logging
Use structured logging

Include relevant context in logs

Use appropriate log levels

java
// Good
logger.info("Processing transaction: {} for customer {}", transactionId, customerId);
logger.warn("Weight mismatch for SKU {}: expected {}kg, measured {}kg", sku, expected, measured);

// Bad
logger.info("Processing");
logger.error("Error!");
6.4 Wait Strategies
Use explicit waits for UI tests

Avoid Thread.sleep()

Use WebDriverWait with reasonable timeouts

java
// Good
wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("payment-status")));

// Bad
Thread.sleep(5000);
7. Test Data Fixtures
7.1 Fixture Factory Usage
java
// Create a fixture factory with a seed for repeatability
FixtureFactory factory = new FixtureFactory(
    new TestDataConfig.Builder().seed(42).build()
);

// Generate a basket
List<BasketItem> basket = factory.generateBasket(5);

// Generate a transaction
Transaction transaction = factory.generateTransaction();
7.2 Custom Fixtures
java
// Create a product-specific fixture
ProductFixture fixture = factory.generateProductFixture("elera");

// Use the fixture in a test
String sku = fixture.getBasket().get(0).getSku();
double total = fixture.getTotal();
8. Code Review Checklist
Tests are independent (no order dependency)

Proper tags are applied (@smoke, @regression, @flaky, etc.)

Gherkin style followed (Given-When-Then)

Assertions have descriptive messages

Logging is appropriate

No hard-coded sensitive data

Tests run in CI (no local-only assumptions)

Documentation updated

9. Revision History
Version	Date	Author	Changes
1.0	2026-07-08	POS Test Engineering Team	Initial creation
