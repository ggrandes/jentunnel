package org.javastack.jentunnel.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.javastack.jentunnel.AliasID;
import org.javastack.jentunnel.Connection;
import org.javastack.jentunnel.ConnectionStatus;
import org.javastack.jentunnel.Forward;
import org.javastack.jentunnel.Identity;
import org.javastack.jentunnel.SSHClient;
import org.javastack.jentunnel.SSHClient.Session;
import org.javastack.jentunnel.gui.flatlaf.IJThemesPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.ui.FlatRootPaneUI;
import com.formdev.flatlaf.ui.FlatTitlePane;

public class WindowedGUI extends JFrame implements SSHClient.Notify {
	private static final long serialVersionUID = 42L;
	private static final Logger log = LoggerFactory.getLogger(WindowedGUI.class);
	private static final int MAIN_MINIMAL_WIDTH = 540;
	private static final int MAIN_MINIMAL_HEIGHT = 250;
	private static final int MAIN_DEFAULT_WIDTH = 540;
	private static final int MAIN_DEFAULT_HEIGHT = 250;
	private static final int EDIT_DEFAULT_WIDTH = 300;
	private static final int V_SPACE = 5;

	final SSHClient client;
	final boolean isTrayApp;

	private JTabbedPane tabs = null;
	private Tabs.Table selectedTable = null;
	private Timer animationTimer = null;
	private volatile boolean dirtyConnectionStatus = true;

	public WindowedGUI init() {
		if (animationTimer == null) {
			final int delay = 100; // milliseconds
			animationTimer = new Timer(delay, (e) -> {
				try {
					if ((selectedTable != null) && selectedTable.animateIcons()) {
						Resources.spinnerIcon.nextFrame();
					}
				} catch (Exception ex) {
					log.error("Exception: {}", String.valueOf(ex), ex);
				}
			});
			animationTimer.setRepeats(true);
			log.debug("New Animation Timer: {}", animationTimer);
		}
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				final Dimension size = WindowedGUI.this.getSize();
				GlobalSettings.MAIN_WINDOW_WIDTH.setInt(size.width);
				GlobalSettings.MAIN_WINDOW_HEIGHT.setInt(size.height);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				destroy();
			}
		});
		if (client != null) {
			client.addNotify(this);
		}
		// Display the window.
		setVisible(true);
		return this;
	}

	@Override
	public void notifyConnecting(final Session session) {
		log.info("Update state (connecting): {} >> {}", session.getConnectionAlias(),
				client.getStatus(session.getConnectionID()));
		dirtyConnectionStatus = true;
	}

	@Override
	public void notifyEstablished(final Session session) {
		log.info("Update state (established): {} >> {}", session.getConnectionAlias(),
				client.getStatus(session.getConnectionID()));
		dirtyConnectionStatus = true;
	}

	@Override
	public void notifyFail(final Session session) {
		log.warn("Update state (fail): {} >> {}", session.getConnectionAlias(),
				client.getStatus(session.getConnectionID()));
		dirtyConnectionStatus = true;
	}

	@Override
	public void notifyClosed(final Session session) {
		log.info("Update state (closed): {} >> {}", session.getConnectionAlias(),
				client.getStatus(session.getConnectionID()));
		dirtyConnectionStatus = true;
	}

	private void destroy() {
		if (client != null) {
			client.removeNotify(this);
		}
		if (animationTimer != null) {
			log.debug("Stop Animation Timer: {}", animationTimer);
			animationTimer.stop();
			;
			animationTimer = null;
		}
	}

	// https://docs.oracle.com/javase/tutorial/uiswing/components/index.html
	// https://docs.oracle.com/javase/tutorial/uiswing/layout/visual.html
	// https://docs.oracle.com/javase/tutorial/uiswing/examples/components/
	// https://github.com/JFormDesigner/FlatLaf
	public WindowedGUI(final SSHClient client, final boolean isTrayApp) {
		super(Resources.appInfo.iam);
		this.client = client;
		this.isTrayApp = isTrayApp;
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		// Redecorate
		getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
		customizeTitlePaneButtons();
		// Load icons
		setIconImages(Resources.mainIcons);
		// Set Main Window Dimension
		final int windowWidth = Math.max(GlobalSettings.MAIN_WINDOW_WIDTH.getInt(), MAIN_DEFAULT_WIDTH);
		final int windowHeight = Math.max(GlobalSettings.MAIN_WINDOW_HEIGHT.getInt(), MAIN_DEFAULT_HEIGHT);
		setMinimumSize(new Dimension(MAIN_MINIMAL_WIDTH, MAIN_MINIMAL_HEIGHT));
		setPreferredSize(new Dimension(windowWidth, windowHeight));
		// Install ESC handler
		if (isTrayApp) {
			EscapeHandler.installEscapeCloseOperation(this);
		}
		// Create contextual popups
		makePopups();
		// Add Main Tabs to the window.
		add(new Tabs(), BorderLayout.CENTER);
		// Add Footer (separator + buttons)
		add(makeFooter(), BorderLayout.PAGE_END);

		// Prepare window.
		pack();
		setLocationRelativeTo(null);
	}

	private final void customizeTitlePaneButtons() {
		// Use voodoo to hack buttons
		new FlatRootPaneUI() {
			@Override
			protected FlatTitlePane createTitlePane() {
				return new FlatTitlePane(rootPane) {
					private static final long serialVersionUID = 42L;
					private JButton trayButton;

					@Override
					protected void createButtons() {
						super.createButtons();
						if (isTrayApp) {
							buttonPanel.removeAll();
							trayButton = createButton("TitlePane.iconifyIcon", //
									"Minimize to Tray", e -> close());
							buttonPanel.add(trayButton);
						} else {
							buttonPanel.removeAll();
							iconifyButton = createButton("TitlePane.iconifyIcon", //
									"Minimize to Taskbar", e -> iconify());
							closeButton = createButton("TitlePane.closeIcon", //
									"Exit", e -> close());
							buttonPanel.add(iconifyButton);
							buttonPanel.add(closeButton);
						}
					}

					@Override
					protected JButton createButton(final String iconKey, final String accessibleName,
							final ActionListener action) {
						JButton button = super.createButton(iconKey, accessibleName, action);
						button.setToolTipText(accessibleName);
						return button;
					}
				};
			}
		}.installUI(rootPane);
	}

	private JPanel makeHeaderPanel(final String text) {
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setBorder(new UnpaddedTitledBorder(text));
		return panel;
	}

	private JPanel makeFooter() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.add(new JSeparator(SwingConstants.HORIZONTAL));
		panel.add(makeButtons());
		return panel;
	}

	private void makePopups() {
		menuConnectAndEditPopup = new PopupConnectAndEdit();
		menuEditPopup = new PopupEdit();
	}

	CopyOnWriteArraySet<JComponent> singleSelectionComponents = new CopyOnWriteArraySet<JComponent>();
	CopyOnWriteArraySet<JComponent> deleteComponents = new CopyOnWriteArraySet<JComponent>();
	JPanel connectButtons = null;
	JPanel saveButtons = null;
	JPanel tableButtons = null;
	JPanel tableEditButtons = null;
	JPopupMenu menuEditPopup = null;
	JPopupMenu menuConnectAndEditPopup = null;

	private JComponent makeButtons() {
		JPanel foot = new JPanel();
		foot.setLayout(new GridLayout(1, 3));
		// Table Buttons
		{
			JPanel panelTableControls = new JPanel();
			panelTableControls.setLayout(new BoxLayout(panelTableControls, BoxLayout.LINE_AXIS));
			{
				JPanel panel = new JPanel();
				panel.setLayout(new FlowLayout(FlowLayout.LEFT));
				//
				JButton add = new JButton();
				add.setToolTipText("Add");
				add.setIcon(Resources.addIcon);
				add.addActionListener(e -> {
					if (selectedTable != null) {
						selectedTable.addRowDialog(this);
					}
				});
				panel.add(add);
				panelTableControls.add(panel);
			}
			//
			{
				JPanel panel = new JPanel();
				panel.setLayout(new FlowLayout(FlowLayout.LEFT));
				//
				JButton edit = new JButton();
				edit.setToolTipText("Edit");
				edit.setIcon(Resources.editIcon);
				edit.addActionListener(e -> {
					if (selectedTable != null) {
						selectedTable.editRowDialog(this);
					}
				});
				singleSelectionComponents.add(edit);
				panel.add(edit);
				JButton copy = new JButton();
				copy.setToolTipText("Copy");
				copy.setIcon(Resources.copyIcon);
				copy.addActionListener(e -> {
					if (selectedTable != null) {
						selectedTable.copyRowDialog(this);
					}
				});
				singleSelectionComponents.add(copy);
				panel.add(copy);
				JButton delete = new JButton();
				delete.setToolTipText("Delete");
				delete.setIcon(Resources.deleteIcon);
				delete.addActionListener(e -> {
					if ((selectedTable != null) && (selectedTable.selectedRow() >= 0)) {
						final int response = showConfirmDialog(this, //
								"Are you sure?", "Delete entry", //
								JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
						if (response == JOptionPane.OK_OPTION) {
							final int r = selectedTable.removeSelectedRow();
							log.debug("DELETED row={}", r);
						}
					}
				});
				singleSelectionComponents.add(delete);
				deleteComponents.add(delete);
				panel.add(delete);
				panelTableControls.add(panel);
				tableEditButtons = panel;
			}
			tableButtons = panelTableControls;
			foot.add(panelTableControls);
		}
		// Connect Buttons
		{
			JPanel panel = new JPanel();
			panel.setLayout(new FlowLayout(FlowLayout.LEFT));
			//
			JButton connect = new JButton();
			connect.setToolTipText("Connect");
			connect.setIcon(Resources.connectIcon);
			connect.setMnemonic(KeyEvent.VK_C);
			connect.addActionListener(e -> {
				selectedTable.getSelectedListPK().forEach((id) -> client.connect(id));
			});
			panel.add(connect);
			JButton disconnect = new JButton();
			disconnect.setToolTipText("Disconnect");
			disconnect.setIcon(Resources.disconnectIcon);
			disconnect.setMnemonic(KeyEvent.VK_D);
			disconnect.addActionListener(e -> {
				selectedTable.getSelectedListPK().forEach((id) -> client.disconnect(id));
			});
			panel.add(disconnect);
			connectButtons = panel;
			foot.add(panel);
		}
		// Save
		{
			JPanel panel = new JPanel();
			panel.setLayout(new FlowLayout(FlowLayout.RIGHT));
			//
			JButton save = new JButton();
			save.setToolTipText("Save");
			save.setIcon(Resources.saveIcon);
			save.setMnemonic(KeyEvent.VK_S);
			save.addActionListener(e -> {
				client.save(ee -> saveButtons.setVisible(false));
				if (selectedTable != null) {
					selectedTable.table.requestFocusInWindow();
				}
			});
			panel.add(save);
			saveButtons = panel;
			foot.add(panel);
			saveButtons.setVisible(client.isDirtyConfig());
		}
		// Keys / Settings / Theme / Exit
		{
			JPanel panel = new JPanel();
			panel.setLayout(new FlowLayout(FlowLayout.RIGHT));
			//
			final JPopupMenu keysPopup = new JPopupMenu();
			//
			JMenuItem createItem = new JMenuItem("Create Key Pair");
			createItem.setIcon(Resources.addIcon);
			createItem.addActionListener(e -> {
				SwingUtilities.invokeLater(() -> {
					new KeyGenGUI(WindowedGUI.this).init();
				});
			});
			JMenuItem changePrivateKeyPassItem = new JMenuItem("Change Private Key Passphrase");
			changePrivateKeyPassItem.setIcon(Resources.editPassIcon);
			changePrivateKeyPassItem.addActionListener(e -> {
				SwingUtilities.invokeLater(() -> {
					new KeyChangePassGUI(WindowedGUI.this).init();
				});
			});
			JMenuItem authorizeKeyItem = new JMenuItem("Authorize Key in Selected Host *");
			authorizeKeyItem.setEnabled(false);
			authorizeKeyItem.setIcon(Resources.fileUploadIcon);
			authorizeKeyItem.setToolTipText(
					"* Only supported in connected Linux Boxes with Shell Access, OpenSSH-server and Bash");
			authorizeKeyItem.addActionListener(e -> {
				SwingUtilities.invokeLater(() -> {
					final String id = ((selectedTable.selectedRows().length == 1) //
							? selectedTable.getSelectedPK() //
							: null);
					new KeyUploadGUI(WindowedGUI.this, client, id).init();
				});
			});
			//
			keysPopup.add(createItem);
			keysPopup.add(changePrivateKeyPassItem);
			keysPopup.add(authorizeKeyItem);
			//
			JButton keys = new JButton();
			keys.setToolTipText("Key Tools");
			keys.setIcon(Resources.keyIcon);
			keys.setMnemonic(KeyEvent.VK_K);
			keys.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					final ConnectionStatus status = ((selectedTable.selectedRows().length == 1) //
							? client.getStatus(selectedTable.getSelectedPK()) //
							: null);
					authorizeKeyItem.setEnabled(status == ConnectionStatus.CONNECTED);
					keysPopup.show(e.getComponent(), e.getX(), e.getY());
				}
			});
			panel.add(keys);
			//
			JButton settings = new JButton();
			settings.setToolTipText("Global Settings");
			settings.setIcon(Resources.settingsIcon);
			settings.setMnemonic(KeyEvent.VK_G);
			settings.addActionListener(e -> new GlobalSettingsDialog(WindowedGUI.this).init());
			panel.add(settings);
			//
			JButton theme = new JButton();
			theme.setToolTipText("Theme");
			theme.setIcon(Resources.themeIcon);
			theme.setMnemonic(KeyEvent.VK_T);
			theme.addActionListener(e -> new ThemeDialog(WindowedGUI.this).init());
			panel.add(theme);
			//
			if (!isTrayApp) {
				JButton exit = new JButton();
				exit.setToolTipText("Exit");
				exit.setIcon(Resources.exitIcon);
				exit.setMnemonic(KeyEvent.VK_X);
				exit.addActionListener(e -> dispose());
				panel.add(exit);
			}
			//
			foot.add(panel);
		}

		return foot;
	}

	public static int showConfirmDialog(final Component parentComponent, //
			final Object message, final String title, //
			final int optionType, final int messageType) {
		// https://stackoverflow.com/a/9314409/1450967
		final String[] options;
		final String defaultOption;
		switch (optionType) {
			case JOptionPane.OK_CANCEL_OPTION:
				options = new String[] {
						UIManager.getString("OptionPane.okButtonText"), //
						UIManager.getString("OptionPane.cancelButtonText")
				};
				defaultOption = UIManager.getString("OptionPane.cancelButtonText");
				break;
			case JOptionPane.YES_NO_OPTION:
				options = new String[] {
						UIManager.getString("OptionPane.yesButtonText"), //
						UIManager.getString("OptionPane.noButtonText")
				};
				defaultOption = UIManager.getString("OptionPane.noButtonText");
				break;
			case JOptionPane.YES_NO_CANCEL_OPTION:
				options = new String[] {
						UIManager.getString("OptionPane.yesButtonText"), //
						UIManager.getString("OptionPane.noButtonText"), //
						UIManager.getString("OptionPane.cancelButtonText")
				};
				defaultOption = UIManager.getString("OptionPane.cancelButtonText");
				break;
			default:
				throw new IllegalArgumentException("Unknown optionType " + optionType);
		}
		return JOptionPane.showOptionDialog(parentComponent, message, title, //
				optionType, messageType, null, options, defaultOption);
	}

	static class BooleanRenderer extends DefaultTableCellRenderer.UIResource {
		private static final long serialVersionUID = 42L;

		public BooleanRenderer() {
			super();
			setHorizontalAlignment(JLabel.CENTER);
		}

		@Override
		public void setValue(Object value) {
			if (value instanceof Boolean) {
				final boolean v = ((Boolean) value).booleanValue();
				final String t = v ? "OptionPane.yesButtonText" : "OptionPane.noButtonText";
				setIcon(v ? Resources.checkIcon : Resources.noCheckIcon);
				setToolTipText(UIManager.getString(t));
			}
		}
	}

	static class ConnectionStatusRenderer extends DefaultTableCellRenderer.UIResource {
		private static final long serialVersionUID = 42L;

		public ConnectionStatusRenderer() {
			super();
			setHorizontalAlignment(JLabel.CENTER);
		}

		@Override
		public void setValue(final Object value) {
			if (value instanceof ConnectionStatus) {
				final ConnectionStatus v = ((ConnectionStatus) value);
				setToolTipText(v.getLabel());
				switch (v) {
					case NOT_CONNECTED:
						setIcon(Resources.noConnectedIcon);
						break;
					case CONNECTING:
						setIcon(Resources.spinnerIcon);
						break;
					case CONNECTED:
						setIcon(Resources.connectedIcon);
						break;
					case DISCONNECTED:
						setIcon(Resources.disconnectedIcon);
						break;
				}
			}
		}
	}

	static class TipStringRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 42L;

		public TipStringRenderer() {
			super();
			setHorizontalAlignment(JLabel.LEFT);
		}

		@Override
		public void setValue(final Object value) {
			final String v = String.valueOf(value);
			super.setValue(v);
			setToolTipText(v);
		}
	}

	static class TipIntegerRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 42L;

		public TipIntegerRenderer() {
			super();
			setHorizontalAlignment(JLabel.RIGHT);
		}

		@Override
		public void setValue(final Object value) {
			final String v = String.valueOf(value);
			super.setValue(v);
			setToolTipText(v);
		}
	}

	static class ForwardTypeRenderer extends DefaultTableCellRenderer.UIResource {
		private static final long serialVersionUID = 42L;

		public ForwardTypeRenderer() {
			super();
			setHorizontalAlignment(JLabel.CENTER);
		}

		@Override
		public void setValue(final Object value) {
			if (value instanceof Forward.Type) {
				final Forward.Type t = ((Forward.Type) value);
				final String v = String.valueOf(t);
				// super.setValue(v.substring(0, 1));
				setToolTipText(v);
				switch (t) {
					case LOCAL:
						setIcon(Resources.typeLocalIcon);
						break;
					case REMOTE:
						setIcon(Resources.typeRemoteIcon);
						break;
					case DYNAMIC:
						setIcon(Resources.typeDynamicIcon);
						break;
				}
			}
		}
	}

	static class CollectionRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 42L;
		private static final String newline = System.getProperty("line.separator");

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
					column);
			if (c instanceof JLabel) {
				final JLabel j = (JLabel) c;
				if (value instanceof Collection) {
					final StringBuilder sb = new StringBuilder();
					for (final Object e : ((Collection<?>) value)) {
						sb.append(e).append(newline);
					}
					if (sb.length() > 0) {
						sb.setLength(sb.length() - newline.length());
					}
					j.setToolTipText(sb.toString());
					j.setHorizontalAlignment(JLabel.CENTER);
					j.setText(sb.toString().replace(newline, ", "));
				} else {
					j.setToolTipText(String.valueOf(value));
				}
			}
			return c;
		}
	}

	class Tabs extends JPanel {
		private static final long serialVersionUID = 42L;

		Tabs() {
			super(new GridLayout(1, 1));

			tabs = new JTabbedPane();
			// Enables to use scrolling tabs
			tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
			// Create Tabs
			makeTabIdentities(tabs);
			Connections conn = makeTabConnections(tabs);
			makeTabForwards(tabs);
			// Add the tabbed pane to this panel.
			add(tabs);
			// Begin at Connections
			tabs.setSelectedComponent(conn);
		}

		abstract class Table extends JPanel {
			private static final long serialVersionUID = 42L;
			protected final JTable table;
			protected final DefaultTableModel tableModel;

			Table() {
				setLayout(new BorderLayout());
				addComponentListener(new ComponentAdapter() {
					public void componentShown(ComponentEvent e) {
						log.debug("Show: {}", e);
						tableShown();
					}

					public void componentHidden(ComponentEvent e) {
						log.debug("Hide: {}", e);
						selectedTable = null;
						tableEditButtons.setVisible(false);
						tableButtons.setVisible(false);
						table.clearSelection();
						table.transferFocus();
					}
				});
				table = new JTable() {
					private static final long serialVersionUID = 42L;

					@Override
					protected JTableHeader createDefaultTableHeader() {
						return new JTableHeader(columnModel) {
							private static final long serialVersionUID = 42L;

							// Implement table header tool tips.
							public String getToolTipText(final MouseEvent e) {
								final int index = columnModel.getColumnIndexAtX(e.getPoint().x);
								final int realIndex = columnModel.getColumn(index).getModelIndex();
								return String.valueOf(columnModel.getColumn(realIndex).getHeaderValue());
							}
						};
					}
				};
				// table.setComponentPopupMenu(menuEditPopup);
				table.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseReleased(final MouseEvent e) {
						if (SwingUtilities.isRightMouseButton(e)) {
							final int r = table.rowAtPoint(e.getPoint());
							if ((r >= 0) && (r < table.getRowCount())) {
								boolean found = false;
								for (final int sr : table.getSelectedRows()) {
									if (sr == r) {
										found = true;
										break;
									}
								}
								if (!found) {
									table.setRowSelectionInterval(r, r);
									selectedRowChanged(true);
								}
							} else {
								table.clearSelection();
								selectedRowChanged(true);
							}
							if (table.getSelectedRow() < 0)
								return;
							if (e.isPopupTrigger() && (e.getComponent() instanceof JTable)) {
								if (Connections.class.equals(Table.this.getClass())) {
									menuConnectAndEditPopup.show(e.getComponent(), e.getX(), e.getY());
								} else {
									menuEditPopup.show(e.getComponent(), e.getX(), e.getY());
								}
							}
						}
					}

					@Override
					public void mousePressed(final MouseEvent e) {
						if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() == 2)) {
							if (table.getSelectedRow() < 0)
								return;
							if (selectedTable != null) {
								selectedTable.editRowDialog(WindowedGUI.this);
							}
						}
					}
				});
				table.getSelectionModel().addListSelectionListener(e -> {
					selectedRowChanged(selectedRow() >= 0);
				});
				table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				table.setModel(tableModel = initTableModel());
				table.setAutoCreateRowSorter(false);
				createAliasSorter(table);

				table.setShowGrid(true);
				table.setDefaultRenderer(Boolean.class, new BooleanRenderer());
				table.setDefaultRenderer(ConnectionStatus.class, new ConnectionStatusRenderer());
				table.setDefaultRenderer(List.class, new CollectionRenderer());
				table.setDefaultRenderer(Set.class, new CollectionRenderer());
				table.setDefaultRenderer(String.class, new TipStringRenderer());
				table.setDefaultRenderer(Integer.class, new TipIntegerRenderer());
				table.setDefaultRenderer(Forward.Type.class, new ForwardTypeRenderer());
				// AutofitTableColumns.autoResizeTable(table, true, 30); // No funciona bien
				// Hide Column ID
				table.getColumnModel().getColumn(0).setMinWidth(0);
				table.getColumnModel().getColumn(0).setMaxWidth(0);
				table.getColumnModel().getColumn(0).setWidth(0);

				add(table.getTableHeader(), BorderLayout.NORTH);
				add(new JScrollPane(table), BorderLayout.CENTER);
			}

			protected void createAliasSorter(final JTable table) {
				final TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(table.getModel());
				table.setRowSorter(sorter);
				final List<RowSorter.SortKey> sortKeys = new ArrayList<RowSorter.SortKey>();
				final int columnIndexToSort = 1; // Alias
				sortKeys.add(new RowSorter.SortKey(columnIndexToSort, SortOrder.ASCENDING));
				sorter.setSortKeys(sortKeys);
				sorter.sort();
			}

			public boolean animateIcons() {
				final int rows = tableModel.getRowCount();
				final int cols = tableModel.getColumnCount();
				boolean changed = false;
				for (int i = 0; i < cols; i++) {
					if (tableModel.getColumnClass(i) == ConnectionStatus.class) {
						for (int j = 0; j < rows; j++) {
							if (dirtyConnectionStatus) {
								final String id = (String) tableModel.getValueAt(j, 0);
								if ((id == null) || id.isEmpty()) {
									continue;
								}
								tableModel.setValueAt(client.getStatus(id), j, i);
								changed = true;
							} else {
								if (ConnectionStatus.CONNECTING.equals(tableModel.getValueAt(j, i))) {
									tableModel.setValueAt(ConnectionStatus.CONNECTING, j, i);
									changed = true;
								}
							}
						}
					}
				}
				dirtyConnectionStatus = false;
				return changed;
			}

			public int selectedRow() {
				if (selectedTable != null) {
					return selectedTable.table.getSelectedRow();
				}
				return -1;
			}

			public int[] selectedRows() {
				if (selectedTable != null) {
					return selectedTable.table.getSelectedRows();
				}
				return new int[0];
			}

			protected void needSave() {
				saveButtons.setVisible(true);
			}

			protected void tableShown() {
				selectedTable = this;
				tableEditButtons.setVisible(false);
				tableButtons.setVisible(true);
			}

			protected void selectedRowChanged(final boolean selected) {
				selectedTable = this;
				tableEditButtons.setVisible(selected);
				singleSelectionComponents.forEach((e) -> e.setEnabled(selectedRows().length == 1));
			}

			protected abstract void addRowDialog(final Frame parent);

			protected abstract void editRowDialog(final Frame parent);

			protected abstract void copyRowDialog(final Frame parent);

			public int removeSelectedRow() {
				final int row = selectedRow();
				if (row >= 0) {
					removeRow(row);
				}
				return row;
			}

			public JTable getTable() {
				return table;
			}

			protected abstract DefaultTableModel initTableModel();

			protected abstract void removeRow(final String alias);

			public void addRow(final Object... row) {
				tableModel.addRow(row);
			}

			public void removeRow(final int row) {
				final int modelRow = table.convertRowIndexToModel(row);
				final String alias = String.valueOf(tableModel.getValueAt(modelRow, 0));
				if ((alias == null) || alias.isEmpty()) {
					return;
				}
				final String data = String.valueOf(tableModel.getDataVector().get(modelRow));
				log.info("DELETING row: {}", data);
				removeRow(alias);
				tableModel.removeRow(modelRow);
			}

			public String getSelectedPK() {
				final int row = selectedRow();
				if (row < 0) {
					return null;
				}
				final int modelRow = table.convertRowIndexToModel(row);
				return String.valueOf(tableModel.getValueAt(modelRow, 0));
			}

			public List<String> getSelectedListPK() {
				if (selectedRow() < 0) {
					return Collections.emptyList();
				}
				final ArrayList<String> list = new ArrayList<String>();
				for (final int row : selectedRows()) {
					final int modelRow = table.convertRowIndexToModel(row);
					list.add(String.valueOf(tableModel.getValueAt(modelRow, 0)));
				}
				return list;
			}
		}

		private Identities makeTabIdentities(final JTabbedPane parent) {
			// JPanel empty = makeTextPanel("No Identities");
			Identities table = new Identities();
			parent.addTab("Identities", Resources.userIcon, table, //
					"Configure Identities (password, private keys, etc)");
			parent.setMnemonicAt(0, KeyEvent.VK_I);
			return table;
		}

		class Identities extends Table {
			private static final long serialVersionUID = 42L;

			@Override
			protected DefaultTableModel initTableModel() {
				DefaultTableModel model = new DefaultTableModel(new String[] {
						"ID", // Hidden
						"Alias", "Username", "Password", "Private Key", "Connections"
				}, 0) {
					private static final long serialVersionUID = 42L;
					Class<?>[] columnTypes = new Class<?>[] {
							String.class, String.class, String.class, Boolean.class, Boolean.class, Set.class
					};

					@Override
					public Class<?> getColumnClass(final int columnIndex) {
						return columnTypes[columnIndex];
					}

					@Override
					public boolean isCellEditable(final int rowIndex, final int columnIndex) {
						return false;
					}
				};
				return model;
			}

			@Override
			protected void tableShown() {
				super.tableShown();
				tableModel.setRowCount(0);
				for (final Identity item : client.getIdentities()) {
					addRow(item.id, //
							item.alias, item.username, //
							!item.password.isEmpty(), //
							!item.keyfile.isEmpty(), //
							client.getIdentityUsage(item));
				}
			}

			@Override
			protected void addRowDialog(final Frame parent) {
				new EditorDialog(parent, EditMode.ADD);
			}

			@Override
			protected void editRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.EDIT);
				}
			}

			@Override
			protected void copyRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.COPY);
				}
			}

			@Override
			protected void selectedRowChanged(final boolean selected) {
				super.selectedRowChanged(selected);
				final boolean enable = (selectedRows().length == 1) //
						&& client.getIdentityUsage(client.getIdentity(getSelectedPK())).isEmpty();
				deleteComponents.forEach((e) -> e.setEnabled(enable));
			}

			@Override
			protected void removeRow(final String alias) {
				log.debug("Remove Row Identity alias={}", alias);
				client.removeIdentity(alias);
				needSave();
			}

			class EditorDialog extends JDialog {
				private static final long serialVersionUID = 42L;

				public EditorDialog(final Frame parent, final EditMode mode) {
					// Modal
					super(parent, tabs.getTitleAt(tabs.getSelectedIndex()) + " " + mode, true);
					setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					EscapeHandler.installEscapeCloseOperation(this);
					//
					final AtomicReference<String> aliasEdit = new AtomicReference<String>("");
					//
					JPanel panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
					//
					JPanel aliasPanel = makeHeaderPanel("Identity Alias");
					JTextField alias = new JTextField(20);
					alias.putClientProperty("JTextField.placeholderText", "Name");
					alias.setToolTipText("Name");
					aliasPanel.add(alias);
					panel.add(aliasPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel userPanel = makeHeaderPanel("Username");
					JTextField username = new JTextField(20);
					userPanel.add(username);
					panel.add(userPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					JPanel passPanel = makeHeaderPanel("Password");
					JPasswordField password = new JPasswordField(20);
					passPanel.add(password);
					panel.add(passPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel privateKeyPanel = makeHeaderPanel("Private Key");
					JPanel filePanel = new JPanel();
					filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.LINE_AXIS));
					JTextField privateKey = new JTextField(20);
					filePanel.add(privateKey);
					JButton openButton = new JButton();
					openButton.setIcon(UIManager.getIcon("Tree.openIcon"));
					openButton.setMnemonic(KeyEvent.VK_O);
					openButton.addActionListener(e -> {
						JFileChooser chooser = new JFileChooser();
						// Path relativize(Path other)
						File cfgDir = new File(client.getConfigDir());
						chooser.setCurrentDirectory(cfgDir);
						chooser.setMultiSelectionEnabled(false);
						// chooser.setFileFilter(new FileNameExtensionFilter("Private Keys", "pem"));
						int returnVal = chooser.showOpenDialog(this);
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							privateKey.setText(client.relativeFile(chooser.getSelectedFile()));
						}
					});
					filePanel.add(openButton);
					privateKeyPanel.add(filePanel);
					panel.add(privateKeyPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel buttons = new JPanel() {
						private static final long serialVersionUID = 42L;

						{
							// setLayout(new GridLayout(1, 5));
							setLayout(new FlowLayout(FlowLayout.CENTER));
							//
							JButton add = new JButton(UIManager.getString("OptionPane.okButtonText"));
							add.addActionListener(e -> {
								switch (mode) {
									case EDIT: {
										if (alias.getText().isEmpty()
												|| (client.aliasExistInIdentities(alias.getText())
														&& !alias.getText().equals(aliasEdit.get()))) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
									case ADD:
									case COPY: {
										// Duplicate FAIL
										if (alias.getText().isEmpty()
												|| client.aliasExistInIdentities(alias.getText())) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
								}
								if (username.getText().isEmpty()) {
									username.putClientProperty("JComponent.outline", "error");
									username.requestFocusInWindow();
									return;
								}
								final String id = ((mode == EditMode.EDIT) //
										? selectedTable.getSelectedPK() //
										: null);
								final Identity i = new Identity(id, //
										alias.getText(), //
										username.getText(), //
										new String(password.getPassword()), //
										privateKey.getText());
								client.setIdentity(i);
								needSave();
								selectedTable.tableShown();
								dispose();
							});
							add(add);
							//
							JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
							cancel.addActionListener(e -> dispose());
							add(cancel);
							//
							EditorDialog.this.getRootPane().setDefaultButton(add); // Default on INTRO
						}
					};
					panel.add(buttons);
					JPanel padded = new JPanel();
					padded.add(panel);
					getContentPane().add(padded);
					switch (mode) {
						case ADD:
							break;
						case EDIT:
						case COPY: {
							final Identity ide = client.getIdentity(selectedTable.getSelectedPK());
							alias.setText(ide.alias);
							username.setText(ide.username);
							password.setText(ide.getClearTextPassword());
							privateKey.setText(ide.keyfile);
							break;
						}
					}
					switch (mode) {
						case ADD:
							setIconImage(Resources.addIcon.getImage());
							break;
						case EDIT:
							setIconImage(Resources.editIcon.getImage());
							aliasEdit.set(alias.getText());
							break;
						case COPY:
							setIconImage(Resources.copyIcon.getImage());
							alias.setText(alias.getText() + "-copy");
							break;
					}
					// Prepare window.
					pack();
					setLocationRelativeTo(null);
					setResizable(false);
					// Display the window.
					setVisible(true);
				}
			}
		}

		private Connections makeTabConnections(final JTabbedPane parent) {
			// JPanel empty = makeTextPanel("No Connections");
			Connections table = new Connections();
			table.getTable().getColumnModel().getColumn(3).setPreferredWidth(35);
			table.getTable().getColumnModel().getColumn(6).setPreferredWidth(35);
			parent.addTab("Connections", Resources.chainIcon, table, //
					"Configure Connections (server, port, etc)");
			parent.setMnemonicAt(1, KeyEvent.VK_C);
			return table;
		}

		class Connections extends Table {
			private static final long serialVersionUID = 42L;

			Connections() {
				addComponentListener(new ComponentAdapter() {
					public void componentShown(ComponentEvent e) {
						// connectButtons.setVisible(true);
						animationTimer.restart();
					}

					public void componentHidden(ComponentEvent e) {
						connectButtons.setVisible(false);
						animationTimer.stop();
					}
				});
			}

			@Override
			protected void selectedRowChanged(final boolean selected) {
				super.selectedRowChanged(selected);
				connectButtons.setVisible(selected);
			}

			@Override
			protected DefaultTableModel initTableModel() {
				DefaultTableModel model = new DefaultTableModel(new String[] {
						"ID", // Hidden
						"Alias", "Hostname", "Port", //
						"Auto connect", "Reconnect", "Status"
				}, 0) {
					private static final long serialVersionUID = 42L;
					Class<?>[] columnTypes = new Class<?>[] {
							String.class, String.class, String.class, Integer.class, //
							Boolean.class, Boolean.class, ConnectionStatus.class
					};

					@Override
					public Class<?> getColumnClass(final int columnIndex) {
						return columnTypes[columnIndex];
					}

					@Override
					public boolean isCellEditable(final int rowIndex, final int columnIndex) {
						return false;
					}
				};
				table.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				return model;
			}

			@Override
			protected void tableShown() {
				super.tableShown();
				tableModel.setRowCount(0);
				for (final Connection item : client.getConnections()) {
					addRow(item.id, //
							item.alias, item.address, item.port, //
							item.isAutoStart, item.isAutoReconnect, //
							client.getStatus(item.id));
				}
			}

			@Override
			protected void addRowDialog(final Frame parent) {
				new EditorDialog(parent, EditMode.ADD);
			}

			@Override
			protected void editRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.EDIT);
				}
			}

			@Override
			protected void copyRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.COPY);
				}
			}

			@Override
			protected void removeRow(final String alias) {
				log.debug("Remove Row Connection alias={}", alias);
				client.removeConnection(alias);
				needSave();
			}

			class EditorDialog extends JDialog {
				private static final long serialVersionUID = 42L;

				public EditorDialog(final Frame parent, final EditMode mode) {
					// Modal
					super(parent, tabs.getTitleAt(tabs.getSelectedIndex()) + " " + mode, true);
					setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					EscapeHandler.installEscapeCloseOperation(this);
					//
					final AtomicReference<String> aliasEdit = new AtomicReference<String>("");
					//
					JPanel panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
					//
					JPanel aliasPanel = makeHeaderPanel("Connection Alias");
					JTextField alias = new JTextField(20);
					alias.putClientProperty("JTextField.placeholderText", "Name");
					alias.setToolTipText("Name");
					aliasPanel.add(alias);
					panel.add(aliasPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel serverPanel = makeHeaderPanel("Server");
					JPanel hostPortPanel = new JPanel();
					hostPortPanel.setLayout(new BoxLayout(hostPortPanel, BoxLayout.LINE_AXIS));
					JTextField hostname = new JTextField(10);
					hostname.putClientProperty("JTextField.placeholderText", "Address");
					hostname.setToolTipText("Address");
					hostPortPanel.add(hostname);
					JSpinner port = new JSpinner(new SpinnerNumberModel(22, 1, 0xFFFF, 1));
					port.setEditor(new JSpinner.NumberEditor(port, "#"));
					port.putClientProperty("JTextField.placeholderText", "Port");
					port.setToolTipText("Port");
					hostPortPanel.add(port);
					serverPanel.add(hostPortPanel);
					panel.add(serverPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel identityPanel = makeHeaderPanel("Identity");
					JComboBox<AliasID> identity = new JComboBox<AliasID>(client.getAliasedIdentities());
					identityPanel.add(identity);
					panel.add(identityPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel checks = new JPanel();
					checks.setBorder(new UnpaddedTitledBorder("Options"));
					checks.setLayout(new FlowLayout(FlowLayout.LEFT));
					JCheckBox autoConnect = new JCheckBox("Auto connect");
					autoConnect.setToolTipText("Auto connect");
					checks.add(autoConnect);
					JCheckBox reconnect = new JCheckBox("Reconnect");
					reconnect.setToolTipText("Reconnect");
					checks.add(reconnect);
					panel.add(checks);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel buttons = new JPanel() {
						private static final long serialVersionUID = 42L;

						{
							// setLayout(new GridLayout(1, 5));
							setLayout(new FlowLayout(FlowLayout.CENTER));
							//
							JButton add = new JButton(UIManager.getString("OptionPane.okButtonText"));
							add.addActionListener(e -> {
								switch (mode) {
									case EDIT: {
										if (alias.getText().isEmpty()
												|| (client.aliasExistInConnections(alias.getText())
														&& !alias.getText().equals(aliasEdit.get()))) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
									case ADD:
									case COPY: {
										// Duplicate FAIL
										if (alias.getText().isEmpty()
												|| client.aliasExistInConnections(alias.getText())) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
								}
								if (hostname.getText().isEmpty()) {
									hostname.putClientProperty("JComponent.outline", "error");
									hostname.requestFocusInWindow();
									return;
								}
								final String id = ((mode == EditMode.EDIT) //
										? selectedTable.getSelectedPK() //
										: null);
								final AliasID identityRef = (AliasID) identity.getSelectedItem();
								final Connection c = new Connection(id, //
										alias.getText(), //
										hostname.getText(), //
										((Integer) port.getValue()).intValue(), //
										identityRef.getID(), //
										autoConnect.isSelected(), //
										reconnect.isSelected());
								client.setConnection(c);
								needSave();
								selectedTable.tableShown();
								dispose();
							});
							add(add);
							//
							JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
							cancel.addActionListener(e -> dispose());
							add(cancel);
							//
							EditorDialog.this.getRootPane().setDefaultButton(add); // Default on INTRO
						}
					};
					panel.add(buttons);
					JPanel padded = new JPanel();
					padded.add(panel);
					getContentPane().add(padded);
					switch (mode) {
						case ADD:
							break;
						case EDIT:
						case COPY: {
							final Connection con = client.getConnection(selectedTable.getSelectedPK());
							alias.setText(con.alias);
							hostname.setText(con.address);
							port.setValue(Integer.valueOf(con.port));
							autoConnect.setSelected(con.isAutoStart);
							reconnect.setSelected(con.isAutoReconnect);
							final Identity ide = client.getIdentity(con.identity);
							if (ide != null) {
								identity.setSelectedItem(ide.getAliasFacade());
							}
							break;
						}
					}
					switch (mode) {
						case ADD:
							setIconImage(Resources.addIcon.getImage());
							break;
						case EDIT:
							setIconImage(Resources.editIcon.getImage());
							aliasEdit.set(alias.getText());
							break;
						case COPY:
							setIconImage(Resources.copyIcon.getImage());
							alias.setText(alias.getText() + "-copy");
							break;
					}
					// Prepare window.
					pack();
					setLocationRelativeTo(null);
					setResizable(false);
					// Display the window.
					setVisible(true);
				}
			}
		}

		private Forwards makeTabForwards(final JTabbedPane parent) {
			// JPanel empty = makeTextPanel("No Forwards");
			Forwards table = new Forwards();
			table.getTable().getColumnModel().getColumn(3).setPreferredWidth(35);
			table.getTable().getColumnModel().getColumn(4).setPreferredWidth(35);
			table.getTable().getColumnModel().getColumn(6).setPreferredWidth(35);
			parent.addTab("Forwards", Resources.forwardIcon, table, //
					"Configure Port Forwarding (local, remote, dynamic)");
			parent.setMnemonicAt(2, KeyEvent.VK_F);
			return table;
		}

		class Forwards extends Table {
			private static final long serialVersionUID = 42L;

			@Override
			protected DefaultTableModel initTableModel() {
				DefaultTableModel model = new DefaultTableModel(new String[] {
						"ID", // Hidden
						"Alias", //
						"Local", "Port", //
						"Type", //
						"Remote", "Port", //
						"Connections"
				}, 0) {
					private static final long serialVersionUID = 42L;
					Class<?>[] columnTypes = new Class<?>[] {
							String.class, //
							String.class, //
							String.class, Integer.class, //
							Forward.Type.class, //
							String.class, Integer.class, //
							Set.class
					};

					@Override
					public Class<?> getColumnClass(final int columnIndex) {
						return columnTypes[columnIndex];
					}

					@Override
					public boolean isCellEditable(final int rowIndex, final int columnIndex) {
						return false;
					}
				};
				return model;
			}

			@Override
			protected void tableShown() {
				super.tableShown();
				tableModel.setRowCount(0);
				for (final Forward item : client.getForwards()) {
					addRow(item.id, //
							item.alias, //
							item.getLocalSocketAddress().getHostName(), //
							item.getLocalSocketAddress().getPort(), //
							item.getType(), //
							item.getRemoteSocketAddress().getHostName(), //
							item.getRemoteSocketAddress().getPort(), //
							client.getForwardUsage(item));
				}
			}

			@Override
			protected void addRowDialog(final Frame parent) {
				new EditorDialog(parent, EditMode.ADD);
			}

			@Override
			protected void editRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.EDIT);
				}
			}

			@Override
			protected void copyRowDialog(final Frame parent) {
				if (selectedRow() >= 0) {
					new EditorDialog(parent, EditMode.COPY);
				}
			}

			@Override
			protected void selectedRowChanged(final boolean selected) {
				super.selectedRowChanged(selected);
				final boolean enable = (selectedRows().length == 1) //
						&& client.getForwardUsage(client.getForward(getSelectedPK())).isEmpty();
				deleteComponents.forEach((e) -> e.setEnabled(enable));
			}

			@Override
			protected void removeRow(final String alias) {
				log.debug("Remove Row Forward alias={}", alias);
				client.removeForward(alias);
				needSave();
			}

			class EditorDialog extends JDialog {
				private static final long serialVersionUID = 42L;

				public EditorDialog(final Frame parent, final EditMode mode) {
					// Modal
					super(parent, tabs.getTitleAt(tabs.getSelectedIndex()) + " " + mode, true);
					setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
					EscapeHandler.installEscapeCloseOperation(this);
					//
					final AtomicReference<String> aliasEdit = new AtomicReference<String>("");
					//
					JPanel panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
					//
					JPanel aliasPanel = makeHeaderPanel("Forwarding Alias");
					JTextField alias = new JTextField(20);
					alias.putClientProperty("JTextField.placeholderText", "Name");
					alias.setToolTipText("Name");
					aliasPanel.add(alias);
					panel.add(aliasPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel typePanel = makeHeaderPanel("Forward Type");
					JComboBox<Forward.Type> ftype = new JComboBox<Forward.Type>(Forward.Type.values());
					ftype.setToolTipText("Forward Type");
					typePanel.add(ftype);
					panel.add(typePanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel topAddrPanel = new JPanel();
					TitledBorder topTitle = new UnpaddedTitledBorder("Top");
					topAddrPanel.setBorder(topTitle);
					topAddrPanel.setLayout(new BoxLayout(topAddrPanel, BoxLayout.LINE_AXIS));
					JTextField topAddr = new JTextField(10);
					topAddr.setText("127.0.0.1");
					topAddr.putClientProperty("JTextField.placeholderText", "Address");
					topAddr.setToolTipText("Address");
					topAddrPanel.add(topAddr);
					JSpinner topPort = new JSpinner(new SpinnerNumberModel(0, 0, 0xFFFF, 1));
					topPort.setEditor(new JSpinner.NumberEditor(topPort, "#"));
					topPort.putClientProperty("JTextField.placeholderText", "Port");
					topPort.setToolTipText("Port");
					topAddrPanel.add(topPort);
					panel.add(topAddrPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel bottomAddrPanel = new JPanel();
					TitledBorder bottomTitle = new UnpaddedTitledBorder("Bottom");
					bottomAddrPanel.setBorder(bottomTitle);
					bottomAddrPanel.setLayout(new BoxLayout(bottomAddrPanel, BoxLayout.LINE_AXIS));
					JTextField bottomAddr = new JTextField(10);
					bottomAddr.putClientProperty("JTextField.placeholderText", "Address");
					bottomAddr.setToolTipText("Address");
					bottomAddrPanel.add(bottomAddr);
					JSpinner bottomPort = new JSpinner(new SpinnerNumberModel(0, 0, 0xFFFF, 1));
					bottomPort.setEditor(new JSpinner.NumberEditor(bottomPort, "#"));
					bottomPort.putClientProperty("JTextField.placeholderText", "Port");
					bottomPort.setToolTipText("Port");
					bottomAddrPanel.add(bottomPort);
					panel.add(bottomAddrPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel connectionPanel = makeHeaderPanel("Connections");
					CheckAliasTable connectionTable = new CheckAliasTable();
					JScrollPane connectionScroll = new JScrollPane(connectionTable);
					connectionPanel.add(connectionScroll);
					panel.add(connectionPanel);
					panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
					//
					JPanel buttons = new JPanel() {
						private static final long serialVersionUID = 42L;

						{
							setLayout(new FlowLayout(FlowLayout.CENTER));
							//
							JButton add = new JButton(UIManager.getString("OptionPane.okButtonText"));
							add.addActionListener(e -> {
								switch (mode) {
									case EDIT: {
										if (alias.getText().isEmpty()
												|| (client.aliasExistInForwards(alias.getText())
														&& !alias.getText().equals(aliasEdit.get()))) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
									case ADD:
									case COPY: {
										// Duplicate FAIL
										if (alias.getText().isEmpty()
												|| client.aliasExistInForwards(alias.getText())) {
											alias.putClientProperty("JComponent.outline", "error");
											alias.requestFocusInWindow();
											return;
										}
										break;
									}
								}
								final Forward.Type t = (Forward.Type) ftype.getSelectedItem();
								final boolean isL = (t == Forward.Type.LOCAL);
								final boolean isR = (t == Forward.Type.REMOTE);
								final boolean isD = (t == Forward.Type.DYNAMIC);
								final String localAddr = (isR ? bottomAddr : topAddr).getText();
								final Integer localPort = ((Integer) (isR ? bottomPort : topPort).getValue());
								final String remAddr = (isR ? topAddr : bottomAddr).getText();
								final Integer remPort = ((Integer) (isR ? topPort : bottomPort).getValue());
								if (((Integer) topPort.getValue()).intValue() == 0) {
									topPort.putClientProperty("JComponent.outline", "error");
									topPort.requestFocusInWindow();
									return;
								}
								if (!isD && ((Integer) bottomPort.getValue()).intValue() == 0) {
									bottomPort.putClientProperty("JComponent.outline", "error");
									bottomPort.requestFocusInWindow();
									return;
								}
								if (isL && bottomAddr.getText().isEmpty()) {
									bottomAddr.putClientProperty("JComponent.outline", "error");
									bottomAddr.requestFocusInWindow();
									return;
								}
								final String id = ((mode == EditMode.EDIT) //
										? selectedTable.getSelectedPK() //
										: null);
								final Forward f = Forward.valueOf(t, //
										id, //
										alias.getText(), //
										connectionTable.getSelectedID(), //
										localAddr, localPort.intValue(), //
										remAddr, remPort.intValue());
								client.setForward(f);
								needSave();
								selectedTable.tableShown();
								dispose();
							});
							add(add);
							//
							JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
							cancel.addActionListener(e -> dispose());
							add(cancel);
							//
							EditorDialog.this.getRootPane().setDefaultButton(add); // Default on INTRO
						}
					};
					panel.add(buttons);
					JPanel padded = new JPanel();
					padded.add(panel);
					getContentPane().add(padded);
					ftype.addActionListener(e -> {
						switch ((Forward.Type) ftype.getSelectedItem()) {
							case DYNAMIC: {
								topTitle.setTitle("Local (Listen)");
								bottomTitle.setTitle("Remote (Connect to)");
								bottomAddr.setEnabled(false);
								bottomPort.setEnabled(false);
								break;
							}
							case LOCAL:
								topTitle.setTitle("Local (Listen)");
								bottomTitle.setTitle("Remote (Connect to)");
								bottomAddr.setEnabled(true);
								bottomPort.setEnabled(true);
								break;
							case REMOTE:
								topTitle.setTitle("Remote (Listen)");
								bottomTitle.setTitle("Local (Connect to)");
								bottomAddr.setEnabled(true);
								bottomPort.setEnabled(true);
								break;
						}
						SwingUtilities.invokeLater(() -> EditorDialog.this.repaint());
					});
					switch (mode) {
						case ADD:
							ftype.setSelectedItem(Forward.Type.LOCAL);
							connectionTable.addRows(client.getAliasedConnections());
							break;
						case EDIT:
						case COPY: {
							final Forward f = client.getForward(selectedTable.getSelectedPK());
							final Forward.Type t = (Forward.Type) f.getType();
							final boolean isR = (t == Forward.Type.REMOTE);
							final SshdSocketAddress top = (isR //
									? f.getRemoteSocketAddress()
									: f.getLocalSocketAddress());
							final SshdSocketAddress bottom = (isR //
									? f.getLocalSocketAddress()
									: f.getRemoteSocketAddress());
							alias.setText(f.alias);
							ftype.setSelectedItem(t);
							ftype.setEnabled(false);
							topAddr.setText(top.getHostName());
							topPort.setValue(Integer.valueOf(top.getPort()));
							bottomAddr.setText(bottom.getHostName());
							bottomPort.setValue(Integer.valueOf(bottom.getPort()));
							for (final AliasID con : client.getAliasedConnections()) {
								connectionTable.addRow(con.getID(), con.getAlias(), //
										f.connections.contains(con.getID()));
							}
							break;
						}
					}
					switch (mode) {
						case ADD:
							setIconImage(Resources.addIcon.getImage());
							break;
						case EDIT:
							setIconImage(Resources.editIcon.getImage());
							aliasEdit.set(alias.getText());
							break;
						case COPY:
							setIconImage(Resources.copyIcon.getImage());
							alias.setText(alias.getText() + "-copy");
							break;
					}
					topPort.addChangeListener(e -> {
						final Forward.Type t = (Forward.Type) ftype.getSelectedItem();
						final boolean isD = (t == Forward.Type.DYNAMIC);
						if (isD) {
							return;
						}
						if (bottomPort.getValue().equals(Integer.valueOf(0))) {
							bottomPort.setValue(topPort.getValue());
						}
					});
					// Prepare window.
					pack();
					setLocationRelativeTo(null);
					setResizable(false);
					// Display the window.
					setVisible(true);
				}
			}
		}
	}

	class PopupConnectAndEdit extends PopupEdit {
		private static final long serialVersionUID = 42L;

		PopupConnectAndEdit() {
			super();
			int i = 0;
			//
			JMenuItem connect = new JMenuItem("Connect");
			connect.setToolTipText("Connect");
			connect.setIcon(Resources.connectIcon);
			connect.setMnemonic(KeyEvent.VK_C);
			connect.addActionListener(e -> {
				selectedTable.getSelectedListPK().forEach((id) -> client.connect(id));
			});
			add(connect, i++);
			//
			JMenuItem disconnect = new JMenuItem("Disconnect");
			disconnect.setToolTipText("Disconnect");
			disconnect.setIcon(Resources.disconnectIcon);
			disconnect.setMnemonic(KeyEvent.VK_D);
			disconnect.addActionListener(e -> {
				selectedTable.getSelectedListPK().forEach((id) -> client.disconnect(id));
			});
			add(disconnect, i++);
			//
			add(new JPopupMenu.Separator(), i++);
		}
	}

	class PopupEdit extends JPopupMenu {
		private static final long serialVersionUID = 42L;

		PopupEdit() {
			JMenuItem add = new JMenuItem("Add");
			add.setMnemonic(KeyEvent.VK_A);
			add.setToolTipText("Add");
			add.setIcon(Resources.addIcon);
			add.addActionListener(e -> {
				if (selectedTable != null) {
					selectedTable.addRowDialog(WindowedGUI.this);
				}
			});
			add(add);
			//
			JMenuItem edit = new JMenuItem("Edit");
			edit.setMnemonic(KeyEvent.VK_E);
			edit.setToolTipText("Edit");
			edit.setIcon(Resources.editIcon);
			edit.addActionListener(e -> {
				if (selectedTable != null) {
					selectedTable.editRowDialog(WindowedGUI.this);
				}
			});
			singleSelectionComponents.add(edit);
			add(edit);
			//
			JMenuItem copy = new JMenuItem("Copy");
			copy.setToolTipText("Copy");
			copy.setIcon(Resources.copyIcon);
			copy.addActionListener(e -> {
				if (selectedTable != null) {
					selectedTable.copyRowDialog(WindowedGUI.this);
				}
			});
			singleSelectionComponents.add(copy);
			add(copy);
			//
			JMenuItem delete = new JMenuItem("Delete");
			delete.setToolTipText("Delete");
			delete.setIcon(Resources.deleteIcon);
			delete.addActionListener(e -> {
				if ((selectedTable != null) && (selectedTable.selectedRow() >= 0)) {
					final int response = showConfirmDialog(this, //
							"Are you sure?", "Delete entry", //
							JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
					if (response == JOptionPane.OK_OPTION) {
						final int r = selectedTable.removeSelectedRow();
						log.debug("DELETED row={}", r);
					}
				}
			});
			singleSelectionComponents.add(delete);
			deleteComponents.add(delete);
			add(delete);
		}
	}

	static class GlobalSettingsDialog extends JDialog {
		private static final long serialVersionUID = 42L;

		public GlobalSettingsDialog(final Frame parent) {
			// Modal
			super(parent, "Global Settings", true);
			setIconImages(Resources.mainIcons);
			setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			EscapeHandler.installEscapeCloseOperation(this);
			//
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			//
			JPanel configHeaderPanel = makeHeaderPanel("Directory Configuration");
			JPanel configPanel = new JPanel();
			configPanel.setLayout(new BoxLayout(configPanel, BoxLayout.LINE_AXIS));
			JTextField configDir = new JTextField(20);
			configPanel.add(configDir);
			JButton openButton = new JButton();
			openButton.setToolTipText("Select configuration folder");
			openButton.setIcon(UIManager.getIcon("Tree.openIcon"));
			openButton.setMnemonic(KeyEvent.VK_O);
			openButton.addActionListener(e -> {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(openButton.getToolTipText());
				chooser.setMultiSelectionEnabled(false);
				chooser.setAcceptAllFileFilterUsed(false);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				final File directory;
				if (!configDir.getText().isEmpty()) {
					directory = new File(configDir.getText());
				} else {
					directory = defaultConfigDir();
				}
				chooser.setCurrentDirectory(directory);
				int returnVal = chooser.showOpenDialog(this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					configDir.setText(absoluteFile(chooser.getSelectedFile()));
				}
			});
			configPanel.add(openButton);
			JButton browseButton = new JButton();
			browseButton.setToolTipText("Show folder content");
			browseButton.setIcon(UIManager.getIcon("FileChooser.detailsViewIcon"));
			browseButton.addActionListener(e -> {
				if (!configDir.getText().isEmpty()) {
					Desktop desktop = Desktop.getDesktop();
					try {
						desktop.open(new File(configDir.getText()));
					} catch (Exception ex) {
						log.error("Unable to browse folder: {}", String.valueOf(ex));
					}
				}
			});
			configPanel.add(browseButton);
			configHeaderPanel.add(configPanel);
			panel.add(configHeaderPanel);
			panel.add(Box.createRigidArea(new Dimension(5, V_SPACE)));
			//
			JPanel buttons = new JPanel() {
				private static final long serialVersionUID = 42L;

				{
					// setLayout(new GridLayout(1, 5));
					setLayout(new FlowLayout(FlowLayout.CENTER));
					//
					JButton add = new JButton(UIManager.getString("OptionPane.okButtonText"));
					add.addActionListener(e -> {
						if (configDir.getText().isEmpty() || !new File(configDir.getText()).isDirectory()) {
							configDir.putClientProperty("JComponent.outline", "error");
							configDir.requestFocusInWindow();
							return;
						}
						final String prevConcfig = GlobalSettings.CONFIG_DIRECTORY.get();
						GlobalSettings.CONFIG_DIRECTORY.set(configDir.getText());
						if (prevConcfig != null) {
							JOptionPane.showMessageDialog(this, //
									"Configuration change does not take effect until next restart", //
									Resources.appInfo.iam, //
									JOptionPane.WARNING_MESSAGE);
						}
						dispose();
					});
					add(add);
					//
					JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
					cancel.addActionListener(e -> dispose());
					add(cancel);
					//
					GlobalSettingsDialog.this.getRootPane().setDefaultButton(add); // Default on INTRO
				}
			};
			panel.add(buttons);
			JPanel padded = new JPanel();
			padded.add(panel);
			getContentPane().add(padded);
			//
			if (GlobalSettings.CONFIG_DIRECTORY.get() != null) {
				configDir.setText(GlobalSettings.CONFIG_DIRECTORY.get());
			} else {
				final File directory = defaultConfigDir();
				directory.mkdir();
				configDir.setText(absoluteFile(directory));
			}
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

		private JPanel makeHeaderPanel(final String text) {
			final JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			panel.setBorder(new UnpaddedTitledBorder(text));
			return panel;
		}
	}

	static class UnpaddedTitledBorder extends TitledBorder {
		private static final long serialVersionUID = 42L;
		private static final Color DEFAULT_COLOR = new JSeparator().getForeground();

		public UnpaddedTitledBorder(final String title) {
			super(BorderFactory.createMatteBorder(1, 0, 0, 0, DEFAULT_COLOR), title);
		}

		@Override
		public Insets getBorderInsets(final Component c, Insets insets) {
			insets = super.getBorderInsets(c, insets);
			insets.left = 0;
			insets.right = 0;
			insets.bottom = 0;
			return insets;
		}
	}

	static class CheckAliasTable extends JTable {
		private static final long serialVersionUID = 42L;
		protected final DefaultTableModel tableModel;

		CheckAliasTable() {
			super();
			getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			setModel(tableModel = initTableModel());
			setAutoCreateRowSorter(false);
			createAliasSorter(this);
			final int rows = 6 - 1;
			final int rh = getRowHeight() * rows;
			final int sp = getIntercellSpacing().height * (rows + 1);
			final int hh = getTableHeader().getPreferredSize().height;
			setMinimumSize(new Dimension(EDIT_DEFAULT_WIDTH, rh + sp + hh));
			setPreferredScrollableViewportSize(getMinimumSize());

			setShowGrid(true);
			// Hide Column ID
			getColumnModel().getColumn(0).setMinWidth(0);
			getColumnModel().getColumn(0).setMaxWidth(0);
			getColumnModel().getColumn(0).setWidth(0);
			// Limit Boolean Width
			getColumnModel().getColumn(1).setMinWidth(35);
			getColumnModel().getColumn(1).setMaxWidth(35);
		}

		protected void addRows(final AliasID[] arr) {
			for (final AliasID a : arr) {
				addRow(a.getID(), a.getAlias(), false);
			}
		}

		protected void addRow(final String id, final String alias, final boolean checked) {
			tableModel.addRow(new Object[] {
					id, Boolean.valueOf(checked), alias
			});
		}

		protected List<String> getSelectedID() {
			final ArrayList<String> list = new ArrayList<String>();
			final int rows = tableModel.getRowCount();
			for (int i = 0; i < rows; i++) {
				final String id = (String) tableModel.getValueAt(i, 0);
				final Boolean checked = (Boolean) tableModel.getValueAt(i, 1);
				if (checked.booleanValue()) {
					list.add(id);
				}
			}
			return list;
		}

		protected DefaultTableModel initTableModel() {
			DefaultTableModel model = new DefaultTableModel(new String[] {
					"ID", // Hidden
					"Map", "Alias"
			}, 0) {
				private static final long serialVersionUID = 42L;
				Class<?>[] columnTypes = new Class<?>[] {
						String.class, Boolean.class, String.class
				};

				@Override
				public Class<?> getColumnClass(final int columnIndex) {
					return columnTypes[columnIndex];
				}

				@Override
				public boolean isCellEditable(final int rowIndex, final int columnIndex) {
					return getColumnClass(columnIndex).equals(Boolean.class);
				}
			};
			return model;
		}

		protected void createAliasSorter(final JTable table) {
			final TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(table.getModel());
			table.setRowSorter(sorter);
			final List<RowSorter.SortKey> sortKeys = new ArrayList<RowSorter.SortKey>();
			final int columnIndexToSort = 2; // Alias
			sortKeys.add(new RowSorter.SortKey(columnIndexToSort, SortOrder.ASCENDING));
			sorter.setSortKeys(sortKeys);
			sorter.sort();
		}
	}

	static class ThemeDialog extends JDialog {
		private static final long serialVersionUID = 42L;

		public ThemeDialog(final Frame parent) {
			// Modal
			super(parent, "Theme Selector", true);
			setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			EscapeHandler.installEscapeCloseOperation(this);
			//
			getContentPane().add(new IJThemesPanel());
			// Prepare window.
			pack();
			setLocationRelativeTo(null);
			setMinimumSize(getSize());
		}

		public void init() {
			// Display the window.
			setVisible(true);
		}
	}

	static enum EditMode {
		ADD, EDIT, COPY;
	}
}
