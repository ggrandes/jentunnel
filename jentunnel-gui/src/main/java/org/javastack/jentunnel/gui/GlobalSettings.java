package org.javastack.jentunnel.gui;

import java.io.File;
import java.io.FileInputStream;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import org.javastack.jentunnel.gui.flatlaf.IJThemesPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.util.StringUtils;

public enum GlobalSettings {
	/**
	 * Config Directory
	 */
	CONFIG_DIRECTORY(null),
	/**
	 * LAF
	 */
	LAF(FlatDarculaLaf.class.getName()),
	/**
	 * LAF Theme
	 */
	LAF_THEME(""),
	/**
	 * UI Theme
	 */
	THEME_UI_KEY("jentunnel.theme"),
	/**
	 * Main Window Width
	 */
	MAIN_WINDOW_WIDTH("0"),
	/**
	 * Main Window Height
	 */
	MAIN_WINDOW_HEIGHT("0"),
	//
	;

	//
	private static final Preferences conf;
	private final String keyName;
	private final String defaultValue;

	static {
		conf = Preferences.userNodeForPackage(GlobalSettings.class);
	}

	GlobalSettings(final String defaultValue) {
		this.keyName = name().toLowerCase().replace('_', '.');
		this.defaultValue = defaultValue;
	}

	public String get() {
		return conf.get(keyName, defaultValue);
	}

	public int getInt() {
		return Integer.parseInt(get());
	}

	public long getLong() {
		return Long.parseLong(get());
	}

	public boolean getBoolean() {
		return Boolean.parseBoolean(get());
	}

	public void set(final String value) {
		conf.put(keyName, value);
	}

	public void setInt(final int value) {
		conf.putInt(keyName, value);
	}

	public void setLong(final long value) {
		conf.putLong(keyName, value);
	}

	public void setBoolean(final boolean value) {
		conf.putBoolean(keyName, value);
	}

	// GUI
	public static final String RESOURCE_PREFIX = "res:";
	public static final String FILE_PREFIX = "file:";
	private static final Logger log = LoggerFactory.getLogger(GlobalSettings.class);

	public static void initLaf() {
		// set look and feel
		try {
			String lafClassName = LAF.get(); // FlatLightLaf.class.getName();
			if (IntelliJTheme.ThemeLaf.class.getName().equals(lafClassName)) {
				String theme = LAF_THEME.get(); // ""
				if (theme.startsWith(RESOURCE_PREFIX)) {
					IntelliJTheme.setup(IJThemesPanel.class.getResourceAsStream(
							IJThemesPanel.THEMES_PACKAGE + theme.substring(RESOURCE_PREFIX.length())));
				} else if (theme.startsWith(FILE_PREFIX)) {
					FlatLaf.setup(IntelliJTheme
							.createLaf(new FileInputStream(theme.substring(FILE_PREFIX.length()))));
				} else {
					FlatDarculaLaf.setup();
				}
				if (!theme.isEmpty()) {
					UIManager.getLookAndFeelDefaults().put(THEME_UI_KEY.get(), theme);
				}
			} else if (FlatPropertiesLaf.class.getName().equals(lafClassName)) {
				String theme = LAF_THEME.get(); // ""
				if (theme.startsWith(FILE_PREFIX)) {
					File themeFile = new File(theme.substring(FILE_PREFIX.length()));
					String themeName = StringUtils.removeTrailing(themeFile.getName(), ".properties");
					FlatLaf.setup(new FlatPropertiesLaf(themeName, themeFile));
				} else {
					FlatDarculaLaf.setup();
				}
				if (!theme.isEmpty()) {
					UIManager.getLookAndFeelDefaults().put(THEME_UI_KEY.get(), theme);
				}
			} else {
				UIManager.setLookAndFeel(lafClassName);
			}
		} catch (Exception e) {
			log.error("Error loading LAF: {}", String.valueOf(e), e);
			// fallback
			FlatDarculaLaf.setup();
		}
		// Show all Mnemonics
		UIManager.put("Component.hideMnemonics", false);

		// remember active look and feel
		UIManager.addPropertyChangeListener(e -> {
			if ("lookAndFeel".equals(e.getPropertyName()))
				LAF.set(UIManager.getLookAndFeel().getClass().getName());
		});
	}
}
