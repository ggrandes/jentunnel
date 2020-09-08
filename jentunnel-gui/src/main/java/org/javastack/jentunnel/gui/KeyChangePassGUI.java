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
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.SessionContext;
import org.bouncycastle.util.Arrays;
import org.javastack.jentunnel.KeyPairUtils;
import org.javastack.jentunnel.gui.WindowedGUI.UnpaddedTitledBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyChangePassGUI extends JDialog {
	private static final long serialVersionUID = 42L;
	private static final Logger log = LoggerFactory.getLogger(KeyChangePassGUI.class);
	private static final int V_SPACE = 5;

	public KeyChangePassGUI(final Frame parent) {
		// Modal
		super(parent, "Change Private Key Passphrase", true);
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
		String passTip = "That passphrase will be used to encrypt the private part of this file using 128-bit AES";
		JPanel pass1Panel = makeHeaderPanel("New Passphrase");
		JPasswordField password1 = new JPasswordField(20);
		password1.putClientProperty("JTextField.placeholderText", "Optional");
		password1.setToolTipText(passTip);
		pass1Panel.add(password1);
		panel.add(pass1Panel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		JPanel pass2Panel = makeHeaderPanel("Repeat new Passphrase");
		JPasswordField password2 = new JPasswordField(20);
		password2.putClientProperty("JTextField.placeholderText", "Optional");
		password2.setToolTipText(passTip);
		pass2Panel.add(password2);
		panel.add(pass2Panel);
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
					if (!Arrays.areEqual(password1.getPassword(), password2.getPassword())) {
						password2.putClientProperty("JComponent.outline", "error");
						password1.putClientProperty("JComponent.outline", "error");
						password2.requestFocusInWindow();
						password1.requestFocusInWindow();
						return;
					}
					try {
						final File file = new File(keyFile.getText());
						final String pwd = new String(password1.getPassword());
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
						KeyPairUtils.saveKeyPair(kp, file.getParentFile(), file.getName(), pwd);
						log.info("Private Key \"{}\" saved in: {}", file.getName(), file.getParentFile());
						SwingUtilities.invokeLater(() -> {
							dispose();
							showMessage("Private Key Passphrase changed", true);
						});
					} catch (GeneralSecurityException | IOException ex) {
						log.error("Error changing Passphrase: {}", String.valueOf(ex));
						SwingUtilities.invokeLater(() -> {
							showMessage("Error changing Passphrase:\n" + ex, false);
						});
					}
				});
				cancel.addActionListener(e -> dispose());
				//
				KeyChangePassGUI.this.getRootPane().setDefaultButton(ok); // Default on INTRO
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
					KeyChangePassGUI.this.getTitle(), //
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
