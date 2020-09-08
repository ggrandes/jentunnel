package org.javastack.jentunnel.gui;

import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;

public class SpinnerDialog extends JDialog {
	private static final long serialVersionUID = 42L;
	private final Timer animationTimer;

	public SpinnerDialog(final Frame parent) {
		// Modal
		super(parent, "Please wait...", true);
		setIconImage(Resources.keyIcon.getImage());
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		//
		JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 30));
		//
		JLabel spinner = new JLabel();
		spinner.setIcon(Resources.spinnerIconBig);
		panel.add(spinner);
		//
		final int delay = 100; // milliseconds
		animationTimer = new Timer(delay, (e) -> {
			spinner.repaint();
			Resources.spinnerIconBig.nextFrame();
		});
		animationTimer.setRepeats(true);
		animationTimer.start();
		//
		getContentPane().add(panel);
		//
		// Display the window.
		pack();
		setLocationRelativeTo(null);
		setResizable(false);
	}

	public void init() {
		setVisible(true);
	}

	public void destroy() {
		animationTimer.stop();
		dispose();
	}
}