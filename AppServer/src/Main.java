import java.util.ArrayList;

public class Main {
	public static void main(String[] args){
		ArrayList<WebNsReq> webnsreqqueue = new ArrayList<WebNsReq>();
		int port = 5000;

		DBManager.url    = "jdbc:MySQL://localhost:3306/test";
		if (args.length>=2) {
			DBManager.user   = args[0];
			DBManager.passwd = args[1];
			System.out.println("username:"+DBManager.user+",password:"+DBManager.passwd);
			if (args.length>2) {
				port = new Integer(args[2]);
			}
		}
		else {
			DBManager.user   = "";
			DBManager.passwd = "";
		}

		System.out.println("port for ns:"+String.valueOf(port));
		DBManager db = new DBManager();
		
		if (!db.connect()) {
			System.out.println("connect database failed!");

			return;
		}

		NsComm  nscomm  = new NsComm(port, db, webnsreqqueue);
		WebComm webcomm = new WebComm(1935, webnsreqqueue);

		nscomm.start();
		webcomm.start();
		
		while(true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
