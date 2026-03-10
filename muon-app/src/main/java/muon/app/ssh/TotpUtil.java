package muon.app.ssh;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * Utility class for generating TOTP (Time-based One-Time Password) codes as
 * defined in RFC 6238. Supports TOTP secrets encoded in Base32.
 */
@Slf4j
public final class TotpUtil {

    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int[] DIGITS_POWER = {1, 10, 100, 1000, 10000, 100000, 1000000};

    private TotpUtil() {
    }

    /**
     * Generates a TOTP code for the given Base32-encoded secret using the current time.
     *
     * @param base32Secret the TOTP secret in Base32 encoding (case-insensitive, spaces allowed)
     * @return the generated TOTP code as a zero-padded string, or {@code null} if the secret is invalid
     */
    public static String generateCode(String base32Secret) {
        if (base32Secret == null || base32Secret.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] key = decodeBase32(base32Secret.replaceAll("\\s", "").toUpperCase());
            long counter = System.currentTimeMillis() / 1000 / TOTP_PERIOD_SECONDS;
            return generateHotp(key, counter);
        } catch (Exception e) {
            log.warn("Failed to generate TOTP code: {}", e.getMessage());
            return null;
        }
    }

    private static String generateHotp(byte[] key, long counter) throws Exception {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % DIGITS_POWER[TOTP_DIGITS];
        return String.format("%0" + TOTP_DIGITS + "d", otp);
    }

    /**
     * Decodes a Base32-encoded string into a byte array.
     * Supports the standard Base32 alphabet (RFC 4648): A-Z and 2-7.
     *
     * @param base32 the Base32-encoded string (uppercase, no spaces, padding optional)
     * @return the decoded bytes
     * @throws IllegalArgumentException if the string contains invalid Base32 characters
     */
    static byte[] decodeBase32(String base32) {
        String input = base32.replaceAll("=", "");
        int outputLength = input.length() * 5 / 8;
        byte[] output = new byte[outputLength];

        int buffer = 0;
        int bitsLeft = 0;
        int outputIndex = 0;

        for (char c : input.toCharArray()) {
            buffer <<= 5;
            if (c >= 'A' && c <= 'Z') {
                buffer |= (c - 'A');
            } else if (c >= '2' && c <= '7') {
                buffer |= (c - '2' + 26);
            } else {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[outputIndex++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return output;
    }
}
