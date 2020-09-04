package configuration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.bukkit.plugin.Plugin;

import exc.ConfigValueNotFound;
import exc.ConfigValueNotParsed;
import exc.ConfigurationException;
import web.ConfigurationHttpHandler;

public abstract class Configuration extends File implements Cloneable {
	private static final long serialVersionUID = 115L;

	private ConfigurationHttpHandler httpHandler = null;

	private String header;
	private Map<String, Object> settings;
	private Map<String, String> info;
	private String pluginName;

	public Configuration(Plugin plugin) {
		super("plugins/" + plugin.getName() + "/config.yml");
		settings = new HashMap<>();
		info = new HashMap<>();
		pluginName = plugin.getName();
	}

	public Configuration(Plugin plugin, String header) {
		super("plugins/" + plugin.getName() + "/config.yml");
		settings = new HashMap<>();
		info = new HashMap<>();
		this.header = header;
		pluginName = plugin.getName();
	}

	/**
	 * 
	 * @param port (Use \"0\" for any available port, you will know which one the web is using for your plugin)
	 * @throws IOException
	 * @throws ConfigurationException
	 */
	public void startServer(int port) throws IOException, ConfigurationException {
		if (httpHandler == null)
			httpHandler = new ConfigurationHttpHandler(port, pluginName, settings) {
				@Override
				public void updateSettings(Map<String, Object> newSettings) {
					settings = newSettings;
					try {
						saveConfig();
					} catch (IOException e) {
						e.printStackTrace();
					}
					onUpdatedByWeb();
				}
			};
		httpHandler.start();
	}

	public void stopServer() {
		if (httpHandler != null) {
			httpHandler.stop();
			httpHandler = null;
		}
	}
	
	public String getWebLink() {
		if (httpHandler != null) {
			return httpHandler.getWebLink();
		} else {
			return null;
		}
	}

	/**
	 * It's called when someone updated the configuration from the server. When this
	 * method is called, the new configuration is already saved in the configuration
	 * file. You can leave it blank if you won't use it.
	 */
	public abstract void onUpdatedByWeb();

	public void setHeader(String header) {
		this.header = header;
	}

	public String getHeader() {
		return header;
	}

	public void setValue(String key, Object value) {
		settings.put(key, value.toString());
	}

	public void setValue(String key, Object value, String info) {
		settings.put(key, value.toString());
		this.info.put(key, info);
	}

	public String getString(String key, String defaultValue) {
		return settings.getOrDefault(key, defaultValue).toString();
	}

	public String getString(String key) throws ConfigValueNotFound {
		String sol = settings.get(key).toString();
		if (sol == null)
			throw new ConfigValueNotFound("The key \"" + key + "\" was never set in the config file.");
		return sol;
	}

	public int getInt(String key, int defaultValue) {
		String str = settings.getOrDefault(key, defaultValue + "").toString();
		try {
			Double num = null;
			try {
				num = Double.parseDouble(str);
				return num.intValue();
			} catch (NumberFormatException e) {
				return Integer.parseInt(str);
			}
		} catch (NumberFormatException e) {
			System.err.println("Error trying to get integer value from config file");
			System.err.println("(Value \"" + str + "\" could not be parsed to integer)");
			return defaultValue;
		}
	}

	public int getInt(String key) throws ConfigValueNotFound, ConfigValueNotParsed {
		String str = settings.get(key).toString();
		if (str == null)
			throw new ConfigValueNotFound("The key \"" + key + "\" is not set in the config file.");
		try {
			Double num = null;
			try {
				num = Double.parseDouble(str);
				return num.intValue();
			} catch (NumberFormatException e) {
				return Integer.parseInt(str);
			}
		} catch (NumberFormatException e) {
			throw new ConfigValueNotParsed("Error trying to get integer value from config file (Value \"" + str
					+ "\" could not be parsed to integer)");
		}
	}

	public double getDouble(String key, double defaultValue) {
		String str = settings.getOrDefault(key, defaultValue + "").toString();
		try {
			return Double.parseDouble(str.replace(",", "."));
		} catch (NumberFormatException e) {
			System.err.println("Error trying to get double value from config file");
			System.err.println("(Value \"" + str + "\" could not be parsed to double)");
			return defaultValue;
		}
	}

	public double getDouble(String key) throws ConfigValueNotParsed, ConfigValueNotFound {
		String str = settings.get(key).toString();
		if (str == null)
			throw new ConfigValueNotFound("The key \"" + key + "\" was never set in the config file.");
		try {
			return Double.parseDouble(str.replace(",", "."));
		} catch (NumberFormatException e) {
			throw new ConfigValueNotParsed("Error trying to get double value from config file (Value \"" + str
					+ "\" could not be parsed to double)");
		}
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String str = settings.getOrDefault(key, defaultValue + "").toString();
		switch (str) {
		case "true":
		case "yes":
			return true;
		case "false":
		case "no":
			return false;

		default:
			System.err.println("Error trying to get boolean value from config file");
			System.err.println("(Value \"" + str + "\" could not be parsed to boolean)");
			return defaultValue;
		}
	}

	public boolean getBoolean(String key) throws ConfigValueNotFound, ConfigValueNotParsed {
		String str = settings.get(key).toString();
		if (str == null)
			throw new ConfigValueNotFound("The key \"" + key + "\" was never set in the config file.");
		switch (str) {
		case "true":
		case "yes":
			return true;
		case "false":
		case "no":
			return false;

		default:
			throw new ConfigValueNotParsed("Error trying to get boolean value from config file (Value \"" + str
					+ "\" could not be parsed to boolean)");
		}
	}

	public void setInfo(String key, String info) {
		this.info.put(key, info);
	}

	/**
	 * @throws IOException
	 * 
	 */
	public void saveConfig() throws IOException {
		String configTxt = header == null ? "" : "#\t" + header + "\n\n";
		Set<String> keys = settings.keySet();
		for (String key : keys) {
			String value = settings.get(key).toString();
			String info = this.info.get(key);
			if (info != null) {
				configTxt += "#" + info + "\n";
			}
			configTxt += key + ": " + value + "\n";
		}

		if (exists()) {
			delete();
		}
		try {
			getParentFile().mkdirs();
		} catch (NullPointerException e) {
		}
		createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(this));
		writer.write(configTxt);
		writer.close();
	}

	/**
	 * @throws IOException
	 * 
	 */
	public void reloadConfigFromFile() {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(this));
			String line;
			int cont = 0;
			while ((line = reader.readLine()) != null) {
				cont++;
				line = line.trim();
				if (!line.startsWith("#") && !line.trim().isEmpty()) {
					StringTokenizer st = new StringTokenizer(line, ":");
					if (st.countTokens() != 2) {
						reader.close();
						throw new IOException("Looks like the file content is not correct. Broken line " + cont + " ("
								+ st.countTokens() + " tokens, should be 2)");
					}
					String key = st.nextToken().trim();
					String value = st.nextToken().trim();
					setValue(key, value);
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			System.err.println("Configuration file not created yet. Skipping load.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
