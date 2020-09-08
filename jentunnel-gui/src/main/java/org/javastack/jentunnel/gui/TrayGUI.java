package org.javastack.jentunnel.gui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.javastack.jentunnel.SSHClient;
import org.javastack.jentunnel.SSHClient.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html
public class TrayGUI {
	private static final Logger log = LoggerFactory.getLogger(TrayGUI.class);

	private final SSHClient client;

	private final ArrayBlockingQueue<Event> q = new ArrayBlockingQueue<Event>(256);
	private final AtomicReference<WindowedGUI> windowGUI = new AtomicReference<WindowedGUI>();
	private final AtomicReference<LoggingGUI> loggingGUI = new AtomicReference<LoggingGUI>();

	private TrayIcon trayIcon = null;

	TrayGUI(final SSHClient client) {
		this.client = client;
	}

	private Event getEvent() {
		try {
			final Event e = q.poll(250, TimeUnit.MILLISECONDS);
			if (e == null) {
				return Event.NOP;
			}
			return e;
		} catch (InterruptedException e) {
			return Event.EXIT;
		}
	}

	public void init() {
		trayIcon = new TrayIcon(Resources.iconGrey);
		trayIcon.setImageAutoSize(true);
		trayIcon.setToolTip(Resources.appInfo.iam);
		//
		MenuItem connectItem = new MenuItem("Connect/Reconnect");
		MenuItem disconnectItem = new MenuItem("Disconnect");
		MenuItem loggingItem = new MenuItem("Logs");
		MenuItem configItem = new MenuItem("Configure");
		MenuItem aboutItem = new MenuItem("About");
		MenuItem exitItem = new MenuItem("Exit");
		//
		final PopupMenu popup = new PopupMenu();
		popup.add(connectItem);
		popup.add(disconnectItem);
		popup.addSeparator();
		popup.add(loggingItem);
		popup.add(configItem);
		popup.addSeparator();
		popup.add(aboutItem);
		popup.add(exitItem);
		//
		trayIcon.setPopupMenu(popup);
		//
		final SystemTray tray = SystemTray.getSystemTray();
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			System.out.println("TrayIcon could not be added.");
			q.add(Event.EXIT);
			return;
		}
		//
		trayIcon.addActionListener(e -> openWindowedGUI(windowGUI, configItem, client));
		client.addNotify(new SSHClient.Notify() {
			private final int DEFAULT_DECAY_MILLIS = 3000;
			private volatile Timer timer = null;

			@Override
			public void notifyConnecting(final Session session) {
				changeIcon(Resources.iconYellow, DEFAULT_DECAY_MILLIS * 2);
			}

			@Override
			public void notifyEstablished(final Session session) {
				changeIcon(Resources.iconGreen, DEFAULT_DECAY_MILLIS);
			}

			@Override
			public void notifyFail(final Session session) {
				changeIcon(Resources.iconRed, DEFAULT_DECAY_MILLIS * 3);
			}

			@Override
			public void notifyClosed(final Session session) {
				changeIcon(Resources.iconGrey, DEFAULT_DECAY_MILLIS);
			}

			private void changeIcon(final Image img, final int decayMillis) {
				SwingUtilities.invokeLater(() -> {
					trayIcon.setImage(img);
				});
				if (timer == null) {
					timer = new Timer(decayMillis, (e) -> {
						SwingUtilities.invokeLater(() -> {
							log.debug("Fired timer: {}", timer);
							trayIcon.setImage(Resources.iconGrey);
						});
					});
					timer.setRepeats(false);
					timer.start();
					log.debug("New timer: {}", timer);
				} else {
					timer.setDelay(decayMillis);
					timer.restart();
					log.debug("Restart timer: {}", timer);
				}
			}
		});
		//
		connectItem.addActionListener(e -> q.add(Event.CONNECT));
		disconnectItem.addActionListener(e -> q.add(Event.DISCONNECT));
		configItem.addActionListener(e -> openWindowedGUI(windowGUI, configItem, client));
		loggingItem.addActionListener(e -> openLoggingGUI(loggingGUI, loggingItem));
		//
		aboutItem.addActionListener(e -> {
			final String iam = Resources.appInfo.iam;
			final String groupId = Resources.appInfo.groupId;
			final String artifactId = Resources.appInfo.artifactId;
			final String version = Resources.appInfo.version;
			final String url = Resources.appInfo.url;
			final String title = "About " + iam + " " + version;
			final String mavenCoordinates = groupId + ":" + artifactId + ":" + version;
			final MessageWithLink mLink = new MessageWithLink(url);
			final JOptionPane jop = new JOptionPane();
			jop.setMessageType(JOptionPane.INFORMATION_MESSAGE);
			jop.setMessage(new Object[] {
					mavenCoordinates, //
					mLink
			});
			final JDialog dialog = jop.createDialog(null, title);
			mLink.setParentDialog(dialog);
			dialog.setIconImage(Resources.mainIcon);
			dialog.setVisible(true);
		});
		//
		exitItem.addActionListener(e -> {
			tray.remove(trayIcon);
			q.add(Event.DISCONNECT);
			q.add(Event.EXIT);
		});
		//
		if (client.getConnections().isEmpty()) {
			SwingUtilities.invokeLater(() -> {
				openWindowedGUI(windowGUI, configItem, client);
			});
		}
	}

	private void openWindowedGUI(final AtomicReference<WindowedGUI> windowGUI, //
			final MenuItem configItem, //
			final SSHClient client) {
		WindowedGUI w = windowGUI.get();
		if (w == null) {
			configItem.setEnabled(false);
			w = new WindowedGUI(client, true);
			windowGUI.set(w);
			w.addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					configItem.setEnabled(true);
					windowGUI.set(null);
				}
			});
			w.init();
		} else {
			w.requestFocusInWindow();
		}
	}

	private void openLoggingGUI(final AtomicReference<LoggingGUI> loggingGUI, //
			final MenuItem loggingItem) {
		LoggingGUI w = loggingGUI.get();
		if (w == null) {
			loggingItem.setEnabled(false);
			w = new LoggingGUI();
			loggingGUI.set(w);
			w.init();
			w.addWindowListener(new WindowAdapter() {
				public void windowClosed(WindowEvent e) {
					loggingItem.setEnabled(true);
					loggingGUI.set(null);
				}
			});
		} else {
			w.requestFocusInWindow();
		}
	}
	
	private static class MessageWithLink extends JEditorPane {
		private static final long serialVersionUID = 42L;
		private JDialog parent = null;

		public MessageWithLink(final String link) {
			super("text/html", "<html><body style=\"" + getStyle() + "\"><a href=\\\"" + link + "\\\">" //
					+ link + "</a></body></html>");
			addHyperlinkListener(new HyperlinkListener() {
				@Override
				public void hyperlinkUpdate(final HyperlinkEvent event) {
					if (event.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
						try {
							if (parent != null) {
								parent.dispose();
							}
							Desktop.getDesktop().browse(new URI(link));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			});
			setEditable(false);
			setBorder(null);
		}

		public void setParentDialog(final JDialog parent) {
			this.parent = parent;
		}

		private static StringBuilder getStyle() {
			// for copying style
			final JLabel label = new JLabel();
			final Font font = label.getFont();
			final Color color = label.getBackground();

			// create some css from the label's font
			final StringBuilder style = new StringBuilder("font-family:" + font.getFamily() + ";");
			style.append("font-weight:" + (font.isBold() ? "bold" : "normal") + ";");
			style.append("font-size:" + font.getSize() + "pt;");
			style.append("background-color: rgb(" + color.getRed() + "," + color.getGreen() + ","
					+ color.getBlue() + ");");
			return style;
		}
	}

	private static enum Event {
		NOP, //
		CONNECT, //
		DISCONNECT, //
		EXIT //
	}

	public void handleEvents() {
		Event evt = null;
		while ((evt = getEvent()) != Event.EXIT) {
			switch (evt) {
				case NOP:
					// client.resetIdle();
					break;
				case CONNECT:
					client.stop();
					client.start();
					break;
				case DISCONNECT:
					client.stop();
					break;
				default:
					break;
			}
		}
	}
}
