
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

    /**
     * Creates an InteractiveResponseProvider that temporarily extends SSH timeout
     * while waiting for user input in dialogs.
     *
     * @param sshClient the SSH client to manage timeout for
     * @param originalTimeout the original timeout value in milliseconds
     */
    public InteractiveResponseProvider(SSHClient sshClient, int originalTimeout) {
        this.sshClient = sshClient;
        this.originalTimeout = originalTimeout;
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
        } else {
            JPasswordField passwordField = new JPasswordField(30);
            int ret = OptionPaneUtils.showOptionDialog(null,
                                                       new Object[]{prompt, passwordField}, App.getCONTEXT().getBundle().getString("input"));
            if (ret == JOptionPane.OK_OPTION) {
                return passwordField.getPassword();
            }
        }
    }

    @Override
    public boolean shouldRetry() {
        return retry;
    }

}
