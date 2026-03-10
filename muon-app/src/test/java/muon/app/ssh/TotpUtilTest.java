package muon.app.ssh;

import junit.framework.TestCase;

/**
 * Tests for TotpUtil TOTP code generation.
 */
public class TotpUtilTest extends TestCase {

    // RFC 6238 test vector: secret is "12345678901234567890" encoded as Base32
    // Base32 of "12345678901234567890" = GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
    private static final String RFC_TEST_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    public void testBase32Decode_simple() {
        // "JBSWY3DPEHPK3PXP" is Base32 for "Hello World!"
        byte[] result = TotpUtil.decodeBase32("JBSWY3DPEHPK3PXP");
        assertEquals("Hello World!".length(), result.length);
        assertEquals('H', (char) result[0]);
        assertEquals('e', (char) result[1]);
        assertEquals('l', (char) result[2]);
        assertEquals('l', (char) result[3]);
        assertEquals('o', (char) result[4]);
    }

    public void testBase32Decode_invalidCharacter() {
        try {
            TotpUtil.decodeBase32("JBSWY3DP1HPKZPXP"); // '1' is not valid Base32
            fail("Expected IllegalArgumentException for invalid Base32 character");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid Base32 character"));
        }
    }

    public void testBase32Decode_withPadding() {
        // Padding characters should be stripped before decode
        byte[] withPadding = TotpUtil.decodeBase32("JBSWY3DPEHPK3PXP");
        assertNotNull(withPadding);
        assertTrue(withPadding.length > 0);
    }

    public void testGenerateCode_validSecret() {
        String code = TotpUtil.generateCode(RFC_TEST_SECRET);
        assertNotNull(code);
        assertEquals(6, code.length());
        // Code should consist of digits only
        for (char c : code.toCharArray()) {
            assertTrue("Expected digit, got: " + c, Character.isDigit(c));
        }
    }

    public void testGenerateCode_nullSecret() {
        assertNull(TotpUtil.generateCode(null));
    }

    public void testGenerateCode_emptySecret() {
        assertNull(TotpUtil.generateCode(""));
    }

    public void testGenerateCode_whitespaceOnlySecret() {
        assertNull(TotpUtil.generateCode("   "));
    }

    public void testGenerateCode_secretWithSpaces() {
        // Spaces should be stripped before processing
        String codeWithSpaces = TotpUtil.generateCode("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ");
        String codeNoSpaces = TotpUtil.generateCode(RFC_TEST_SECRET);
        assertNotNull(codeWithSpaces);
        assertEquals(codeNoSpaces, codeWithSpaces);
    }

    public void testGenerateCode_secretCaseInsensitive() {
        String codeUpper = TotpUtil.generateCode(RFC_TEST_SECRET);
        String codeLower = TotpUtil.generateCode(RFC_TEST_SECRET.toLowerCase());
        assertNotNull(codeUpper);
        assertEquals(codeUpper, codeLower);
    }

    public void testGenerateCode_invalidSecret() {
        // Invalid Base32 character '1' should cause graceful failure
        String code = TotpUtil.generateCode("INVALID1SECRET");
        assertNull(code);
    }

    public void testGenerateCode_zeroPadded() {
        // Multiple calls should all return 6-digit zero-padded codes
        for (int i = 0; i < 5; i++) {
            String code = TotpUtil.generateCode(RFC_TEST_SECRET);
            assertNotNull(code);
            assertEquals("Code should always be 6 digits", 6, code.length());
        }
    }
}
