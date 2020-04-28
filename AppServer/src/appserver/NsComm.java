package appserver;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

import org.apache.log4j.Logger;

public class NsComm {
	private List<Socket>    sock_list   = new ArrayList<>();
	private NsDlMsg         nsdlqueue   = null;
	private NsUlMsg         nsulqueue   = null;
	private int             port        = 0;
	private NsUlDecoder     uldecoder   = null;
	private WebNsReq        webnsreq    = null;
	//private static Logger   logger = Logger.getLogger("appserver");

	public NsComm() {
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setWebnsreq(WebNsReq webnsreq) {
		this.webnsreq = webnsreq;
	}

	public void setNsdlqueue(NsDlMsg nsdlqueue) {
		this.nsdlqueue = nsdlqueue;
	}

	public void setNsulqueue(NsUlMsg nsulqueue) {
		this.nsulqueue = nsulqueue;
	}

	public void setUldecoder(NsUlDecoder uldecoder) {
		this.uldecoder = uldecoder;
	}

	public void start() {
		NsListen listen=new NsListen();
		NsUpLink uplink=new NsUpLink();
		NsDnLink dnlink=new NsDnLink();

		new Thread(uldecoder).start();
		new Thread(listen).start();
		new Thread(uplink).start();
		new Thread(dnlink).start();
	}

	class NsListen implements Runnable {
		public void run() {
			ServerSocket serv=null;

			try {
				serv = new ServerSocket(port);
			}
			catch (Exception e) {
				Main.logger.error("", e);

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
					Main.logger.error("", e);
				}
			}
		}
	}

	class NsUpLink implements Runnable {

		public void run() {
			byte[] buf  = new byte[4096];

			while(true) {
				if (sock_list.isEmpty()){
					try {
						Thread.sleep(10);
					}
					catch(Exception e) {
						Main.logger.error("", e);
					}

					continue;
				}

				List<Socket> list     = new ArrayList<>(sock_list);
				Iterator<Socket>   iterator = list.iterator();

				Socket s;

				while (iterator.hasNext()) {
					s = iterator.next();

					if (s.isClosed()) {
						sock_list.remove(s);
						Main.logger.warn("ns套接字关闭了");

						continue;
					}

					try {
						InputStream in  = s.getInputStream();
						int         len = in.available();

						if (len<=0) {
							continue;
						}
						
						do {
							Thread.sleep(200);
							
							int curlen = in.available();
							
							if (curlen == len)
								break;
							
							len = curlen;
						} while(true);

						len = in.read(buf, 0, buf.length);
						if (len <= 0) {
							Main.logger.error("NS读取失败\n");
							continue;
						}

						List<NsUlMsg> msgs = NsUlMsg.Load(buf, len, s);

						if (msgs.size() < 1) {
							Main.logger.error("NS上行加载失败，长度:"+len+"\n");
							continue;
						}

						Main.logger.debug("收到NS上行消息，放到消息处理队列");

						for (NsUlMsg m : msgs) {
							nsulqueue.put(m);
						}
						// 处理消息
					} catch (IOException e) {
						Main.logger.error("", e);

						try {
							s.close();
							sock_list.remove(s);
							Main.logger.warn("ns break");
						} catch (IOException e1) {
							Main.logger.error("", e1);
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}

	class NsDnLink implements Runnable {

		public void run() {
			while(true) {
				Socket   s     = null;
				WebNsReq dmsg  = null;
				NsDlMsg  dlmsg = null;

				Map<Long, Socket > nsconn_list = new HashMap<>();

				nsconn_list.putAll(uldecoder.nsconn_list);

				if (nsdlqueue.isEmpty() && webnsreq.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Main.logger.error("", e);
					}

					continue;
				}

				try {
					if (!nsdlqueue.isEmpty()) {
						dlmsg = nsdlqueue.poll();

						if (dlmsg!=null && nsconn_list.containsKey(dlmsg.id)) {
							s = nsconn_list.get(dlmsg.id);
							s.getOutputStream().write(dlmsg.getBytes());
							Main.logger.debug("下行消息发送到NS"+dlmsg.id);
						}
					}
				} catch (IOException e) {
					Main.logger.error("", e);

					try {
						s.close();
						nsconn_list.remove(dlmsg.id);
					} catch (IOException e1) {
						Main.logger.error("", e1);
					}
				}

				try {
					if (webnsreq.isEmpty()) {
						continue;
					}

					List<WebNsReq>      list     = webnsreq.getWebNsReq();
					Iterator<WebNsReq>  itorator = list.iterator();

					while(itorator.hasNext()) {
						dmsg = itorator.next();

						if (!nsconn_list.containsKey(dmsg.dlmsg.id)) {
							dmsg.state = WebNsReq.STATE_FAIL;
							Main.logger.warn("下行消息发送到NS"+dmsg.dlmsg.id+"失败");

							continue;
						}

						if (dmsg.state==WebNsReq.STATE_WAIT) {
							s = nsconn_list.get(dmsg.dlmsg.id);
							s.getOutputStream().write(dmsg.dlmsg.getBytes());
							dmsg.state = dmsg.confirm?WebNsReq.STATE_SEND:WebNsReq.STATE_ACK;
						}
					}
				} catch (Exception e) {
					Main.logger.error("", e);
					dmsg.state = WebNsReq.STATE_FAIL;

					try {
						s.close();
						nsconn_list.remove(dmsg.dlmsg.id);
					} catch (IOException e1) {
						Main.logger.error("", e1);
					}
				}

				if (nsdlqueue.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Main.logger.error("", e);
					}
				}
			}
		}
	}
}
