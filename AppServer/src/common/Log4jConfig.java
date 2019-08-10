package common;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Log4jConfig {
//	private String log4jPath = "";
//	private int interval = 60000;
//	private boolean reload = true;
	private Logger log = Logger.getRootLogger();
	
	public Log4jConfig(String path, int interval, boolean reload) {
		if (reload) {
			PropertyConfigurator.configureAndWatch(path, interval);
		}
		else {
			PropertyConfigurator.configureAndWatch(path);
		}
	}
}
