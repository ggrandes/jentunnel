package org.javastack.jentunnel.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.SessionContext;
import org.javastack.jentunnel.KeyPairUtils;
import org.javastack.jentunnel.SSHClient;
import org.javastack.jentunnel.gui.WindowedGUI.UnpaddedTitledBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyUploadGUI extends JDialog {
	private static final long serialVersionUID = 42L;
	private static final Logger log = LoggerFactory.getLogger(KeyUploadGUI.class);
	private static final int V_SPACE = 5;

	public KeyUploadGUI(final Frame parent, final SSHClient client, final String id) {
		// Modal
		super(parent, "Authorize Key in Selected Host", true);
		setIconImage(Resources.keyIcon.getImage());
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		EscapeHandler.installEscapeCloseOperation(this);
		//
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		//
		JPanel keyFilePanel = makeHeaderPanel("Private Key File");
		JPanel filePanel = new JPanel();
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.LINE_AXIS));
		JTextField keyFile = new JTextField(20);
		filePanel.add(keyFile);
		JButton openButton = new JButton();
		openButton.setToolTipText("Select key file");
		openButton.setIcon(UIManager.getIcon("Tree.openIcon"));
		openButton.setMnemonic(KeyEvent.VK_O);
		openButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(openButton.getToolTipText());
			chooser.setMultiSelectionEnabled(false);
			final File directory = new File(GlobalSettings.CONFIG_DIRECTORY.get());
			chooser.setCurrentDirectory(directory);
			int returnVal = chooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				keyFile.setText(absoluteFile(chooser.getSelectedFile()));
			}
		});
		filePanel.add(openButton);
		keyFilePanel.add(filePanel);
		panel.add(keyFilePanel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		//
		String commentTip = "That comment will be used to upload the public key to remote host";
		JPanel commentPanel = makeHeaderPanel("Comment");
		JTextField comment = new JTextField(20);
		comment.putClientProperty("JTextField.placeholderText", "Optional");
		comment.setToolTipText(commentTip);
		commentPanel.add(comment);
		panel.add(commentPanel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		//
		JPanel buttons = new JPanel() {
			private static final long serialVersionUID = 42L;

			{
				setLayout(new FlowLayout(FlowLayout.CENTER));
				//
				JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
				add(ok);
				JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
				add(cancel);
				//
				ok.addActionListener(e -> {
					if (keyFile.getText().isEmpty() || !new File(keyFile.getText()).isFile()) {
						keyFile.putClientProperty("JComponent.outline", "error");
						keyFile.requestFocusInWindow();
						return;
					}
					try {
						final File file = new File(keyFile.getText());
						final FilePasswordProvider currentPassProvider = new AskUserGUI() {
							@Override
							public ResourceDecodeResult handleDecodeAttemptResult(
									final SessionContext session, final NamedResource resourceKey,
									final int retryIndex, final String password, final Exception err)
									throws IOException, GeneralSecurityException {
								if (err != null) {
									log.error("Unable to decode privateKey {}: {}", resourceKey,
											String.valueOf(err));
									if (retryIndex < 3) {
										SwingUtilities.invokeLater(() -> {
											showMessage("Unable to decode Private Key.\n" //
													+ "Are you using the correct passphrase?", true);
										});
										return ResourceDecodeResult.RETRY;
									}
								}
								return ResourceDecodeResult.TERMINATE;
							}
						};
						final KeyPair kp = KeyPairUtils.loadKeyPair(file.toPath(), currentPassProvider);
						if (kp == null) {
							log.error("Unable to load Private Key: {}", file);
							SwingUtilities.invokeLater(() -> {
								showMessage("Unable to load Private Key", false);
							});
							return;
						} else {
							log.info("Private Key loaded: {}", file);
						}
						if (client.authorizePublicKey(id, kp.getPublic(), comment.getText())) {
							log.info("Key \"{}\" Authorized", file.getName());
							SwingUtilities.invokeLater(() -> {
								dispose();
								showMessage("Key Authorized", true);
							});
						} else {
							log.error("Unable to Authorize key");
							SwingUtilities.invokeLater(() -> {
								showMessage("Unable to Authorize key", false);
							});
						}
					} catch (GeneralSecurityException | IOException ex) {
						log.error("Error Authorizing key: {}", String.valueOf(ex));
						SwingUtilities.invokeLater(() -> {
							showMessage("Error Authorizing key:\n" + ex, false);
						});
					}
				});
				cancel.addActionListener(e -> dispose());
				//
				KeyUploadGUI.this.getRootPane().setDefaultButton(ok); // Default on INTRO
			}
		};
		panel.add(buttons);
		JPanel padded = new JPanel();
		padded.add(panel);
		getContentPane().add(padded);
		// Prepare window.
		pack();
		setLocationRelativeTo(null);
		setResizable(false);
	}

	public void init() {
		// Display the window.
		setVisible(true);
	}

	private String absoluteFile(final File f) {
		final String nativeSeparator = FileSystems.getDefault().getSeparator();
		final String absolute = f.toPath().toAbsolutePath().toString();
		return ("/".equals(nativeSeparator) ? absolute : absolute.replace(nativeSeparator, "/"));
	}

	private final void showMessage(final String text, final boolean isOk) {
		JDialog phantom = new JDialog() {
			private static final long serialVersionUID = 42L;

			{
				setIconImage(Resources.keyIcon.getImage());
			}
		};
		try {
			JOptionPane.showMessageDialog(phantom, //
					text, //
					KeyUploadGUI.this.getTitle(), //
					isOk //
							? JOptionPane.INFORMATION_MESSAGE //
							: JOptionPane.ERROR_MESSAGE);
		} finally {
			phantom.dispose();
		}
	}

	private JPanel makeHeaderPanel(final String text) {
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setBorder(new UnpaddedTitledBorder(text));
		return panel;
	}
}
