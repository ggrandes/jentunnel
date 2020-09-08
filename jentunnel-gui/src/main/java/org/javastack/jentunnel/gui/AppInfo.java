package org.javastack.jentunnel.gui;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppInfo {
	private static final AppInfo INSTANCE = new AppInfo();

	public final String iam, groupId, artifactId, version, url;

	public static final AppInfo getInstance() {
		return INSTANCE;
	}

	private AppInfo() {
		Properties p = loadProperties("/info.properties");
		this.iam = p.getProperty("iam");
		this.groupId = p.getProperty("groupId");
		this.artifactId = p.getProperty("artifactId");
		this.version = p.getProperty("version");
		this.url = p.getProperty("url");
	}

	private static final Properties loadProperties(final String location) {
		final Properties p = new Properties();
		try (final InputStream is = AppInfo.class.getResourceAsStream(location)) {
			p.load(is);
		} catch (IOException e) {
			throw new IOError(e);
		}
		return p;
	}
}