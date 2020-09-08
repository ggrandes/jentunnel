package org.javastack.jentunnel.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.ModifiedServerKeyAcceptor;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.session.SessionContext;
import org.javastack.jentunnel.Identity;
import org.javastack.jentunnel.PasswordEncoder;
import org.javastack.jentunnel.SSHClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AskUserGUI implements FilePasswordProvider, ServerKeyVerifier, ModifiedServerKeyAcceptor {
	private static final Logger log = LoggerFactory.getLogger(AskUserGUI.class);
	private static final int V_SPACE = 5;

	@Override
	public String getPassword(final SessionContext session, final NamedResource resourceKey,
			final int retryIndex) throws IOException {
		final Identity identity = ((session == null) ? null //
				: (Identity) session.getObject(SSHClient.IDENTITY_PROP_ID));
		if ((identity != null) && (identity.cacheIdentityPrivateKeyPassword() != null) //
				&& (retryIndex < 1)) {
			log.info("Decoding privateKey {}: with cached identity: {}", resourceKey, identity.alias);
			return PasswordEncoder.decode(identity.cacheIdentityPrivateKeyPassword());
		}
		JDialog phantom = getPhantom();
		try {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			//
			String text = "Enter a passphrase to decrypt your private key for";
			JTextArea textArea = new JTextArea((session == null) //
					? String.format("%s\n%s", text, resourceKey) //
					: String.format("%s\n%s@%s", text, session.getUsername(), session.getRemoteAddress()));
			textArea.setEnabled(false);
			panel.add(textArea);
			panel.add(Box.createRigidArea(new Dimension(5, V_SPACE * 3)));
			//
			JPasswordField password = new JPasswordField(20);
			panel.add(password);
			panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
			//
			Timer timer = new Timer(100, (e) -> {
				SwingUtilities.invokeLater(() -> {
					// https://stackoverflow.com/a/8881370/1450967
					password.requestFocusInWindow();
				});
			});
			timer.setRepeats(false);
			timer.start();
			final String[] options = {
					UIManager.getString("OptionPane.okButtonText"), //
					UIManager.getString("OptionPane.cancelButtonText")
			};
			final String defaultOption = UIManager.getString("OptionPane.okButtonText");
			final int response = JOptionPane.showOptionDialog(phantom, panel, "Password needed",
					JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, defaultOption);
			if (response == JOptionPane.OK_OPTION) {
				return new String(password.getPassword());
			}
		} catch (Exception e) {
			log.error("Error getting password for {}: {}", resourceKey, String.valueOf(e), e);
		} finally {
			phantom.dispose();
		}
		return null;
	}

	@Override
	public ResourceDecodeResult handleDecodeAttemptResult(final SessionContext session,
			final NamedResource resourceKey, final int retryIndex, final String password, final Exception err)
			throws IOException, GeneralSecurityException {
		if (err != null) {
			log.error("Unable to decode privateKey {}: {}", resourceKey, String.valueOf(err));
			if (retryIndex < 3) {
				return ResourceDecodeResult.RETRY;
			}
		} else {
			// TODO: Cache password for next times
			final Identity identity = (session == null) //
					? null //
					: (Identity) session.getObject(SSHClient.IDENTITY_PROP_ID);
			log.info("Decoded privateKey {}: identity: {}", resourceKey,
					(identity == null ? null : identity.alias));
			if (identity != null) {
				identity.cacheIdentityPrivateKeyPassword(PasswordEncoder.encode(password));
			}
		}
		return ResourceDecodeResult.TERMINATE;
	}

	@Override
	public boolean verifyServerKey(final ClientSession clientSession, final SocketAddress remoteAddress,
			final PublicKey serverKey) {
		JDialog phantom = getPhantom();
		try {
			final String body = String.format("Unknown server %s publickey\n" //
					+ "- - -\n" //
					+ "It is recommended you verify your host key before accepting.\n\n" //
					+ "New server's host key (%s, %sbits) fingerprint:\n\n" //
					+ "%s\n%s\n%s\n", //
					remoteAddress, //
					KeyUtils.getKeyType(serverKey), //
					KeyUtils.getKeySize(serverKey), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha256, serverKey), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha1, serverKey), //
					KeyUtils.getFingerPrint(BuiltinDigests.md5, serverKey));
			final int response = showConfirmDialog(phantom, //
					body, "Unknown Host Key (new)");
			if (response == JOptionPane.OK_OPTION) {
				return true;
			}
		} catch (Exception e) {
			log.error("Error verifing new ServerKey ({}): {}", remoteAddress, String.valueOf(e), e);
		} finally {
			phantom.dispose();
		}
		return false;
	}

	@Override
	public boolean acceptModifiedServerKey(final ClientSession clientSession,
			final SocketAddress remoteAddress, final KnownHostEntry entry, final PublicKey expected,
			final PublicKey actual) throws Exception {
		JDialog phantom = getPhantom();
		try {
			final String body = String.format("Known server %s changed publickey\n\n" //
					+ "Last known server's host key (%s, %sbits) fingerprint:\n\n" //
					+ "%s\n%s\n%s\n" //
					+ "- - -\n" //
					+ "It is recommended you verify your host key before accepting.\n\n" //
					+ "New server's host key (%s, %sbits) fingerprint:\n\n" //
					+ "%s\n%s\n%s\n\n", //
					remoteAddress, //
					KeyUtils.getKeyType(expected), //
					KeyUtils.getKeySize(expected), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha256, expected), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha1, expected), //
					KeyUtils.getFingerPrint(BuiltinDigests.md5, expected), //
					KeyUtils.getKeyType(actual), //
					KeyUtils.getKeySize(actual), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha256, actual), //
					KeyUtils.getFingerPrint(BuiltinDigests.sha1, actual), //
					KeyUtils.getFingerPrint(BuiltinDigests.md5, actual));
			final int response = showConfirmDialog(phantom, //
					body, "Unknown Host Key (changed)");
			if (response == JOptionPane.OK_OPTION) {
				return true;
			}
		} catch (Exception e) {
			log.error("Error verifing modified ServerKey ({}): {}", remoteAddress, String.valueOf(e), e);
		} finally {
			phantom.dispose();
		}
		return false;
	}

	private static int showConfirmDialog(final Component parentComponent, final String body, String title) {
		return showConfirmDialog(parentComponent, body, title, //
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
	}

	private static int showConfirmDialog(final Component parentComponent, //
			final Object message, final String title, //
			final int optionType, final int messageType) {
		// https://stackoverflow.com/a/9314409/1450967
		final List<Object> options = new ArrayList<Object>();
		final Object defaultOption;
		options.add("Accept & Save");
		options.add(UIManager.getString("OptionPane.cancelButtonText"));
		defaultOption = UIManager.getString("OptionPane.cancelButtonText");
		return JOptionPane.showOptionDialog(parentComponent, message, title, //
				optionType, messageType, null, options.toArray(), defaultOption);
	}

	private static JDialog getPhantom() {
		return new JDialog() {
			private static final long serialVersionUID = 42L;

			{
				setIconImages(Resources.mainIcons);
			}
		};
	}
}
