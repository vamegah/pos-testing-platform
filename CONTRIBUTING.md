# CONTRIBUTING.md
# Contributing to POS Test Framework

**Version:** 1.0  
**Date:** 2026-07-08  
**Author:** POS Test Engineering Team

---

## 1. Introduction

Thank you for considering contributing to the POS Test Framework! This document provides guidelines for contributing to the project. Please read it carefully before submitting any contributions.

---

## 2. Code of Conduct

This project adheres to a Contributor Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

---

## 3. Getting Started

### 3.1 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 11+ | Runtime and compilation |
| Maven | 3.8+ | Build and dependency management |
| Git | 2.30+ | Version control |
| Docker | 20.10+ | Local service orchestration |

### 3.2 First-Time Setup

```bash
# Clone the repository
git clone git@github.com:your-org/pos-test-framework.git
cd pos-test-framework

# Build the project
mvn clean install

# Run the tests
mvn test

# Start services locally
docker-compose up -d

3.3 Project Structure
text
pos-test-framework/
├── framework-core/           # Core framework modules
│   ├── src/main/java/       # Framework code
│   └── src/test/java/       # Framework tests
├── product-tests/           # Product-specific tests
│   ├── src/test/java/       # Test implementations
│   └── src/test/resources/  # Test resources
├── simulators/              # Product simulators
│   ├── pos-services/        # Mock POS services
│   ├── product-profiles/    # Product capability manifests
│   └── kiosk-ui-harness/    # UI test harness
├── ci-cd/                   # CI/CD pipelines
├── docs/                    # Documentation
├── scripts/                 # Utility scripts
└── test-automation/         # Legacy API tests
4. Coding Standards
4.1 Java Code Style
Formatting:

Use 4 spaces for indentation (no tabs)

Maximum line length: 120 characters

Curly braces on the same line (Egyptian style)

One statement per line

Example:

java
public class ExampleClass {
    private static final Logger logger = LogManager.getLogger(ExampleClass.class);

    public void exampleMethod(String param) {
        if (param != null) {
            logger.info("Processing: {}", param);
        } else {
            throw new IllegalArgumentException("param cannot be null");
        }
    }
}
4.2 Naming Conventions
Type	Convention	Example
Class	PascalCase	ProductAdapter
Interface	PascalCase (with I prefix)	IProductAdapter
Method	camelCase	getProfile()
Variable	camelCase	productName
Constant	UPPER_SNAKE_CASE	DEFAULT_TIMEOUT
Package	lowercase	com.toshiba.pos.adapter
4.3 Javadoc
All public APIs must have Javadoc comments:

java
/**
 * ProductAdapter Interface.
 * 
 * <p>Every product profile must implement this interface.
 * 
 * @see ProductProfile
 */
public interface ProductAdapter {
    /**
     * Get the product profile metadata.
     * 
     * @return ProductProfile containing name, version, and capabilities
     */
    ProductProfile getProfile();
}
4.4 Testing Standards
Test Type	Framework	Location
Unit Tests	JUnit 5 + Mockito	src/test/java/
Integration Tests	TestNG + RestAssured	src/test/java/
UI Tests	TestNG + Selenium	src/test/java/
Test Naming:

Unit tests: {ClassUnderTest}Test.java

Integration tests: {Feature}IT.java

E2E tests: {Product}E2ETest.java

5. Branch/PR Strategy
5.1 Branch Naming
Branch Type	Pattern	Example
Main branch	main	main
Feature	feature/{issue}-{description}	feature/123-add-product-adapter
Bug Fix	bugfix/{issue}-{description}	bugfix/456-fix-offline-sync
Release	release/{version}	release/1.0.0
Hotfix	hotfix/{issue}-{description}	hotfix/789-critical-security-fix
5.2 Branch Strategy
text
main
  │
  └─┬─ feature/123-add-product-adapter
    │   └── (PR to main)
    │
    └─┬─ release/1.0.0
      │   └── (tag: v1.0.0)
      │
      └─┬─ hotfix/789-critical-fix
          └── (PR to main and release)
5.3 Commit Message Format
text
<type>(<scope>): <subject>

<body>

<footer>
Types:

feat: New feature

fix: Bug fix

docs: Documentation only

style: Code style (formatting, missing semicolons, etc.)

refactor: Code refactoring

test: Adding/modifying tests

chore: Build process or auxiliary tool changes

Example:

text
feat(adapter): add ProductAdapter interface for ELERA

- Define ProductAdapter with getProfile(), getPeripheralCapabilities()
- Add AbstractProductAdapter for common functionality
- Implement EleraAdapter as first concrete implementation

Closes #123
6. Pull Request Process
6.1 PR Checklist
Before submitting a PR, ensure:

Code compiles without errors

All tests pass locally

New features have tests

Documentation updated

No new Checkstyle or SpotBugs violations

Commit messages follow the format

PR title follows the format

Linked to an issue (if applicable)

6.2 PR Template
markdown
## Description
<!-- Describe the changes in this PR -->

## Type of Change
<!-- Mark the appropriate option with an 'x' -->
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Refactoring
- [ ] Test update
- [ ] Other (please describe)

## Testing
<!-- Describe how you tested your changes -->
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] E2E tests added/updated
- [ ] Manually tested

## Checklist
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Documentation updated
- [ ] No new Checkstyle/SpotBugs violations
- [ ] Commit messages follow the format

## Related Issues
<!-- Link to related issues (e.g., Closes #123) -->
Closes #
6.3 Review Process
Open PR with clear title and description

Automated checks run (build, tests, static analysis)

Code review by at least one maintainer

Address feedback with additional commits

Rebase/merge to keep branch up to date

Approval from required reviewers

Merge (squash and merge recommended)

6.4 PR Approval Requirements
Role	Required
Author	Must be a team member or external contributor
Reviewers	At least 2 approvals
CI	All checks must pass
Labels	Appropriate labels applied
7. Testing Requirements
7.1 Unit Tests
Coverage target: >80% for new code

Execution: mvn test

Framework: JUnit 5 + Mockito

7.2 Integration Tests
Coverage target: All critical APIs

Execution: mvn verify -Pintegration

Framework: TestNG + RestAssured

7.3 E2E Tests
Coverage target: All products

Execution: mvn test -Dgroups=e2e

Framework: TestNG + ProductE2EEngine

7.4 UI Tests
Coverage target: All product screens

Execution: mvn test -Dgroups=ui

Framework: TestNG + Selenium

8. Static Analysis
8.1 Checkstyle
Configuration: config/checkstyle/checkstyle.xml

Execution: mvn checkstyle:check

Requirement: No new violations

8.2 SpotBugs
Configuration: config/spotbugs/exclude.xml

Execution: mvn spotbugs:check

Requirement: No new critical/high findings

9. Documentation Standards
9.1 Code Documentation
All public APIs must have Javadoc

Complex logic should have inline comments

Use @see, @link, and @deprecated where appropriate

9.2 User Documentation
Document	Location	Purpose
README	README.md	Project overview
Contributing	CONTRIBUTING.md	Guidelines for contributors
Quality Bar	docs/framework/QUALITY_BAR.md	Quality standards
Reporting	docs/framework/REPORTING.md	Test reporting
Release Process	docs/framework/RELEASE_PROCESS.md	Release guidelines
10. Getting Help
Channel	Purpose
GitHub Issues	Bug reports, feature requests
Team Chat	Real-time questions
Wiki	Knowledge base
Email	Project maintainers
11. Revision History
Version	Date	Author	Changes
1.0	2026-07-08	POS Test Engineering Team	Initial creation
