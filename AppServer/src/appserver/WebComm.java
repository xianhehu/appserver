package appserver;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.log4j.Logger;
import com.google.gson.*;
import common.Common;
import common.DBManager;
import appserver.Main;

public class WebComm {
	private int                      port          = 5001;
	private List<Socket>             sock_list     = null;
	private DBManager                dbm           = null;
	private WebNsReq                 webnsreqlist  = null;
	private WebNsDevUptReq           webnsuptlist  = null;
	private BlockingQueue<WebReq>    webreqqueue   = null;
	private BlockingQueue<WebAck>    webackqueue   = null;
	private UserUpt                  userupt       = null;
	//private static Logger logger = Logger.getLogger("appserver");

	private final static int FREQ_TYPE_CUSTOM = 3;

/*	public WebComm(int p,
			       ArrayList<WebNsReq>  list1) {
		port          = p;
		webnsreqlist  = list1;
		sock_list     = new ArrayList<Socket>();
		webreqqueue   = new LinkedBlockingQueue<WebReq>();
		webackqueue   = new LinkedBlockingQueue<WebAck>();
		webnsuptlist  = new ArrayList<>();
	}*/

	public WebComm() {
		sock_list     = new ArrayList<Socket>();
		webreqqueue   = new LinkedBlockingQueue<WebReq>();
		webackqueue   = new LinkedBlockingQueue<WebAck>();
//		webnsuptlist  = new ArrayList<>();
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setDbm(DBManager dbm) {
		this.dbm = dbm;
	}

	public void setUserupt(UserUpt userupt) {
		this.userupt = userupt;
	}

	public void setWebnsreqlist(WebNsReq webnsreqlist) {
		this.webnsreqlist = webnsreqlist;
	}

	public void setWebnsuptlist(WebNsDevUptReq webnsuptlist) {
		this.webnsuptlist = webnsuptlist;
	}

	public void start() {
		new Thread(new WebListen()).start();
		new Thread(new WebDnLink()).start();
		new Thread(new WebReqHandle()).start();
		new Thread(new WebReqTimeout()).start();
		new Thread(new WebUpLink()).start();
	}

	private JsonArray getDevsData(String SessId, DBManager dbm) {
		JsonArray devs = new JsonArray();
		String    sql  = "select d.* from devs d, userdev u, session s where d.DevEUI=u.DevEUI and u.ID=s.ID and s.SessId=\""+SessId+"\""
				+ " order by d.UpdateTime desc";
		ResultSet ret  = dbm.query(sql);

		if (ret == null) {
			Main.logger.error("会话"+SessId+"对应的用户没有设备");

			return null;
		}

		try {
			while(ret.next()) {
				JsonObject dev = new JsonObject();

				dev.addProperty("DevEUI"  , ret.getLong("DevEUI"));
				dev.addProperty("GwID"    , ret.getLong("GwId"));
				dev.addProperty("DevAddr" , ret.getLong("DevAddr"));
				dev.addProperty("FPort"   , ret.getLong("FPort"));
				dev.addProperty("FcntDown", ret.getLong("FcntDown"));
				dev.addProperty("FcntUp"  , ret.getLong("FcntUp"));
				dev.addProperty("ULFreq"  , ret.getLong("ULFreq"));
				dev.addProperty("Tmst"    , ret.getLong("Tmst"));
				dev.addProperty("RSSI"    , ret.getLong("RSSI"));
				dev.addProperty("SNR"     , ret.getFloat("SNR"));
				dev.addProperty("DataRate", ret.getString("DataRate"));
				dev.addProperty("CodeRate", ret.getString("CodeRate"));
				dev.addProperty("RecvTime", ret.getString("RecvTime"));
				dev.addProperty("AppData" , ret.getString("AppData"));

				devs.add(dev);
			}
		}
		catch (Exception e) {
			Main.logger.error("", e);
		}

		return devs;
	}

	private JsonArray getGwsData(String SessId, DBManager dbm) {
		JsonArray gws = new JsonArray();
		String sql    = "select d.* from gws d, usergw u, session s where d.GwID=u.GwID and u.ID=s.ID and s.SessId=\""+SessId+"\""
				+ " order by d.UpdateTime desc";
		ResultSet ret = dbm.query(sql);

		if (ret==null) {
			Main.logger.error("会话"+SessId+"对应的用户没有网关");

			return null;
		}

		try {
			while(ret.next()) {
				JsonObject gw = new JsonObject();

				gw.addProperty("GwID", ret.getLong("GwID"  ));
				gw.addProperty("Time", ret.getString("Time"));
				gw.addProperty("Lati", ret.getFloat("Lati" ));
				gw.addProperty("Long", ret.getFloat("Longi"));
				gw.addProperty("Alti", ret.getLong("Alti")  );
				gw.addProperty("RxNb", ret.getLong("RxNb")  );
				gw.addProperty("RxOK", ret.getLong("RxOK")  );
				gw.addProperty("RxFw", ret.getLong("RxFw")  );
				gw.addProperty("AckR", ret.getLong("AckR")  );
				gw.addProperty("DwNb", ret.getLong("DwNb")  );
				gw.addProperty("TxNb", ret.getLong("TxNb")  );

				gws.add(gw);
			}
		}
		catch (Exception e) {
			Main.logger.error("", e);
		}

		return gws;
	}

	class WebListen implements Runnable {

		@Override
		public void run() {
			ServerSocket serv = null;

			try {
				serv = new ServerSocket(port);
			} catch (Exception e) {
				Main.logger.error("", e);

				return;
			}

			while (true) {
				try {
					Socket s = serv.accept();

					if (!sock_list.isEmpty()) {
						List<Socket> list     = new ArrayList<>(sock_list);
						Iterator<Socket>  iterator = list.iterator();
						Socket s1 = null;

						while (iterator.hasNext()) {
							s1 = iterator.next();

							if (s1.isClosed()) {
								sock_list.remove(s1);
							}
						}
					}

					sock_list.add(s);
				} catch (Exception e) {
					Main.logger.error("", e);
				}
			}
		}
	}

	class WebUpLink implements Runnable {
		@Override
		public void run() {

			while(true) {
				if (webackqueue.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Main.logger.error("", e);
					}

					continue;
				}

				WebAck weback = webackqueue.poll();

				try {
					OutputStream out = weback.sock.getOutputStream();

					Main.logger.debug("web请求回复:"+weback.jmsg);

					out.write(weback.jmsg.getBytes());
				} catch (IOException e) {
					Main.logger.error("", e);
				}
			}
		}
	}

	class WebDnLink implements Runnable {
		@Override
		public void run() {
			while (true) {
				List<Socket> list = new ArrayList<>(sock_list);

				if (list.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Main.logger.error("", e);
					}

					continue;
				}

				Iterator<Socket> iterator = list.iterator();
				Socket s = null;

				while (iterator.hasNext()) {
					s = iterator.next();
					if (s==null || s.isClosed()) {
						continue;
					}

					try {
						InputStream in = s.getInputStream();
						int len = in.available();
						if (len<=0) {
							continue;
						}

						byte[] buf = new byte[len];
						in.read(buf, 0, len);
   						String     str    = new String(buf);
						System.out.println(str);
						JsonObject jreq   = new JsonParser().parse(str).getAsJsonObject();
						WebReq     webreq = new WebReq();

						webreq.sock = s;
						webreq.jmsg = jreq;

						Main.logger.debug("web请求转发");

						webreqqueue.add(webreq);
					} catch (Exception e) {
						Main.logger.error("", e);

						try {
							s.close();
						} catch (Exception e1) {
							Main.logger.error("", e1);
						}
					}
				}
			}
		}
	}

	class WebReqHandle implements Runnable {

		public WebReqHandle() {

		}

		boolean validSession(WebReq webreq) {
			if (!(webreq.jmsg.has("SessId")&&webreq.jmsg.has("SessKey"))) {
				invalidReq(webreq);
				Main.logger.error("无效的web请求:"+webreq.jmsg);

				return false;
			}

			String SessId   = webreq.jmsg.get("SessId").getAsString();
			String SessKey  = webreq.jmsg.get("SessKey").getAsString();

			String sql = String.format("select SessKey from session where SessId=\"%s\"", SessId);

			try {
				ResultSet ret = dbm.query(sql);

				if (ret==null || ret.wasNull()) {
					Main.logger.error("没有会话"+SessId+"的连接记录");

					return false;
				}

				WebAck     weback = new WebAck();
				JsonObject jmsg   = new JsonObject();
				boolean    error  = false;

				weback.sock = webreq.sock;

				try {
					if (!ret.next()) {
						jmsg.addProperty("Status", 0);
						jmsg.addProperty("Error", "Session don't exist");
						error = true;
					}
					else if (!SessKey.equals(ret.getString("SessKey"))) {
						jmsg.addProperty("Status", 0);
						jmsg.addProperty("Error", "Session Key error");
						error = true;
					}
				} catch (SQLException e) {
					Main.logger.error("", e);
					jmsg.addProperty("Status", 0);
					jmsg.addProperty("Error", "Session don't exist");
					error = true;
				}

				if (!error) {
					Main.logger.debug("会话"+SessId+"信息OK");

					return true;
				}

				weback.jmsg=jmsg.getAsString();
				webackqueue.add(weback);

				Main.logger.error("会话"+SessId+"信息无效");

			} catch (Exception e) {
				Main.logger.error("", e);
			}

			return false;
		}

		void invalidReq(WebReq req) {
			WebAck     ack  = new WebAck();
			JsonObject jmsg = new JsonObject();

			jmsg.addProperty("Status", 0);
			jmsg.addProperty("Error", "request format invalid!");

			ack.sock = req.sock;
			ack.jmsg = jmsg.toString();

			webackqueue.add(ack);
		}

		void login(WebReq webreq) {
			if (!(webreq.jmsg.has("Username")&&webreq.jmsg.has("Password")
			    &&webreq.jmsg.has("SessId")&&webreq.jmsg.has("SessKey"))) {
				invalidReq(webreq);
				Main.logger.error("错误的登录请求信息:"+webreq.jmsg);

				return;
			}

			String Username = webreq.jmsg.get("Username").getAsString();
			String Password = webreq.jmsg.get("Password").getAsString();
			String SessId   = webreq.jmsg.get("SessId").getAsString();
			String SessKey  = webreq.jmsg.get("SessKey").getAsString();

			String sql = String.format("select Password, ID from user where Username=\"%s\"", Username);

			ResultSet ret = dbm.query(sql);

			if (ret==null) {
				Main.logger.error("没有用户"+Username+"的注册信息");

				return;
			}

			WebAck     weback = new WebAck();
			JsonObject jmsg   = new JsonObject();

			weback.sock = webreq.sock;

			try {
				if (!ret.next()) {
					jmsg.addProperty("Status", 0);
					jmsg.addProperty("Error", "Username don't exist");
				}
				else if (!Password.equals(ret.getString("Password"))) {
					jmsg.addProperty("Status", 0);
					jmsg.addProperty("Error", "Password error");
				}
				else {
					long ID = ret.getLong("ID");
					sql = String.format("select count(*) as RowCount from session where SessId=\"%s\"", SessId);

					ret = dbm.query(sql);

					if (ret==null) {
						Main.logger.error("没有会话"+SessId+"的记录");

						return;
					}

					ret.next();
					int count = ret.getInt("RowCount");

					if (count>0) {
						sql = String.format("update session set SessKey=\"%s\" where SessId=\"%s\"", SessKey, SessId);
					}
					else {
						sql = String.format("insert into session values (\"%s\", \"%s\", %d)", SessId, SessKey, ID);
					}

					dbm.query(sql);
					jmsg.addProperty("Status", 1);
				}
			} catch (Exception e) {
				Main.logger.error("", e);
				jmsg.addProperty("Status", 0);
				jmsg.addProperty("Error", "Username don't exist");
			}

			weback.jmsg = jmsg.toString();
			webackqueue.add(weback);
			Main.logger.debug("web登录响应:"+jmsg);
		}

		void getDevs(WebReq webreq) {
			WebAck    ack  = new WebAck();
			String SessId  = webreq.jmsg.get("SessId").getAsString();
			JsonArray datas= getDevsData(SessId, dbm);

			if (datas==null) {
				Main.logger.error("获取设备数据失败");

				return;
			}

			ack.sock       = webreq.sock;
			ack.jmsg       = datas.toString();

			webackqueue.add(ack);
		}

		void uptDevs(WebReq webreq) {
			if (!webreq.jmsg.has("Timeout")
			  ||!webreq.jmsg.has("SessId")) {
				invalidReq(webreq);
				Main.logger.error("无效的设备更新请求:"+webreq.jmsg);

				return;
			}

			long           timeout = webreq.jmsg.get("Timeout").getAsLong();
			String         SessId  = webreq.jmsg.get("SessId").getAsString();
			WebNsDevUptReq req     = new WebNsDevUptReq();

			req.sock    = webreq.sock;
			req.SessId  = SessId;
			req.timeout = timeout;

			String sql = "select ID from session where SessId=\""+req.SessId+"\"";

			try {
				ResultSet ret = dbm.query(sql);

				ret.next();

				req.UserId = ret.getString("ID");
			} catch (Exception e) {
				Main.logger.error("", e);
			}

			Main.logger.debug("会话"+SessId+"的设备更新请求放到处理队列");

			webnsuptlist.add(req);
		}

		void getDevInfs(WebReq webreq) {
			if (!webreq.jmsg.has("SessId")) {
				invalidReq(webreq);
				Main.logger.error("无效的设备信息请求:"+webreq.jmsg);

				return;
			}

			WebAck ack     = new WebAck();
			String SessId  = webreq.jmsg.get("SessId").getAsString();
			String sql     = String.format("select * from devcfgs where DevEUI in (select DevEUI "
					+ "from userdev where ID=(select ID from session where SessId=\"%s\"))", SessId);
			JsonArray infs = new JsonArray();

			ResultSet ret  = dbm.query(sql);

			if (ret==null) {
				Main.logger.error("会话"+SessId+"对应用户没有设备配置信息");

				return;
			}

			try {
				while(ret.next()) {
					JsonObject inf = new JsonObject();

					inf.addProperty("DevEUI"         , ret.getLong("DevEui"));
					inf.addProperty("AppEUI"         , ret.getLong("AppEui"));
					inf.addProperty("AppKey"         , ret.getString("AppKey"));
					inf.addProperty("LoRaMode"       , ret.getLong("Class"));
					inf.addProperty("MACMajorVersion", ret.getLong("Version"));
					inf.addProperty("RXDelay1"       , ret.getLong("RX1Delay"));
					inf.addProperty("RXDROffset1"    , ret.getLong("RX1DROffset"));
					inf.addProperty("RXDataRate2"    , ret.getLong("RX2DataRate"));
					inf.addProperty("RXFreq2"        , ret.getLong("RX2Freq"));
					inf.addProperty("MaxDutyCycle"   , ret.getLong("MaxDutyCycle"));
					inf.addProperty("ActivationMode" , ret.getLong("ActiveMode"));
					inf.addProperty("FreqType"       , ret.getLong("FreqType"));
					inf.addProperty("FreqPair"       , ret.getString("FreqPair"));

					infs.add(inf);
				}
			} catch (Exception e) {
				Main.logger.error("", e);
			}

			ack.sock = webreq.sock;
			ack.jmsg = infs.toString();

			webackqueue.add(ack);
		}

		void cfgDevInfs(WebReq webreq) {
			if (!(webreq.jmsg.has("Timeout")
			   && webreq.jmsg.has("DevEUI")
			   && webreq.jmsg.has("AppEUI")
			   && webreq.jmsg.has("AppKey")
			   && webreq.jmsg.has("RXFreq2")
			   && webreq.jmsg.has("LoRaMode")
			   && webreq.jmsg.has("RXDelay1")
			   && webreq.jmsg.has("FreqType")
			   && webreq.jmsg.has("FreqPair")
			   && webreq.jmsg.has("RXDROffset1")
			   && webreq.jmsg.has("RXDataRate2")
			   && webreq.jmsg.has("MaxDutyCycle")
			   && webreq.jmsg.has("ActivationMode")
			   && webreq.jmsg.has("MACMajorVersion")
			)) {
				invalidReq(webreq);
				Main.logger.error("无效的设备信息配置请求:"+webreq.jmsg);

				return;
			}

			JsonObject req = webreq.jmsg;
			String     str = req.get("FreqPair").getAsString();
			String     sql = String.format("select count(*) from devcfgs where DevEui=%d", req.get("DevEUI").getAsLong());
			ResultSet  ret = dbm.query(sql);

			JsonArray  jfs1;
			JsonArray  jfs2 = new JsonArray();

			try {
				jfs1 = new JsonParser().parse(str).getAsJsonArray();
			}
			catch(Exception e) {
				invalidReq(webreq);
				Main.logger.error("无效的频率列表信息:"+str);

				return;
			}

			if (req.get("FreqType").getAsLong()!=FREQ_TYPE_CUSTOM) {
				jfs1.forEach(j->{
					String tmpstr = j.getAsString();
					int    tmpint = Integer.parseInt(tmpstr, 16);
					jfs2.add(new JsonPrimitive(tmpint));
				});
			}

			if (ret==null) {
				Main.logger.error("查询是否有设备配置信息失败");

				return;
			}

			try {
				sql="";
				ret.next();

				if (ret.getLong(1) > 0) {
					sql=String.format("delete from devcfgs where DevEui=%d;", req.get("DevEUI").getAsLong());

					dbm.query(sql);
				}

				sql=String.format("insert into devcfgs values (%d,%d,\"%s\",%d,%d,%d,%d,%d,%d,%d,%d,%d,\"%s\");",
									req.get("DevEUI"      ).getAsLong(), req.get("AppEUI"         ).getAsLong(), req.get("AppKey"  ).getAsString(),
									req.get("LoRaMode"    ).getAsLong(), req.get("MACMajorVersion").getAsLong(), req.get("RXDelay1").getAsLong(  ),
									req.get("RXDROffset1" ).getAsLong(), req.get("RXDataRate2"    ).getAsLong(), req.get("RXFreq2" ).getAsLong(  ),
									req.get("MaxDutyCycle").getAsLong(), req.get("ActivationMode" ).getAsLong(), req.get("FreqType").getAsLong(  ),
									req.get("FreqPair"    ).getAsString()  );

				dbm.query(sql);

				sql = String.format("select NsId from (select * from devs) as A where DevEui=%d and Count="
									+ "(select MAX(Count) from devs where DevEui=%d);", req.get("DevEUI").getAsLong(),
									req.get("DevEUI").getAsLong());

				ret = dbm.query(sql);

				if (ret==null)
					return;

				if (ret.wasNull() || !ret.next()) {
					WebAck ack = new WebAck();
					JsonObject obj = new JsonObject();

					obj.addProperty("Status", 0);
					obj.addProperty("Error" , "Failed to send to ns");

					ack.sock = webreq.sock;
					ack.jmsg = obj.toString();
					return;
				}

				Long       nsid  = ret.getLong("NsId");
				WebNsReq   nsreq = new WebNsReq();
				NsDlMsg    dlmsg = new NsDlMsg();
				JsonObject msg   = new JsonObject();
				JsonObject req1  = new JsonObject();

				nsreq.confirm = true;
				nsreq.sock    = webreq.sock;
				nsreq.timeout = req.get("Timeout").getAsLong();
				nsreq.dlmsg   = dlmsg;

				dlmsg.id      = nsid;
				dlmsg.jmsg    = msg;
				dlmsg.type    = 10;
				dlmsg.version = 1;

				req1.addProperty("DevEUI"         , req.get("DevEUI"         ).getAsLong());
				req1.addProperty("AppEUI"         , req.get("AppEUI"         ).getAsLong());
				req1.addProperty("RXFreq2"        , req.get("RXFreq2"        ).getAsLong());
				req1.addProperty("LoRaMode"       , req.get("LoRaMode"       ).getAsLong());
				req1.addProperty("RXDelay1"       , req.get("RXDelay1"       ).getAsLong());
				req1.addProperty("FreqType"       , req.get("FreqType"       ).getAsLong());
				req1.addProperty("RXDROffset1"    , req.get("RXDROffset1"    ).getAsLong());
				req1.addProperty("RXDataRate2"    , req.get("RXDataRate2"    ).getAsLong());
				req1.addProperty("MaxDutyCycle"   , req.get("MaxDutyCycle"   ).getAsLong());
				req1.addProperty("ActivationMode" , req.get("ActivationMode" ).getAsLong());
				req1.addProperty("MACMajorVersion", req.get("MACMajorVersion").getAsLong());
				req1.addProperty("AppKey"         , req.get("AppKey"         ).getAsString());

				if (req.get("FreqType").getAsLong()!=FREQ_TYPE_CUSTOM) {
					req1.addProperty("FreqPair"   , jfs2.toString());
				}
				else {
					req1.addProperty("FreqPair"   , req.get("FreqPair").getAsString());
				}

				msg.add("MoteInfoPush", req1);

				webnsreqlist.addWebNsReq(nsreq);
			} catch (Exception e) {
				Main.logger.error("", e);
			}
		}

		void sendDevData(WebReq webreq) {
			if (!webreq.jmsg.has("Timeout")
			  ||!webreq.jmsg.has("DevEUI")
			  ||!webreq.jmsg.has("FPort")
			  ||!webreq.jmsg.has("AppData")) {
				invalidReq(webreq);
				Main.logger.error("无效的设备数据发送请求:"+webreq.jmsg);

				return;
			}

			JsonObject req = webreq.jmsg;
			String     sql = String.format("select NsId from (select * from devs) as A where DevEui=%d and Count="
					   + "(select MAX(Count) from devs where DevEui=%d);", req.get("DevEUI").getAsLong(),
					   req.get("DevEUI").getAsLong());

			ResultSet ret = dbm.query(sql);

			if (ret==null) {
				Main.logger.error("查询设备连接的最新NS失败");

				return;
			}

			try {
				if (ret.wasNull() || !ret.next()) {
					WebAck ack = new WebAck();
					JsonObject obj = new JsonObject();

					obj.addProperty("Status", 0);
					obj.addProperty("Error" , "Failed to send to ns");

					ack.sock = webreq.sock;
					ack.jmsg = obj.toString();

					return;
				}

				Long       nsid  = ret.getLong("NsId");
				WebNsReq   nsreq = new WebNsReq();
				NsDlMsg    dlmsg = new NsDlMsg();
				JsonObject msg   = new JsonObject();
				JsonObject req1  = new JsonObject();

				nsreq.confirm = false;
				nsreq.sock    = webreq.sock;
				nsreq.timeout = req.get("Timeout").getAsLong();
				nsreq.dlmsg   = dlmsg;

				dlmsg.id      = nsid;
				dlmsg.jmsg    = msg;
				dlmsg.type    = 2;
				dlmsg.version = 1;

				req1.addProperty("DevEUI" , req.get("DevEUI").getAsLong());
				req1.addProperty("FPort"  , req.get("FPort").getAsLong());
				req1.addProperty("AppData", req.get("AppData").getAsString());
				req1.addProperty("DataLen", req.get("AppData").getAsString().length()/2);

				msg.add("MoteDLData", req1);

				webnsreqlist.addWebNsReq(nsreq);
			}
			catch(Exception e) {
				Main.logger.error("", e);
			}
		}

		void getGws(WebReq webreq) {
			WebAck    ack  = new WebAck();
			String SessId  = webreq.jmsg.get("SessId").getAsString();
			JsonArray datas= getGwsData(SessId, dbm);

			if (datas == null) {
				Main.logger.error("查询会话"+SessId+"的基站数据失败");

				return;
			}

			ack.sock       = webreq.sock;
			ack.jmsg       = datas.toString();

			webackqueue.add(ack);
		}

		void uptGws(WebReq webreq) {
			if (!webreq.jmsg.has("Timeout")
			  ||!webreq.jmsg.has("SessId")) {
				invalidReq(webreq);
				Main.logger.error("无效的网关数据更新请求:"+webreq.jmsg);

				return;
			}

			long           timeout = webreq.jmsg.get("Timeout").getAsLong();
			String         SessId  = webreq.jmsg.get("SessId").getAsString();
			WebNsDevUptReq req     = new WebNsDevUptReq();

			req.sock    = webreq.sock;
			req.SessId  = SessId;
			req.timeout = timeout;
			req.type    = 1;

			webnsuptlist.add(req);
		}

		void getGwInfs(WebReq webreq) {
			if (!webreq.jmsg.has("SessId")) {
				invalidReq(webreq);
				Main.logger.error("无效的网关信息请求:"+webreq.jmsg);

				return;
			}

			WebAck ack     = new WebAck();
			String SessId  = webreq.jmsg.get("SessId").getAsString();
			String sql     = String.format("select * from gwcfgs where GwID in (select GwID "
					+ "from usergw where ID=(select ID from session where SessId=\"%s\"))", SessId);
			JsonArray infs = new JsonArray();

			ResultSet ret  = dbm.query(sql);

			if (ret==null) {
				Main.logger.error("查询会话"+SessId+"的所有网关配置信息失败");

				return;
			}

			try {
				while(ret.next()) {
					JsonObject inf = new JsonObject();

					inf.addProperty("GwID"   , ret.getLong("GwID"));
					inf.addProperty("TxPower", ret.getLong("TxPower"));

					infs.add(inf);
				}
			} catch (Exception e) {
				Main.logger.error("", e);
			}

			ack.sock = webreq.sock;
			ack.jmsg = infs.toString();

			webackqueue.add(ack);
		}

		void cfgGwInfs(WebReq webreq) {
			if (!(webreq.jmsg.has("Timeout")
			   && webreq.jmsg.has("GwID"   )
			   && webreq.jmsg.has("TxPower")
			)) {
				invalidReq(webreq);
				Main.logger.error("无效的网关信息配置请求:"+webreq.jmsg);

				return;
			}

			JsonObject req = webreq.jmsg;
			String     sql = String.format("select count(*) from gwcfgs where GwId=%d", req.get("GwID").getAsLong());
			ResultSet  ret = dbm.query(sql);

			if (ret == null) {
				return;
			}

			try {
				sql="";
				ret.next();
				if (ret.getLong(1)>0) {
					sql=String.format("delete from gwcfgs where GwId=%d;", req.get("GwID").getAsLong());

					dbm.query(sql);
				}

				sql=String.format("insert into gwcfgs values (%d,%d);",
						req.get("GwID").getAsLong(), req.get("TxPower").getAsLong());

				dbm.query(sql);

				sql = String.format("select NsId from (select * from devs) as A where GwId=%d and Count="
						+ "(select MAX(Count) from devs where GwId=%d);", req.get("GwID").getAsLong(),
						req.get("GwID").getAsLong());

				ret = dbm.query(sql);

				if (ret==null)
					return;

				if (ret.wasNull() || !ret.next()) {
					WebAck ack = new WebAck();
					JsonObject obj = new JsonObject();

					obj.addProperty("Status", 0);
					obj.addProperty("Error" , "Failed to send to ns");

					ack.sock = webreq.sock;
					ack.jmsg = obj.toString();
					return;
				}

				Long       nsid  = ret.getLong("NsId");
				WebNsReq   nsreq = new WebNsReq();
				NsDlMsg    dlmsg = new NsDlMsg();
				JsonObject msg   = new JsonObject();
				JsonObject req1  = new JsonObject();

				nsreq.confirm = true;
				nsreq.sock    = webreq.sock;
				nsreq.timeout = req.get("Timeout").getAsLong();
				nsreq.dlmsg   = dlmsg;

				dlmsg.id      = nsid;
				dlmsg.jmsg    = msg;
				dlmsg.type    = 12;
				dlmsg.version = 1;

				req1.addProperty("GwID"   , req.get("GwID"   ).getAsLong());
				req1.addProperty("TxPower", req.get("TxPower").getAsLong());

				msg.add("GwInfoPush", req1);

				webnsreqlist.addWebNsReq(nsreq);
			} catch (Exception e) {
				Main.logger.error("", e);
			}
		}

		@Override
		public void run() {

			while(true) {
				if (webreqqueue.isEmpty()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Main.logger.error("", e);
					}

					continue;
				}

				WebReq webreq = webreqqueue.poll();

				if (!webreq.jmsg.has("Type")) {
					invalidReq(webreq);
					continue;
				}

				String Type = webreq.jmsg.get("Type").getAsString();

				if (Type.equals("Login")) {
					login(webreq);
				}
				else {
					if (!validSession(webreq)) {
						continue;
					}

					if (Type.equals("GetDevs")) {
						getDevs(webreq);
						continue;
					}
					if (Type.equals("UptDevs")) {
						uptDevs(webreq);
						continue;
					}
					if (Type.equals("GetMoteInfos")) {
						getDevInfs(webreq);
						continue;
					}
					if (Type.equals("CfgMoteInfos")) {
						cfgDevInfs(webreq);
						continue;
					}
					if (Type.equals("SendData")) {
						sendDevData(webreq);
						continue;
					}
					if (Type.equals("GetGws")) {
						getGws(webreq);
						continue;
					}
					if (Type.equals("UptGws")) {
						uptGws(webreq);
						continue;
					}
					if (Type.equals("GetGwInfos")) {
						getGwInfs(webreq);
						continue;
					}
					if (Type.equals("CfgGwInfos")) {
						cfgGwInfs(webreq);

						continue;
					}

					Main.logger.error("无效的web请求类型"+Type);
				}
			}
		}
	}

	class WebReqTimeout implements Runnable {
		public WebReqTimeout() {
//			dbm = new DBManager();
//			dbm.connect();
		}

		void reqTimeout(WebNsReq wnreq) {
			WebAck     ack = new WebAck();
			JsonObject obj = new JsonObject();

			obj.addProperty("Status", 0);
			obj.addProperty("Error" , "Timeout");

			ack.sock = wnreq.sock;
			ack.jmsg = obj.toString();

			webackqueue.add(ack);
		}

		void reqResult(WebNsReq wnreq) {
			WebAck     ack = new WebAck();
			JsonObject obj = new JsonObject();

			if (wnreq.state==WebNsReq.STATE_FAIL) {
				obj.addProperty("Status", 0);
				obj.addProperty("Error" , "Send Failed");
			}
			else {
				obj.addProperty("Status", 1);
			}

			ack.sock = wnreq.sock;
			ack.jmsg = obj.toString();

			webackqueue.add(ack);
		}

		private void updateDevs(WebNsDevUptReq req) {
			/* check update */
			if (!req.UserId.isEmpty()) {
				if (!userupt.CheckUserDevicesUpt(req.UserId))
					return;
			}

			try {
				userupt.UptUserDevices(req.UserId, false);

				WebAck ack = new WebAck();
				JsonArray datas= getDevsData(req.SessId, dbm);

				if (datas == null)
					return;

				ack.sock = req.sock;
				ack.jmsg = datas.toString();

				webackqueue.add(ack);
				webnsuptlist.remove(req);
			} catch (Exception e) {
				Main.logger.error("", e);
			}
		}

		private void updateGws(WebNsDevUptReq req) {
			/* check update */
			if (!req.UserId.isEmpty()) {
				if (!userupt.CheckUserGwsUpt(req.UserId))
					return;
			}

			try {
				userupt.UptUserGws(req.UserId, false);

				WebAck ack = new WebAck();
				JsonArray datas= getGwsData(req.SessId, dbm);

				if (datas==null)
					return;

				ack.sock = req.sock;
				ack.jmsg = datas.toString();

				webackqueue.add(ack);
				webnsuptlist.remove(req);
			} catch (Exception e) {
				Main.logger.error("", e);
			}
		}

		@Override
		public void run() {

			while (true) {
				if (!webnsreqlist.isEmpty()) {
					List<WebNsReq>      list     = webnsreqlist.getWebNsReq();
					Iterator<WebNsReq>  iterator = list.iterator();

					WebNsReq req = null;

					while(iterator.hasNext()) {
						req = iterator.next();
						long now = Common.getTime();

						if (now>req.time+req.timeout) {
							reqTimeout(req);
							webnsreqlist.delWebNsReq(req);
						}
						else if (req.state==WebNsReq.STATE_ACK || req.state==WebNsReq.STATE_FAIL) {
							reqResult(req);
							webnsreqlist.delWebNsReq(req);
						}
					}
				}

			    /* 定时查询设备数据 */
				if (!webnsuptlist.isEmpty()) {
					List<WebNsDevUptReq> reqlist  = webnsuptlist.get();
					Iterator<WebNsDevUptReq>  itorator = reqlist.iterator();

					while(itorator.hasNext()) {
						WebNsDevUptReq req = (WebNsDevUptReq) itorator.next();

						if (Common.getTime()>req.time+req.timeout) { /* 超时 */
							WebAck ack = new WebAck();
							JsonObject obj = new JsonObject();

							obj.addProperty("Status", 0);
							obj.addProperty("Error" ,  "timeout");

							ack.sock = req.sock;
							ack.jmsg = obj.toString();

							webackqueue.add(ack);
							webnsuptlist.remove(req);

							Main.logger.warn("超时web请求:"+req.type);
						}
						else {
							if (req.type==0) {
								updateDevs(req);
							}
							else {
								updateGws(req);
							}
						}
					}
				}

				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Main.logger.error("", e);
				}
			}
		}
	}

	class WebReq {
		public Socket      sock = null;
		public JsonObject  jmsg = null;
	}

	class WebAck {
		public Socket      sock = null;
		public String      jmsg = null;
	}
}
