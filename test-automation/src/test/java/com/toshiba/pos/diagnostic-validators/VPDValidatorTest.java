// test-automation/src/test/java/com/toshiba/pos/diagnostic-validators/VPDValidatorTest.java

package com.toshiba.pos.diagnostic_validators;

import com.toshiba.pos.BaseTest;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import java.util.*;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

/**
 * VPD (Vital Product Data) Validator Test
 * 
 * Validates simulated Vital Product Data payload for POS hardware diagnostics.
 * Tests that serial numbers, firmware versions, and other VPD fields are
 * present and well-formed.
 * 
 * This follows the "real-machine-less evaluation" approach:
 * - Uses fixture JSON data, not a live device
 * - Validates format and completeness of VPD data
 * - Enables automated testing without physical hardware
 * 
 * VPD typically contains:
 *   - Serial Number
 *   - Firmware Version
 *   - Hardware Revision
 *   - Manufacturer
 *   - Model Number
 *   - Build Date
 *   - MAC Address
 *   - Feature Flags
 */
public class VPDValidatorTest extends BaseTest {
    
    // Common VPD fields expected in Toshiba POS devices
    private static final Set<String> EXPECTED_VPD_FIELDS = new HashSet<>(Arrays.asList(
        "serial_number",
        "firmware_version",
        "hardware_revision",
        "manufacturer",
        "model_number",
        "build_date",
        "mac_address",
        "feature_flags"
    ));
    
    // Valid serial number pattern: TCX-XXX-XXXXX (TCX-810-12345)
    private static final Pattern SERIAL_PATTERN = Pattern.compile("^TCX-[0-9]{3}-[A-Z0-9]{5}$");
    
    // Valid firmware version pattern: vX.Y.Z (v3.2.1)
    private static final Pattern FIRMWARE_PATTERN = Pattern.compile("^v[0-9]+\\.[0-9]+\\.[0-9]+$");
    
    // Valid MAC address pattern: XX:XX:XX:XX:XX:XX
    private static final Pattern MAC_PATTERN = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    
    // Valid date format: YYYY-MM-DD
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    
    // Sample VPD payloads for different TCx models
    private static final Map<String, Map<String, Object>> SAMPLE_VPD_DATA = new LinkedHashMap<>();
    
    @BeforeClass
    public void setUpClass() {
        logger.info("=== VPDValidatorTest Initialized ===");
        logger.info("Testing Vital Product Data validation");
        logger.info("=====================================");
        
        // Load sample VPD data for TCx 810
        Map<String, Object> vpd810 = new LinkedHashMap<>();
        vpd810.put("serial_number", "TCX-810-A1B2C");
        vpd810.put("firmware_version", "v3.2.1");
        vpd810.put("hardware_revision", "REV-D");
        vpd810.put("manufacturer", "Toshiba Global Commerce Solutions");
        vpd810.put("model_number", "TCx 810");
        vpd810.put("build_date", "2025-11-15");
        vpd810.put("mac_address", "00:1A:2B:3C:4D:5E");
        vpd810.put("feature_flags", "WLAN,BT,USB,ETH,RS232");
        SAMPLE_VPD_DATA.put("TCX-810-A1B2C", vpd810);
        
        // Load sample VPD data for TCx 700
        Map<String, Object> vpd700 = new LinkedHashMap<>();
        vpd700.put("serial_number", "TCX-700-F9G8H");
        vpd700.put("firmware_version", "v2.4.5");
        vpd700.put("hardware_revision", "REV-B");
        vpd700.put("manufacturer", "Toshiba Global Commerce Solutions");
        vpd700.put("model_number", "TCx 700");
        vpd700.put("build_date", "2025-09-01");
        vpd700.put("mac_address", "00:1A:2B:3C:4D:5F");
        vpd700.put("feature_flags", "USB,ETH,RS232");
        SAMPLE_VPD_DATA.put("TCX-700-F9G8H", vpd700);
        
        // Load sample VPD data for SurePOS 700
        Map<String, Object> vpdSP700 = new LinkedHashMap<>();
        vpdSP700.put("serial_number", "SP700-001-XY");
        vpdSP700.put("firmware_version", "v4.1.0");
        vpdSP700.put("hardware_revision", "REV-A");
        vpdSP700.put("manufacturer", "Toshiba Global Commerce Solutions");
        vpdSP700.put("model_number", "SurePOS 700");
        vpdSP700.put("build_date", "2024-12-10");
        vpdSP700.put("mac_address", "00:1A:2B:3C:4D:60");
        vpdSP700.put("feature_flags", "ETH,RS232,USB");
        SAMPLE_VPD_DATA.put("SP700-001-XY", vpdSP700);
        
        logger.info("  Loaded {} sample VPD records", SAMPLE_VPD_DATA.size());
    }
    
    /**
     * Helper: Validate a VPD payload structure.
     * Checks all expected fields are present and well-formed.
     */
    private Map<String, List<String>> validateVpdPayload(Map<String, Object> vpdPayload) {
        Map<String, List<String>> validationResults = new HashMap<>();
        validationResults.put("passed", new ArrayList<>());
        validationResults.put("failed", new ArrayList<>());
        
        // Step 1: Check all expected fields are present
        for (String field : EXPECTED_VPD_FIELDS) {
            if (!vpdPayload.containsKey(field)) {
                validationResults.get("failed").add("Missing field: " + field);
            } else {
                validationResults.get("passed").add("Field present: " + field);
            }
        }
        
        // Step 2: Validate each field's content
        String serial = (String) vpdPayload.get("serial_number");
        if (serial != null) {
            if (SERIAL_PATTERN.matcher(serial).matches()) {
                validationResults.get("passed").add("Serial number format: " + serial);
            } else {
                validationResults.get("failed").add("Invalid serial number: " + serial);
            }
        }
        
        String firmware = (String) vpdPayload.get("firmware_version");
        if (firmware != null) {
            if (FIRMWARE_PATTERN.matcher(firmware).matches()) {
                validationResults.get("passed").add("Firmware format: " + firmware);
            } else {
                validationResults.get("failed").add("Invalid firmware version: " + firmware);
            }
        }
        
        String mac = (String) vpdPayload.get("mac_address");
        if (mac != null) {
            if (MAC_PATTERN.matcher(mac).matches()) {
                validationResults.get("passed").add("MAC format: " + mac);
            } else {
                validationResults.get("failed").add("Invalid MAC address: " + mac);
            }
        }
        
        String buildDate = (String) vpdPayload.get("build_date");
        if (buildDate != null) {
            if (DATE_PATTERN.matcher(buildDate).matches()) {
                validationResults.get("passed").add("Build date format: " + buildDate);
            } else {
                validationResults.get("failed").add("Invalid build date: " + buildDate);
            }
        }
        
        // Step 3: Validate data types
        if (vpdPayload.containsKey("feature_flags")) {
            Object featureFlags = vpdPayload.get("feature_flags");
            if (featureFlags instanceof String) {
                validationResults.get("passed").add("Feature flags: " + featureFlags);
            } else {
                validationResults.get("failed").add("Feature flags should be a String");
            }
        }
        
        return validationResults;
    }
    
    /**
     * Test: VPD Payload Validation - Valid Data
     * 
     * Given: A valid VPD payload for a TCx 810
     * When: The payload is validated
     * Then: All expected fields should be present and well-formed
     */
    @Test(description = "Valid VPD payload passes validation", groups = {"diagnostic", "regression", "product:all"})
    public void testValidVPDPayload() {
        logger.info("Testing valid VPD payload validation...");
        
        // Get sample VPD data for TCx 810
        Map<String, Object> vpdPayload = SAMPLE_VPD_DATA.get("TCX-810-A1B2C");
        assertNotNull(vpdPayload, "VPD payload should not be null");
        
        logger.info("  VPD Payload:");
        for (Map.Entry<String, Object> entry : vpdPayload.entrySet()) {
            logger.info("    {}: {}", entry.getKey(), entry.getValue());
        }
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(vpdPayload);
        
        // Verify results
        logger.info("  Validation Results:");
        for (String passed : results.get("passed")) {
            logger.info("    ✅ {}", passed);
        }
        for (String failed : results.get("failed")) {
            logger.error("    ❌ {}", failed);
        }
        
        assertTrue(results.get("failed").isEmpty(), "There should be no validation failures");
        assertEquals(results.get("passed").size(), EXPECTED_VPD_FIELDS.size() + 4, 
            "Should have passed all field checks plus format checks");
        // +4 for the additional format checks: serial, firmware, MAC, build date
        
        logger.info("✅ Valid VPD payload test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Missing Serial Number
     * 
     * Given: A VPD payload missing the serial number
     * When: The payload is validated
     * Then: A validation failure should be reported
     */
    @Test(description = "Missing serial number fails validation", groups = {"diagnostic", "regression", "negative", "product:all"})
    public void testMissingSerialNumber() {
        logger.info("Testing missing serial number validation...");
        
        // Create payload missing serial number
        Map<String, Object> invalidVpd = new LinkedHashMap<>(SAMPLE_VPD_DATA.get("TCX-810-A1B2C"));
        invalidVpd.remove("serial_number");
        
        logger.info("  VPD Payload (missing serial_number)");
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(invalidVpd);
        
        // Verify results
        boolean foundMissingField = false;
        for (String failed : results.get("failed")) {
            logger.info("    ❌ {}", failed);
            if (failed.contains("Missing field: serial_number")) {
                foundMissingField = true;
            }
        }
        
        assertTrue(foundMissingField, "Should report missing serial_number field");
        assertFalse(results.get("failed").isEmpty(), "Should have validation failures");
        
        logger.info("✅ Missing serial number test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Invalid Serial Number Format
     * 
     * Given: A VPD payload with an invalid serial number format
     * When: The payload is validated
     * Then: A validation failure should be reported
     */
    @Test(description = "Invalid serial number format fails validation", groups = {"diagnostic", "regression", "negative", "product:all"})
    public void testInvalidSerialFormat() {
        logger.info("Testing invalid serial number format...");
        
        // Create payload with invalid serial number
        Map<String, Object> invalidVpd = new LinkedHashMap<>(SAMPLE_VPD_DATA.get("TCX-810-A1B2C"));
        invalidVpd.put("serial_number", "INVALID-SERIAL-123");
        
        logger.info("  Invalid serial number: {}", invalidVpd.get("serial_number"));
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(invalidVpd);
        
        // Verify results
        boolean foundInvalidFormat = false;
        for (String failed : results.get("failed")) {
            logger.info("    ❌ {}", failed);
            if (failed.contains("Invalid serial number")) {
                foundInvalidFormat = true;
            }
        }
        
        assertTrue(foundInvalidFormat, "Should report invalid serial number format");
        assertFalse(results.get("failed").isEmpty(), "Should have validation failures");
        
        logger.info("✅ Invalid serial number test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Invalid Firmware Version
     * 
     * Given: A VPD payload with an invalid firmware version
     * When: The payload is validated
     * Then: A validation failure should be reported
     */
    @Test(description = "Invalid firmware version fails validation", groups = {"diagnostic", "regression", "negative", "product:all"})
    public void testInvalidFirmwareVersion() {
        logger.info("Testing invalid firmware version...");
        
        // Create payload with invalid firmware
        Map<String, Object> invalidVpd = new LinkedHashMap<>(SAMPLE_VPD_DATA.get("TCX-810-A1B2C"));
        invalidVpd.put("firmware_version", "3.2.1");  // Missing 'v' prefix
        
        logger.info("  Invalid firmware version: {}", invalidVpd.get("firmware_version"));
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(invalidVpd);
        
        // Verify results
        boolean foundInvalidFormat = false;
        for (String failed : results.get("failed")) {
            logger.info("    ❌ {}", failed);
            if (failed.contains("Invalid firmware version")) {
                foundInvalidFormat = true;
            }
        }
        
        assertTrue(foundInvalidFormat, "Should report invalid firmware version");
        assertFalse(results.get("failed").isEmpty(), "Should have validation failures");
        
        logger.info("✅ Invalid firmware version test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Invalid MAC Address
     * 
     * Given: A VPD payload with an invalid MAC address
     * When: The payload is validated
     * Then: A validation failure should be reported
     */
    @Test(description = "Invalid MAC address fails validation", groups = {"diagnostic", "regression", "negative", "product:all"})
    public void testInvalidMACAddress() {
        logger.info("Testing invalid MAC address...");
        
        // Create payload with invalid MAC
        Map<String, Object> invalidVpd = new LinkedHashMap<>(SAMPLE_VPD_DATA.get("TCX-810-A1B2C"));
        invalidVpd.put("mac_address", "00:1A:2B:3C:4D");  // Missing one octet
        
        logger.info("  Invalid MAC address: {}", invalidVpd.get("mac_address"));
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(invalidVpd);
        
        // Verify results
        boolean foundInvalidFormat = false;
        for (String failed : results.get("failed")) {
            logger.info("    ❌ {}", failed);
            if (failed.contains("Invalid MAC address")) {
                foundInvalidFormat = true;
            }
        }
        
        assertTrue(foundInvalidFormat, "Should report invalid MAC address");
        assertFalse(results.get("failed").isEmpty(), "Should have validation failures");
        
        logger.info("✅ Invalid MAC address test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Invalid Build Date
     * 
     * Given: A VPD payload with an invalid build date
     * When: The payload is validated
     * Then: A validation failure should be reported
     */
    @Test(description = "Invalid build date fails validation")
    public void testInvalidBuildDate() {
        logger.info("Testing invalid build date...");
        
        // Create payload with invalid build date
        Map<String, Object> invalidVpd = new LinkedHashMap<>(SAMPLE_VPD_DATA.get("TCX-810-A1B2C"));
        invalidVpd.put("build_date", "2025/11/15");  // Wrong format (should be YYYY-MM-DD)
        
        logger.info("  Invalid build date: {}", invalidVpd.get("build_date"));
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(invalidVpd);
        
        // Verify results
        boolean foundInvalidFormat = false;
        for (String failed : results.get("failed")) {
            logger.info("    ❌ {}", failed);
            if (failed.contains("Invalid build date")) {
                foundInvalidFormat = true;
            }
        }
        
        assertTrue(foundInvalidFormat, "Should report invalid build date");
        assertFalse(results.get("failed").isEmpty(), "Should have validation failures");
        
        logger.info("✅ Invalid build date test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - All TCx Models
     * 
     * Given: VPD payloads for all supported TCx models
     * When: Each payload is validated
     * Then: All should pass validation
     */
    @Test(description = "All TCx model VPD payloads pass validation")
    public void testAllTCxModels() {
        logger.info("Testing VPD validation for all TCx models...");
        
        int totalPassed = 0;
        int totalFailed = 0;
        
        for (Map.Entry<String, Map<String, Object>> entry : SAMPLE_VPD_DATA.entrySet()) {
            String serial = entry.getKey();
            Map<String, Object> vpdPayload = entry.getValue();
            
            logger.info("  Validating VPD for: {}", serial);
            
            Map<String, List<String>> results = validateVpdPayload(vpdPayload);
            
            if (results.get("failed").isEmpty()) {
                totalPassed++;
                logger.info("    ✅ {}: PASSED", serial);
            } else {
                totalFailed++;
                logger.info("    ❌ {}: FAILED", serial);
                for (String failed : results.get("failed")) {
                    logger.info("       - {}", failed);
                }
            }
        }
        
        logger.info("  Total passed: {}, Total failed: {}", totalPassed, totalFailed);
        
        assertEquals(totalFailed, 0, "All VPD payloads should pass validation");
        assertEquals(totalPassed, SAMPLE_VPD_DATA.size(), "All VPD payloads should be validated");
        
        logger.info("✅ All TCx models test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - Feature Flags Parsing
     * 
     * Given: A VPD payload with feature flags
     * When: The feature flags are parsed
     * Then: They should be correctly interpreted as a set of capabilities
     */
    @Test(description = "Feature flags are correctly parsed", groups = {"diagnostic", "regression", "product:all"})
    public void testFeatureFlagsParsing() {
        logger.info("Testing feature flags parsing...");
        
        Map<String, Object> vpdPayload = SAMPLE_VPD_DATA.get("TCX-810-A1B2C");
        String featureFlags = (String) vpdPayload.get("feature_flags");
        
        assertNotNull(featureFlags, "Feature flags should be present");
        logger.info("  Feature flags: {}", featureFlags);
        
        // Parse feature flags as comma-separated values
        Set<String> flags = new HashSet<>(Arrays.asList(featureFlags.split(",")));
        
        // Expected features for TCx 810
        Set<String> expectedFeatures = new HashSet<>(Arrays.asList("WLAN", "BT", "USB", "ETH", "RS232"));
        
        logger.info("  Parsed flags: {}", flags);
        
        // Check each expected feature
        for (String expected : expectedFeatures) {
            assertTrue(flags.contains(expected), "Feature flag should contain: " + expected);
            logger.info("    ✅ Contains: {}", expected);
        }
        
        // Check for unexpected features (should only contain expected)
        for (String flag : flags) {
            assertTrue(expectedFeatures.contains(flag.trim()), "Unexpected feature flag: " + flag);
        }
        
        logger.info("✅ Feature flags parsing test passed!");
    }
    
    /**
     * Test: VPD Payload Validation - From JSON Fixture
     * 
     * Given: A VPD payload loaded from a JSON fixture (simulated)
     * When: The payload is validated
     * Then: All fields should be present and well-formed
     * 
     * This demonstrates the "real-machine-less evaluation" approach
     */
    @Test(description = "VPD payload from JSON fixture passes validation", groups = {"diagnostic", "regression", "product:all"})
    public void testFromJsonFixture() {
        logger.info("Testing VPD validation from JSON fixture...");
        
        // Simulate JSON fixture loading (in real test, this would load from a JSON file)
        // Here we use the sample data as a stand-in
        Map<String, Object> jsonFixture = SAMPLE_VPD_DATA.get("TCX-700-F9G8H");
        
        logger.info("  JSON Fixture VPD:");
        for (Map.Entry<String, Object> entry : jsonFixture.entrySet()) {
            logger.info("    {}: {}", entry.getKey(), entry.getValue());
        }
        
        // Validate the payload
        Map<String, List<String>> results = validateVpdPayload(jsonFixture);
        
        // Verify results
        for (String passed : results.get("passed")) {
            logger.info("    ✅ {}", passed);
        }
        for (String failed : results.get("failed")) {
            logger.error("    ❌ {}", failed);
        }
        
        assertTrue(results.get("failed").isEmpty(), "JSON fixture should pass validation");
        assertEquals(results.get("passed").size(), EXPECTED_VPD_FIELDS.size() + 4,
            "Should have passed all field checks plus format checks");
        
        logger.info("✅ JSON fixture test passed!");
    }
}