# docs/architecture/adr/0001-test-framework-choice.md
# ADR 0001: Test Framework Choice

**Date:** 2026-07-08  
**Status:** Accepted  
**Authors:** POS Test Engineering Team

---

## Context

The POS Test Framework needed a test execution framework that could support multiple testing levels:
1. **API testing** for backend services (Phase 1–3)
2. **E2E testing** for product scenarios (Phase 13–14)
3. **UI testing** for the kiosk harness (Phase 15–16)
4. **Performance testing** (Phase 7)

**Requirements:**
- Support for data-driven tests
- Parallel execution capability
- Integration with CI/CD (Jenkins)
- Good reporting and logging
- Familiar to the team

---

## Decision

We decided to use **TestNG** as the primary test framework, with **RestAssured** for API testing and **Selenium WebDriver** for UI testing.

### Rationale

| Factor | TestNG | JUnit 5 | Cucumber |
|--------|--------|---------|----------|
| **Data Providers** | ✅ Built-in | ✅ (via @ParameterizedTest) | ❌ Not native |
| **Parallel Execution** | ✅ Built-in | ✅ | ⚠️ Limited |
| **Grouping/Tagging** | ✅ Built-in | ✅ (via tags) | ✅ |
| **XML Suite Configuration** | ✅ Built-in | ⚠️ Limited | ❌ |
| **CI/CD Integration** | ✅ Maven Surefire | ✅ Maven Surefire | ✅ Cucumber CLI |
| **BDD Support** | ⚠️ Via extensions | ⚠️ Via extensions | ✅ Native |
| **Team Familiarity** | ✅ High | ⚠️ Medium | ⚠️ Medium |
| **Reporting** | ✅ Surefire/Allure | ✅ Surefire/Allure | ✅ Cucumber Reports |

### Alternatives Considered

1. **JUnit 5** — Lacked native support for data-driven tests and XML suite configuration.
2. **Cucumber** — Better for BDD but added complexity for non-functional tests.
3. **Spock** — Groovy-based; team lacked Groovy expertise.

---

## Consequences

### Positive

- Data-driven tests via `@DataProvider` for product matrix
- Parallel execution for faster CI runs
- XML suite configuration (`testng.xml`) for multiple test profiles
- Integration with existing Maven Surefire plugin
- `@Flaky` group for quarantine mechanism

### Negative

- No native BDD support (Gherkin not used)
- Test output less readable than Cucumber reports
- Requires additional configuration for some features

---

## Decision Context

| Aspect | Detail |
|--------|--------|
| **When made** | Phase 3 (Test Automation) |
| **Re-evaluated** | Phase 18 (Framework Architecture) |
| **Constraints** | Must work with existing Maven build, Jenkins CI |
| **Trade-offs** | BDD readability vs. execution flexibility |

---

## Alternatives Evaluated

### JUnit 5

- **Pros:** Modern, supported by IntelliJ, better parameterized tests
- **Cons:** No XML suite support, limited data-driven testing

### Cucumber

- **Pros:** BDD, stakeholder-readable scenarios
- **Cons:** Extra complexity, less flexible for non-functional tests

### Spock

- **Pros:** Groovy-based, expressive syntax
- **Cons:** Team lacks Groovy expertise, additional language to maintain

---

## Notes

- The choice was made during Phase 3 and reaffirmed during Phase 18
- The `@DataProvider` pattern is used extensively for product matrix testing
- The quarantine mechanism (`@Flaky`) was added in Phase 20
- UI tests use the same framework with Selenium integration

---

## References

- [TestNG Documentation](https://testng.org/doc/)
- [RestAssured Documentation](https://rest-assured.io/)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)