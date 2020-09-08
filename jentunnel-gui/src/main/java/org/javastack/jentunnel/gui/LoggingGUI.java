package org.javastack.jentunnel.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class LoggingGUI extends JFrame {
	private static final long serialVersionUID = 42L;
	private static final int MAIN_MINIMAL_WIDTH = 540;
	private static final int MAIN_MINIMAL_HEIGHT = 250;
	private static final int MAIN_DEFAULT_WIDTH = 1000;
	private static final int MAIN_DEFAULT_HEIGHT = 700;

	private WatchablePrintStream logs = null;

	public LoggingGUI() {
		super("Logs");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		// Load icons
		setIconImages(Resources.mainIcons);
		// Set Main Window Dimension
		setMinimumSize(new Dimension(MAIN_MINIMAL_WIDTH, MAIN_MINIMAL_HEIGHT));
		setPreferredSize(new Dimension(MAIN_DEFAULT_WIDTH, MAIN_DEFAULT_HEIGHT));
		// Set content
		this.logs = WatchablePrintStream.getSystemWatchablePrintStream();
		add(logs == null ? makeTextPanel("No logging") : makeLoggingPanel(), BorderLayout.CENTER);
		// Register cleanup
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				if (logs != null) {
					logs.unwatchEvents();
					logs = null;
				}
			}
		});
		EscapeHandler.installEscapeCloseOperation(this);
		// Prepare window.
		pack();
		setLocationRelativeTo(null);
	}

	public void init() {
		// Display the window.
		setVisible(true);
	}

	private JPanel makeTextPanel(final String text) {
		JPanel panel = new JPanel();
		JLabel filler = new JLabel(text);
		filler.setHorizontalAlignment(JLabel.CENTER);
		panel.setLayout(new GridLayout(1, 1));
		panel.add(filler);
		return panel;
	}

	private JScrollPane makeLoggingPanel() {
		JTextArea text = new JTextArea(10, 20);
		text.setEditable(false);
		text.setLineWrap(true);
		//
		JScrollPane panel = new JScrollPane(text);
		panel.setBorder(new EmptyBorder(0, 0, 0, 0));
		//
		if (logs != null) {
			for (final String event : logs.getQueuedEvents()) {
				text.append(event);
				text.append("\n");
			}
			final JScrollBar vertical = panel.getVerticalScrollBar();
			logs.watchEvents(event -> {
				SwingUtilities.invokeLater(() -> {
					text.append(event);
					text.append("\n");
					vertical.setValue(vertical.getMaximum());
				});
			});
		}
		return panel;
	}
}
