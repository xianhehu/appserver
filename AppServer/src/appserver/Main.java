package appserver;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Main {
	public  static Logger logger = Logger.getLogger("appserver");
	private static String beanPath = "/etc/appserver";
//	private static String beanPath = "C:/appserver";

	private static void argshandle(String[] args) {
		String cmd = null;

		for (int i = 0; i < args.length; i ++) {
			if (cmd == null) {
				if (!args[i].contains("-")) {
					return;
				}

				cmd = args[i];

				continue;
			}

			if (cmd.equals("-b")) {
				beanPath = args[i];
			}

			cmd = null;
		}
	}

	public static void main(String[] args){
		argshandle(args);

//		ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
		ApplicationContext context = new FileSystemXmlApplicationContext(beanPath+"/beans.xml");

//		NsComm  nscomm  = new NsComm(port, db, webnsreqqueue);
//		WebComm webcomm = new WebComm(1935, webnsreqqueue);

		logger.debug("start");
		NsComm  nscomm  = (NsComm) context.getBean("NsComm");
		WebComm webcomm = (WebComm)context.getBean("WebComm");

		nscomm.start();
		webcomm.start();

		while(true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.error("", e);
			}
		}
	}
}
