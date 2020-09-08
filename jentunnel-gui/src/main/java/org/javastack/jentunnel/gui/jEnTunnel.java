/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.javastack.jentunnel.gui;

import java.awt.SystemTray;
import java.awt.Window;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.javastack.jentunnel.SSHClient;
import org.javastack.jentunnel.gui.WindowedGUI.GlobalSettingsDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class jEnTunnel {
	private static final Logger log = LoggerFactory.getLogger(jEnTunnel.class);
	private static SSHClient client;

	public static void main(String[] args) throws Throwable {
		final boolean disableTrayApp = Boolean.getBoolean("jentunnel.tray.disable");
		final boolean isTrayApp = SystemTray.isSupported() && !disableTrayApp;
		if (!SystemTray.isSupported()) {
			System.out.println("SystemTray is not supported");
		}
		// Ordered shutdown on Uncaught Exceptions
		ExitProcessOnUncaughtException.register();
		// Enable window decorations
		JFrame.setDefaultLookAndFeelDecorated(true);
		JDialog.setDefaultLookAndFeelDecorated(true);
		// System.setProperty("flatlaf.animatedLafChange", "false");
		// FlatDarkLaf.install();
		// FlatLightLaf.install();
		GlobalSettings.initLaf();
		// Global Lock (machine)
		if (!GlobalLock.tryGetLockFile()) {
			final JDialog phantom = new JDialog() {
				private static final long serialVersionUID = 42L;

				{
					setIconImages(Resources.mainIcons);
				}
			};
			JOptionPane.showMessageDialog(phantom, //
					"Program is already running", //
					Resources.appInfo.iam, //
					JOptionPane.ERROR_MESSAGE);
			phantom.dispose();
			System.exit(2);
		}
		// Initial setup: directory configuration
		if (GlobalSettings.CONFIG_DIRECTORY.get() == null) {
			new GlobalSettingsDialog(null).init();
			if (GlobalSettings.CONFIG_DIRECTORY.get() == null) {
				final JDialog phantom = new JDialog() {
					private static final long serialVersionUID = 42L;

					{
						setIconImages(Resources.mainIcons);
					}
				};
				JOptionPane.showMessageDialog(phantom, //
						"Invalid configuration directory", //
						Resources.appInfo.iam, //
						JOptionPane.ERROR_MESSAGE);
				phantom.dispose();
				System.exit(1);
			}
		}
		// Startup
		if (isTrayApp) {
			// Name log file in temporal directory
			final File logFile = getLogFile().getCanonicalFile();
			// Simple rotate
			if (logFile.exists()) {
				final File oldFile = new File(logFile.getAbsolutePath() + ".old");
				oldFile.delete();
				logFile.renameTo(oldFile);
			}
			final PrintStream out = System.out;
			final PrintStream err = System.err;
			try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
				// Redirect standard logging/stdout to WatchablePrintStream
				System.setOut(new WatchablePrintStream(new PrintStream(fos), 100));
				System.setErr(System.out);
				log.info("Logging to file: {}", logFile);
				initClient();
				final TrayGUI tray = new TrayGUI(client);
				tray.init();
				// Launch auto-start connections...
				client.start();
				tray.handleEvents();
			} finally {
				Arrays.asList(Window.getWindows()).forEach(e -> e.dispose());
				System.setOut(out);
				System.setErr(err);
			}
		} else {
			initClient();
			SwingUtilities.invokeLater(() -> {
				new WindowedGUI(client, false).init();
			});
			// Launch auto-start connections...
			client.start();
		}
	}
	
	private static final void initClient() throws IOException {
		client = new SSHClient(GlobalSettings.CONFIG_DIRECTORY.get());
		final AskUserGUI asker = new AskUserGUI();
		client.setFilePasswordProvider(asker);
		client.setKeyAcceptatorNew(asker);
		client.setKeyAcceptatorModified(asker);
		client.init();
	}

	private static final String getBaseFileName() {
		return Resources.appInfo.iam.toLowerCase() + "." + System.getProperty("user.name", "unknown");
	}

	private static final File getLogFile() {
		return new File(System.getProperty("java.io.tmpdir", "."), getBaseFileName() + ".log");
	}
}
