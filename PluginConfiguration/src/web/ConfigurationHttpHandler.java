package web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import exc.ConfigurationException;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public abstract class ConfigurationHttpHandler implements HttpHandler, Listener {
	private HttpServer serverWeb;
	private String html, title;
	protected int port;

	public ConfigurationHttpHandler(int port, String pluginName, Map<String, Object> currentSettings) throws IOException, ConfigurationException {
		InputStream is = getClass().getResourceAsStream("/web/config.html");
		if (is == null) {
			throw new NullPointerException("Config html file not found, web server could not be opened");
		} else {
			is.close();
			title = pluginName;
			updateHTML(currentSettings);
			
			try {

				serverWeb = HttpServer.create(new InetSocketAddress(port), 0);
				serverWeb.setExecutor(null);
				serverWeb.createContext("/", this);
				this.port = serverWeb.getAddress().getPort();
				System.out.println("[CrisWebConfiguration] Server started for \""+pluginName+"\", you can use http://localhost:"+this.port+"/ to edit its configuration.");
			} catch (BindException e) {
				throw new ConfigurationException("Port "+port+" is already been used, try another one (try something like \"8888\")");
			}
		}
	}
	
	public void updateHTML(Map<String, Object> currentSettings) throws IOException {
		InputStream is = getClass().getResourceAsStream("/web/config.html");
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder result = new StringBuilder();
		boolean flag = false;
		for (String line; (line = reader.readLine()) != null;) {
			result.append(flag ? System.getProperty("line.separator") : "").append(line);
			flag = true;
		}
		reader.close();
		String htmlString = result.toString();
		
		StringBuilder settingsHtml = new StringBuilder();
		currentSettings.forEach((name, value) -> {
			if (value.toString().equals("true") || value.toString().equals("false")) {
//				System.out.println("[CrisWebConfiguration] "+name+": "+value+" -> boolean");
				settingsHtml.append(getInputCheckbox(name, name, Boolean.parseBoolean(value.toString())));
			} else if (value instanceof Double) {
//				System.out.println("[CrisWebConfiguration] "+name+": "+value+" -> Double");
				settingsHtml.append(getInputNumber(name, name, (Double)value));
			} else {
//				System.out.println("[CrisWebConfiguration] "+name+": "+value+" -> String");
				settingsHtml.append(getInputText(name, name, value.toString()));
			}
		});
		html = htmlString.replace("$title", title).replace("$settings", settingsHtml);
	}
	
	public void start() {
		serverWeb.start();
	}
	
	public void stop() {
		serverWeb.stop(0);
	}
	
	@EventHandler
	private void onPluginDisable(PluginDisableEvent e) {
		serverWeb.stop(0);
		System.out.println(e.getPlugin().getName()+" - Stopped configuration server");
	}
	
	public abstract void updateSettings(Map<String, Object> newSettings);

	@Override
	public void handle(HttpExchange t) throws IOException {
		StringBuilder sb = new StringBuilder();
		InputStream is = t.getRequestBody();
		int i;
		boolean newVar = false, readName = true;
		HashMap<String, Object> postData = new HashMap<>();
		{
			String varName = "", varData = "";
//			while ((i = is.read()) != -1) {
			while (true) {
				i = is.read();
				sb.append((char) i);
				if (readName) {
					if ((char) i == '=') {
						readName = false;
					} else {
						varName += (char) i;
					}
				} else {
					if ((char) i == '&' || i == -1) {
						newVar = true;
					} else if ((char) i == '+') {
						varData += " ";
					} else {
						varData += (char) i;
					}
				}

				if (newVar) {
					if (varData=="true" || varData=="false") {
						postData.put(varName, Boolean.parseBoolean(varData));
					} else {
						postData.put(varName, varData);
					}
					varName = "";
					varData = "";
					newVar = false;
					readName = true;
				}

				if (i == -1) {
					break;
				}
			}
		}
//		System.out.println("Data: " + sb.toString());
//		postData.forEach((k, v) -> System.out.println(("Name: \"" + k + "\" Value: \"" + v + "\"")));
		
		if (postData.size() > 0) {
			updateSettings(postData);
			updateHTML(postData);
		}

		t.sendResponseHeaders(200, html.length());
		OutputStream os = t.getResponseBody();
		os.write(html.getBytes());
		os.close();
		
		updateSettings(postData);
	}
	
	private String getInputText(String display, String name, String value) {
		return "<div class=\"form-group\">" 
				+ "    <label for=\"id-"+name+"\">"+display+"</label>"
				+ "	<input type=\"text\" class=\"form-control\" name=\""+name+"\" id=\"id-"+name+"\" value=\""+value+"\">" 
				+ "</div>";
	}
	
	private String getInputCheckbox(String display, String name, boolean value) {
		return "<div class=\"form-check\">"
				+"<label class=\"form-check-label\">"
				+    "<input type=\"hidden\" name=\""+name+"\" value=\"false\">"
				+    "<input type=\"checkbox\" class=\"form-check-input\" name=\""+name+"\" value=\"true\" "+(value?"checked":"")+">"+display
				+"</label>"
				+"</div>";
	}
	
	private String getInputNumber(String display, String name, double value) {
		return "<div class=\"form-group\">" 
				+ "    <label for=\"id-"+name+"\">"+display+"</label>"
				+ "	<input type=\"number\" class=\"form-control\" name=\""+name+"\" id=\"id-"+name+"\" value=\""+Double.toString(value)+"\">" 
				+ "</div>";
	}

	public String getWebLink() {
		return "http://localhost:"+port+"/";
	}

//	private String getSelect(String display, String name, List<? extends Object> values) {
//		String ret = "<div class=\"form-group\">"
//				+ "    <label for=\"id-name\">"+name
//				+ "    </label>"
//				+ "    <select class=\"form-control\" name=\"name\" id=\"id-name\">";
//		for (Object object : values) {
//			ret+="<option>"+object.toString()+"</option>";
//		}
//		ret += "</select></div>";
//		return ret;
//	}
}
