
package muon.app.ssh;

import lombok.extern.slf4j.Slf4j;
import muon.app.App;
import muon.app.util.OptionPaneUtils;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider;
import net.schmizz.sshj.userauth.password.Resource;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author subhro
 */
@Slf4j
public class InteractiveResponseProvider implements ChallengeResponseProvider {

    private boolean retry = true;
    private final SSHClient sshClient;
    private final int originalTimeout;
    private final String totpSecret;
    private boolean totpAttempted = false;

    /**
     * Creates an InteractiveResponseProvider that temporarily extends SSH timeout
     * while waiting for user input in dialogs.
     *
     * @param sshClient the SSH client to manage timeout for
     * @param originalTimeout the original timeout value in milliseconds
     */
    public InteractiveResponseProvider(SSHClient sshClient, int originalTimeout) {
        this(sshClient, originalTimeout, null);
    }

    /**
     * Creates an InteractiveResponseProvider with an optional TOTP secret for
     * automatic verification code responses.
     *
     * @param sshClient the SSH client to manage timeout for
     * @param originalTimeout the original timeout value in milliseconds
     * @param totpSecret the TOTP secret (Base32-encoded), or {@code null} if not configured
     */
    public InteractiveResponseProvider(SSHClient sshClient, int originalTimeout, String totpSecret) {
        this.sshClient = sshClient;
        this.originalTimeout = originalTimeout;
        this.totpSecret = totpSecret;
    }

    @Override
    public List<String> getSubmethods() {
        return Collections.emptyList();
    }

    @Override
    public void init(Resource resource, String name, String instruction) {
        log.info("ChallengeResponseProvider init - resource: {} name: {} instruction: {}", resource, name, instruction);
        if ((name != null && !name.isEmpty())
            || (instruction != null && !instruction.isEmpty())) {
            JOptionPane.showMessageDialog(null, name + "\n" + instruction);
        }
    }

    @Override
    public char[] getResponse(String prompt, boolean echo) {
        log.info("prompt: {} echo: {}", prompt, echo);

        // If a TOTP secret is configured and has not yet been attempted,
        // automatically generate the current TOTP code for verification prompts.
        if (totpSecret != null && !totpSecret.isEmpty() && !totpAttempted && isVerificationCodePrompt(prompt)) {
            totpAttempted = true;
            String code = TotpUtil.generateCode(totpSecret);
            if (code != null) {
                log.info("Auto-providing TOTP code for prompt: {}", prompt);
                return code.toCharArray();
            }
            log.warn("Failed to generate TOTP code, falling through to manual input");
        }

        // Temporarily extend timeout to 5 minutes (300 seconds) to allow user time to respond
        // This is especially important for MFA where users need to retrieve codes from devices
        final int extendedTimeout = 300000; // 5 minutes in milliseconds
        
        try {
            log.debug("Extending SSH timeout from {}ms to {}ms for user input", originalTimeout, extendedTimeout);
            sshClient.setTimeout(extendedTimeout);
        } catch (Exception e) {
            log.warn("Failed to extend SSH timeout, continuing with original timeout: {}", e.getMessage());
        }

        try {
            if (echo) {
                String str = OptionPaneUtils.showInputDialog(null, prompt, App.getCONTEXT().getBundle().getString("input"));
                if (str != null) {
                    return str.toCharArray();
                }
            } else {
                JPasswordField passwordField = new JPasswordField(30);
                int ret = OptionPaneUtils.showOptionDialog(null,
                                                           new Object[]{prompt, passwordField}, App.getCONTEXT().getBundle().getString("input"));
                if (ret == JOptionPane.OK_OPTION) {
                    return passwordField.getPassword();
                }
            }
            return null;
        } finally {
            // Restore original timeout after user has responded
            try {
                log.debug("Restoring SSH timeout to original value: {}ms", originalTimeout);
                sshClient.setTimeout(originalTimeout);
            } catch (Exception e) {
                log.warn("Failed to restore SSH timeout: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean shouldRetry() {
        return retry;
    }

    /**
     * Returns true if the given prompt text looks like a TOTP/verification code request.
     * This covers common SSH server prompt patterns for 2FA challenges.
     */
    private boolean isVerificationCodePrompt(String prompt) {
        if (prompt == null) {
            return false;
        }
        String lower = prompt.toLowerCase();
        return lower.contains("verification code")
                || lower.contains("authenticator")
                || lower.contains("otp")
                || lower.contains("one-time")
                || lower.contains("one time")
                || lower.contains("totp");
    }

}
