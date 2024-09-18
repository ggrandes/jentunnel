package org.javastack.jentunnel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class ConfigUtils {
	private static final String DEFAULT_FILE_NAME = ConfigUtils.class.getPackage().getName() + ".config.yaml";
	private final String configDirectory;

	public ConfigUtils(final String configDirectory) {
		this.configDirectory = configDirectory;
	}

	public ConfigData load() throws IOException {
		return load(configDirectory, DEFAULT_FILE_NAME);
	}

	public void save(final ConfigData cfg) throws IOException {
		save(configDirectory, DEFAULT_FILE_NAME, cfg);
	}

	protected ConfigData load(final String configDirectory, final String file) throws IOException {
		final File f = new File(configDirectory, file);
		if (f.exists()) {
			return readFileYAML(f).updateHightWaterMark();
		} else {
			return new ConfigData();
		}
	}

	protected void save(final String configDirectory, final String file, final ConfigData data)
			throws IOException {
		final File f = new File(configDirectory, file);
		if (f.exists()) {
			final File ff = new File(configDirectory, file + ".old");
			if (ff.exists()) {
				ff.delete();
			}
			f.renameTo(ff);
		}
		saveFileYAML(f, data);
	}

	protected static Yaml getYaml() {
		// https://yaml.org/
		// https://bitbucket.org/asomov/snakeyaml/wiki/Documentation
		// https://bitbucket.org/asomov/snakeyaml-engine/wiki/Documentation
		// https://github.com/EsotericSoftware/yamlbeans
		final LinkedHashMap<Class<?>, String> map = new LinkedHashMap<Class<?>, String>() {
			private static final long serialVersionUID = 42L;

			{
				put(ConfigData.class, "!config");
				put(Forward.class, "!forward");
				put(Forward.Dynamic.class, "!dynamic");
				put(Forward.Local.class, "!local");
				put(Forward.Remote.class, "!remote");
			}
		};
		final DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setLineBreak(LineBreak.WIN);
		options.setAllowUnicode(false);
		options.setIndicatorIndent(2);
		options.setIndent(4);
		final Constructor constructor = new Constructor(ConfigData.class, new LoaderOptions());
		final Representer representer = new Representer(options);
		for (final Entry<Class<?>, String> e : map.entrySet()) {
			constructor.addTypeDescription(new TypeDescription(e.getKey(), e.getValue()));
			representer.addClassTag(e.getKey(), new Tag(e.getValue()));
		}
		return new Yaml(constructor, representer, options);
	}

	protected static void saveFileYAML(final File file, final ConfigData data) throws IOException {
		try (final FileWriter out = new FileWriter(file)) {
			final Yaml yaml = getYaml();
			out.write("# Creation date: ");
			out.write(getTimestamp());
			out.write(" #\r\n");
			yaml.dump(data, out);
			out.write("# END #\r\n");
			out.flush();
		}
	}

	protected static final String getTimestamp() {
		return new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSS").format(new Date());
	}

	protected static ConfigData readFileYAML(final File file) throws IOException {
		try (final FileReader in = new FileReader(file)) {
			final Yaml yaml = getYaml();
			return yaml.load(in);
		}
	}
}
