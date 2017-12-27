import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;


public class NsComm {
	private LinkedList<Socket>            sock_list   = null;
	private LinkedBlockingQueue<NsDlMsg>  nsdlqueue   = null;
	private int                           port        = 0;
	private DBManager                     dbm         = null;
	private NsUlDecoder                   uldecoder   = null;
	private ArrayList<WebNsReq>           webnsgreq   = null;
	
	public NsComm(int p, DBManager db, ArrayList<WebNsReq> req) {
		// TODO Auto-generated constructor stub
		port        = p;
		dbm         = db;
		webnsgreq   = req;
		sock_list   = new LinkedList<Socket>();
		nsdlqueue   = new LinkedBlockingQueue<NsDlMsg>();
		uldecoder   = new NsUlDecoder(nsdlqueue, req, dbm);
		new Thread(uldecoder).start();
	}

	public void start() {
		NsListen listen=new NsListen(5000);
		NsUpLink uplink=new NsUpLink();
		NsDnLink dnlink=new NsDnLink();
		
		new Thread(listen).start();
		new Thread(uplink).start();
		new Thread(dnlink).start();
	}

	class NsListen implements Runnable {
		public NsListen(int p) {
			// TODO Auto-generated constructor stub
			port=p;
			sock_list    = new LinkedList<Socket>();
		}

		public void run() {
			// TODO Auto-generated method stub
			ServerSocket serv=null;

			try {
				serv = new ServerSocket(port);
			}
			catch (Exception e) {
				e.printStackTrace();
				return;
			}
			
			new Thread(new NsUpLink()).start();
			new Thread(new NsDnLink()).start();

			while(true) {
				try {
					Socket s=serv.accept();
					sock_list.add(s);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	class NsUpLink implements Runnable {

		public void run() {
			// TODO Auto-generated method stub
			while(true) {
				if (sock_list.isEmpty()){
					try {
						Thread.sleep(10);
					}
					catch(Exception e) {
						e.printStackTrace();
					}
					continue;
				}

				LinkedList<Socket> list     = (LinkedList<Socket>) sock_list.clone();
				Iterator<Socket>   iterator = list.iterator();

				Socket s;

				while (iterator.hasNext()) {
					s = iterator.next();

					if (s.isClosed()) {
						sock_list.remove(s);
						System.out.println("ns break");
						continue;
					}

					try {
						InputStream in  = s.getInputStream();
						int         len = in.available();
						if (len<=0) {
							continue;
						}

						byte[] buf  = new byte[len];
						NsUlMsg msg = new NsUlMsg(s);
						
						in.read(buf, 0, len);

						if (!msg.Load(buf)) {
							continue;
						}

						uldecoder.put(msg);
						// ������Ϣ
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						try {
							s.close();
							sock_list.remove(s);
							System.out.println("ns break");
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
			}
		}
	}

	class NsDnLink implements Runnable {

		public void run() {
			// TODO Auto-generated method stub
			while(true) {
				Socket   s     = null;
				WebNsReq dmsg  = null;
				NsDlMsg  dlmsg = null;
				
				HashMap<Long, Socket > nsconn_list = (HashMap<Long, Socket>) uldecoder.nsconn_list.clone();

				if (nsdlqueue.isEmpty() && webnsgreq.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					continue;
				}

				try {
					if (!nsdlqueue.isEmpty()) {
						dlmsg = nsdlqueue.poll();
	
						if (dlmsg!=null && nsconn_list.containsKey(dlmsg.id)) {
							s = nsconn_list.get(dlmsg.id);
							s.getOutputStream().write(dlmsg.getBytes());
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					try {
						s.close();
						nsconn_list.remove(dlmsg.id);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}

				try {
					if (webnsgreq.isEmpty()) {
						continue;
					}

					ArrayList<WebNsReq> list     = (ArrayList<WebNsReq>) webnsgreq.clone();
					Iterator<WebNsReq>  itorator = list.iterator();

					while(itorator.hasNext()) {
						dmsg = itorator.next();
						if (!nsconn_list.containsKey(dmsg.dlmsg.id)) {
							dmsg.state = WebNsReq.STATE_FAIL;
							continue;
						}

						if (dmsg.state==WebNsReq.STATE_WAIT) {
							s = nsconn_list.get(dmsg.dlmsg.id);
							s.getOutputStream().write(dmsg.dlmsg.getBytes());
							dmsg.state = dmsg.confirm?WebNsReq.STATE_SEND:WebNsReq.STATE_ACK;
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					dmsg.state = WebNsReq.STATE_FAIL;
					try {
						s.close();
						nsconn_list.remove(dmsg.dlmsg.id);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				
				if (nsdlqueue.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
}
