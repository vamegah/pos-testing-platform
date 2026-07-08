// framework-core/src/main/java/com/toshiba/pos/testdata/FlakyTest.java

package com.toshiba.pos.testdata;

import org.testng.annotations.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

/**
 * FlakyTest — Demonstrates the flaky-test quarantine mechanism.
 * 
 * <p>This test is deliberately flaky to demonstrate the quarantine mechanism.
 * It randomly passes or fails to simulate real-world flaky tests.
 * 
 * <p>It is tagged with {@code @Flaky} group and excluded from build-blocking runs.
 * The test is still executed and tracked in a separate report.
 * 
 * <p>Usage:
 * <pre>
 *   # Run all tests (excludes flaky)
 *   mvn test
 * 
 *   # Run only flaky tests
 *   mvn test -Dgroups=flaky
 * 
 *   # Run flaky tests and generate report
 *   mvn test -Dgroups=flaky -Dflaky.report=true
 * </pre>
 */
@Test(groups = {"flaky", "quarantine"})
public class FlakyTest {

    private static final Logger logger = LogManager.getLogger(FlakyTest.class);

    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private static int attemptCount = 0;

    /**
     * A flaky test that passes ~70% of the time.
     * 
     * <p>This simulates a real-world flaky test that fails intermittently.
     * It is excluded from build-blocking runs but tracked separately.
     */
    @Test(description = "Flaky test — passes ~70% of the time")
    public void testFlakyPassRate70Percent() {
        attemptCount++;
        boolean passes = RANDOM.nextDouble() < 0.7;
        
        logger.info("Flaky test attempt #{}: {}", attemptCount, passes ? "PASSED" : "FAILED");
        
        if (!passes) {
            throw new AssertionError("Flaky test failed on attempt #" + attemptCount + 
                " (this failure is expected and demonstrates the quarantine mechanism)");
        }
        // Test passed
    }

    /**
     * A flaky test that fails ~50% of the time.
     */
    @Test(description = "Flaky test — passes ~50% of the time")
    public void testFlakyPassRate50Percent() {
        attemptCount++;
        boolean passes = RANDOM.nextDouble() < 0.5;
        
        logger.info("Flaky test (50%) attempt #{}: {}", attemptCount, passes ? "PASSED" : "FAILED");
        
        if (!passes) {
            throw new AssertionError("Flaky test (50%) failed on attempt #" + attemptCount +
                " (this failure is expected and demonstrates the quarantine mechanism)");
        }
        // Test passed
    }

    /**
     * A flaky test that is consistently flaky.
     */
    @Test(description = "Flaky test — consistently flaky", invocationCount = 5)
    public void testFlakyConsistentlyFlaky() {
        attemptCount++;
        boolean passes = RANDOM.nextDouble() < 0.6;
        
        logger.info("Flaky test (consistent) attempt #{}: {}", attemptCount, passes ? "PASSED" : "FAILED");
        
        if (!passes) {
            throw new AssertionError("Flaky test (consistent) failed on attempt #" + attemptCount +
                " (this failure is expected and demonstrates the quarantine mechanism)");
        }
        // Test passed
    }

    /**
     * Reset the flaky test state (for clean test runs).
     */
    public static void reset() {
        attemptCount = 0;
        logger.debug("Flaky test state reset");
    }

    /**
     * Get the number of attempts made.
     */
    public static int getAttemptCount() {
        return attemptCount;
    }
}