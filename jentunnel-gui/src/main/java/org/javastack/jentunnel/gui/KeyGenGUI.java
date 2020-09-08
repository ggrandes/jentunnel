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
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.bouncycastle.util.Arrays;
import org.javastack.jentunnel.KeyPairUtils;
import org.javastack.jentunnel.gui.WindowedGUI.UnpaddedTitledBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyGenGUI extends JDialog {
	private static final long serialVersionUID = 42L;
	private static final Logger log = LoggerFactory.getLogger(KeyGenGUI.class);
	private static final int V_SPACE = 5;

	public KeyGenGUI(final Frame parent) {
		// Modal
		super(parent, "KeyPair Generator", true);
		setIconImage(Resources.keyIcon.getImage());
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		EscapeHandler.installEscapeCloseOperation(this);
		//
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		//
		JPanel keyDirPanel = makeHeaderPanel("Private Key Directory");
		JPanel filePanel = new JPanel();
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.LINE_AXIS));
		JTextField keyDir = new JTextField(20);
		filePanel.add(keyDir);
		JButton openButton = new JButton();
		openButton.setToolTipText("Select key file folder");
		openButton.setIcon(UIManager.getIcon("Tree.openIcon"));
		openButton.setMnemonic(KeyEvent.VK_O);
		openButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle(openButton.getToolTipText());
			chooser.setMultiSelectionEnabled(false);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			final File directory;
			if (!keyDir.getText().isEmpty()) {
				directory = new File(keyDir.getText());
			} else {
				directory = defaultConfigDir();
			}
			chooser.setCurrentDirectory(directory);
			int returnVal = chooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				keyDir.setText(absoluteFile(chooser.getSelectedFile()));
			}
		});
		filePanel.add(openButton);
		keyDirPanel.add(filePanel);
		panel.add(keyDirPanel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		//
		JPanel typePanel = makeHeaderPanel("Key Type");
		JComboBox<KeyPairUtils.KeyType> ktype = new JComboBox<KeyPairUtils.KeyType>(
				KeyPairUtils.KeyType.values());
		ktype.setToolTipText("Key Type");
		typePanel.add(ktype);
		panel.add(typePanel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		//
		JPanel namePanel = makeHeaderPanel("Key file name");
		JTextField name = new JTextField(20);
		name.putClientProperty("JTextField.placeholderText", "id_ed25519");
		namePanel.add(name);
		panel.add(namePanel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		//
		String passTip = "That passphrase will be used to encrypt the private part of this file using 128-bit AES";
		JPanel pass1Panel = makeHeaderPanel("Passphrase");
		JPasswordField password1 = new JPasswordField(20);
		password1.putClientProperty("JTextField.placeholderText", "Optional");
		password1.setToolTipText(passTip);
		pass1Panel.add(password1);
		panel.add(pass1Panel);
		panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
		JPanel pass2Panel = makeHeaderPanel("Repeat Passphrase");
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
					if (keyDir.getText().isEmpty() || !new File(keyDir.getText()).isDirectory()) {
						keyDir.putClientProperty("JComponent.outline", "error");
						keyDir.requestFocusInWindow();
						return;
					}
					if (name.getText().isEmpty()) {
						name.putClientProperty("JComponent.outline", "error");
						name.requestFocusInWindow();
						return;
					}
					if (!Arrays.areEqual(password1.getPassword(), password2.getPassword())) {
						password2.putClientProperty("JComponent.outline", "error");
						password1.putClientProperty("JComponent.outline", "error");
						password2.requestFocusInWindow();
						password1.requestFocusInWindow();
						return;
					}
					final Thread keyGenThread = new Thread(() -> {
						final SpinnerDialog spinner = new SpinnerDialog(parent);
						final Timer spinDelayer = new Timer(1000, (ee) -> {
							if (spinner.isDisplayable()) {
								spinner.init();
							}
						});
						spinDelayer.setRepeats(false);
						spinDelayer.start();
						try {
							final KeyPairUtils.KeyType keyType = (KeyPairUtils.KeyType) ktype
									.getSelectedItem();
							final File dir = new File(keyDir.getText());
							final String file = name.getText();
							final String pwd = new String(password1.getPassword());
							final KeyPair kp = KeyPairUtils.generateKeyPair(keyType);
							log.info("Key pair generated: {}", keyType);
							KeyPairUtils.saveKeyPair(kp, dir, file, pwd);
							log.info("Key pair \"{}\" saved in: {}", file, dir);
							SwingUtilities.invokeLater(() -> {
								dispose();
								showMessage("Key pair generated", true);
							});
						} catch (GeneralSecurityException | IOException ex) {
							log.error("Error generating key pair: {}", String.valueOf(ex));
							SwingUtilities.invokeLater(() -> {
								showMessage("Error generating key pair:\n" + ex, false);
							});
						} finally {
							spinDelayer.stop();
							SwingUtilities.invokeLater(() -> {
								spinner.destroy();
							});
						}
					});
					keyGenThread.start();
				});
				cancel.addActionListener(e -> dispose());
				//
				KeyGenGUI.this.getRootPane().setDefaultButton(ok); // Default on INTRO
			}
		};
		panel.add(buttons);
		JPanel padded = new JPanel();
		padded.add(panel);
		getContentPane().add(padded);
		//
		keyDir.setText(GlobalSettings.CONFIG_DIRECTORY.get());
		ktype.setSelectedItem(KeyPairUtils.KeyType.ED25519);
		// Prepare window.
		pack();
		setLocationRelativeTo(null);
		setResizable(false);
	}

	public void init() {
		// Display the window.
		setVisible(true);
	}

	private static final File defaultConfigDir() {
		return new File(System.getProperty("user.home", "."), //
				"." + Resources.appInfo.iam.toLowerCase());
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
					KeyGenGUI.this.getTitle(), //
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
