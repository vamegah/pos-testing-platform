# CHANGELOG.md
# Changelog for POS Test Framework

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial framework structure with multi-module Maven build
- `framework-core` module with ProductAdapter interface and model classes
- `product-tests` module for product-specific test execution
- Generic ProductE2EEngine with parameterized TestNG suite
- AdapterRegistry for automatic adapter discovery
- FixtureFactory for config-driven test data generation

### Changed
- N/A

### Deprecated
- N/A

### Removed
- Hand-written Phase 13 E2E test classes (replaced by generic engine)

### Fixed
- N/A

### Security
- N/A

---

## [0.1.0] - 2026-07-08

### Added
- Initial release of the POS Test Framework
- Framework core module with adapter interface
- Product adapter implementations for all 9 Phase 12 profiles
- Generic product E2E engine with TestNG integration
- Adapter registry for automatic product discovery
- Fixture factory for repeatable test data generation
- Multi-module Maven build structure
- Changelog and release process documentation

### Changed
- N/A

### Deprecated
- N/A

### Removed
- N/A

### Fixed
- N/A

### Security
- N/A