# docs/architecture/adr/0002-mock-first-hardware-strategy.md
# ADR 0002: Mock-First Hardware Strategy

**Date:** 2026-07-08  
**Status:** Accepted  
**Authors:** POS Test Engineering Team

---

## Context

The POS system includes hardware peripherals (RS-232 devices, USB devices, iButton, VPD) that are difficult to test in CI/CD due to:
- Physical hardware availability
- Lab setup complexity
- Cost of hardware
- Limited remote access
- Inconsistent behavior across different models

**Requirements:**
- Test hardware-related features without physical hardware
- Enable CI/CD execution
- Maintain realistic behavior
- Support future hardware integration

---

## Decision

We adopted a **mock-first hardware strategy** where all hardware interactions are simulated in software using the "real-machine-less evaluation" approach.

### Implementation Approach

1. **RS-232 devices** → `socat` virtual COM port simulator (Phase 2.1)
2. **USB devices** → `usbip` stub (Phase 2.3)
3. **iButton** → Software simulation (Phase 4, 12.6)
4. **VPD** → Fixture JSON data (Phase 4.1)
5. **Payment devices** → Mock gateway (Phase 1.4)
6. **Printers** → Virtual ports (Phase 12.8, 12.9)

### Rationale

| Factor | Physical Hardware | Mock-First |
|--------|-------------------|------------|
| **Cost** | ❌ High | ✅ Low |
| **Availability** | ❌ Limited | ✅ Always available |
| **CI/CD Integration** | ❌ Difficult | ✅ Easy |
| **Repeatability** | ❌ Variable | ✅ Consistent |
| **Scale** | ❌ Limited | ✅ Infinite |
| **Realism** | ✅ High | ⚠️ Good enough |

---

## Consequences

### Positive

- Tests run in CI/CD without hardware
- Consistent test results
- No hardware procurement required
- Can simulate error conditions (paper-out, jams)
- Faster feedback loops

### Negative

- Not a complete validation of hardware behavior
- Some edge cases may not be simulated
- Requires maintaining mock behavior accuracy
- May miss hardware-specific timing issues

---

## Decision Context

| Aspect | Detail |
|--------|--------|
| **When made** | Phase 2 (Hardware Emulation) |
| **Re-evaluated** | Phase 12 (Product Profiles), Phase 18 (Framework) |
| **Constraints** | Must work in CI/CD, no physical hardware |
| **Trade-offs** | Realism vs. accessibility and repeatability |

---

## Implementation Details

### RS-232 Simulation

The `socat` simulator creates a virtual COM port pair that behaves like a real RS-232 connection. This is used by the printer profiles (Phase 12.8, 12.9).

### USB/IP Simulation

The `usbip_bind_script.sh` provides a documented stub that checks for device presence before acting, ensuring safe no-op behavior.

### Payment Simulation

The `mock-payment-gateway` uses sentinel values (e.g., `4111111111110000` for decline) to simulate payment outcomes.

### VPD Simulation

The `VPDValidatorTest` validates Vital Product Data using fixture JSON data instead of live hardware.

---

## Validation Strategy

| Validation Type | Method |
|-----------------|--------|
| **Functional** | Mock passes same API tests as real hardware |
| **Behavioral** | Mock simulates error conditions (paper-out, jam) |
| **Performance** | Mock provides consistent timing for tests |
| **Integration** | Mock integrates with Phase 1 services |

---

## Alternatives Considered

### Real Hardware in Lab

- **Pros:** Most realistic
- **Cons:** Cost, availability, maintenance, not suitable for CI/CD

### Hardware-in-the-Loop (HIL)

- **Pros:** More realistic than pure mock
- **Cons:** Complex setup, cost, limited scale

### No Hardware Testing

- **Pros:** Simplest
- **Cons:** Misses hardware-related defects

---

## Notes

- The mock-first strategy is inspired by Toshiba Tec's "real-machine-less evaluation" system
- The approach enables comprehensive testing without physical hardware
- Mock accuracy should be periodically validated against real hardware when available

---

## References

- [Phase 2.1: socat_rs232_simulator.sh](../../hardware-emulation/virtual-com-ports/socat_rs232_simulator.sh)
- [Phase 2.2: diags2x20.properties.template](../../hardware-emulation/virtual-com-ports/diags2x20.properties.template)
- [Phase 4.1: VPDValidatorTest.java](../../test-automation/src/test/java/com/toshiba/pos/diagnostic-validators/VPDValidatorTest.java)
- [Phase 12.8: TCx Single Station Printer](../../simulators/product-profiles/tcx-printer-single/)