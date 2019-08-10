package appserver;

import java.net.Socket;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import org.apache.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import common.DBManager;
import appserver.Main;

public class NsUlDecoder implements Runnable {
	private NsUlMsg                ulqueue;
	private NsDlMsg                dlqueue;
	private WebNsReq               webnsreqlist;
	public  Map<Long, Socket>      nsconn_list;
	private DBManager              dbm;
	private int                    maxdevdatanum = 5000;
	private Map<Long, Integer>     devdatacount  = new HashMap<>();
	private Map<Long, Integer>     devdataseqno  = new HashMap<>();
	private Map<Long, Integer>     gwdatacount   = new HashMap<>();
	private UserUpt                userupt = null;
	//private static Logger logger = Logger.getLogger("appserver");

/*	public NsUlDecoder(LinkedBlockingQueue<NsDlMsg> queue,
			ArrayList<WebNsReq> wlist,
			DBManager db) {
		dbm          = db;
		ulqueue      = new LinkedBlockingQueue<NsUlMsg>();
		dlqueue      = queue;
		webnsreqlist = wlist;
		nsconn_list  = new HashMap<Long, Socket>();
	}*/

	public NsUlDecoder() {
//		ulqueue      = new LinkedBlockingQueue<NsUlMsg>();
		nsconn_list  = new HashMap<Long, Socket>();
//		webnsreqlist = new ArrayList<>();
	}

	public void setMaxdevdatanum(int maxdevdatanum) {
		this.maxdevdatanum = maxdevdatanum;
	}

	public void setDbm(DBManager dbm) {
		this.dbm = dbm;
	}

	public void setWebnsreqlist(WebNsReq webnsreqlist) {
		this.webnsreqlist = webnsreqlist;
	}

	public void setDlqueue(NsDlMsg dlqueue) {
		this.dlqueue = dlqueue;
	}

	public void setUlqueue(NsUlMsg ulqueue) {
		this.ulqueue = ulqueue;
	}

	public void setUserupt(UserUpt userupt) {
		this.userupt = userupt;
	}
/*
	public void put(NsUlMsg m) {
		try {
			ulqueue.put(m);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}*/

	private void handleUlData(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("MoteULData");

		Main.logger.debug("处理上行设备数据:"+data.toString());

		long   DevEUI   = data.get("DevEUI").getAsLong();
		long   GwID     = data.get("GwID").getAsLong();
		long   DevAddr  = data.get("DevAddr").getAsLong();
		long   FPort    = data.get("FPort").getAsLong();
		long   FcntDown = data.get("FcntDown").getAsLong();
		long   FcntUp   = data.get("FcntUp").getAsLong();
		long   ULFreq   = data.get("ULFreq").getAsLong();
		long   Tmst     = data.get("Tmst").getAsLong();
		long   RSSI     = data.get("RSSI").getAsLong();
		long   DataLen  = data.get("DataLen").getAsLong();

		float  SNR      = data.get("SNR").getAsFloat();

		String DataRate = data.get("DataRate").getAsString();
		String CodeRate = data.get("CodeRate").getAsString();
		String RecvTime = data.get("RecvTime").getAsString();
		String AppData  = data.get("AppData").getAsString();

		String UpdateTime = "0";

		try {
			Date t = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(RecvTime);
			UpdateTime = new SimpleDateFormat("yyyyMMddHHmmss").format(t);
		} catch (Exception e) {
			Main.logger.error("转换时间失败", e);
		}

		int    count = 0;
//		int    Seqno = 0;
		String sqlstate = "";

		if (!devdatacount.containsKey(DevEUI)) {
			sqlstate = String.format("select count(*) as RowCount,max(count) as Seqno from devs where DevEUI=%d", DevEUI);

			ResultSet ret   = dbm.query(sqlstate);

			if (ret==null)
				return;

			try {
				ret.next();
				count = ret.getInt("RowCount");
//				Seqno = ret.getInt("Seqno");

				devdatacount.put(DevEUI, count);
//				devdataseqno.put(DevEUI, Seqno);
			} catch (SQLException e) {
				Main.logger.error("", e);
			}
		}
		else {
			count = devdatacount.get(DevEUI);
//			Seqno = devdataseqno.get(DevEUI);
		}

		Main.logger.debug("设备"+DevEUI+"有"+count+"个数据");

		if (count >= maxdevdatanum) {
			sqlstate = "delete from devs where DevEUI="+DevEUI+" and UpdateTime in (select time from "
					+ "(select min(d.UpdateTime) as time FROM devs d where DevEUI="+DevEUI+") t)";

			dbm.query(sqlstate);
		}
		else {
			count++;
			devdatacount.put(DevEUI, count);
		}

		sqlstate = String.format("insert into devs values (%d, %d, %d, %d, %d, "
				+ "%d, \"%s\", \"%s\", %d, \"%s\", %d, %d, %f, %d, \"%s\", %d, %d, %s);", DevEUI,
				GwID, DevAddr, FPort, FcntDown, FcntUp, DataRate, CodeRate, ULFreq, RecvTime,
			    Tmst, RSSI, SNR, DataLen, AppData, msg.id, count, UpdateTime);
		dbm.query(sqlstate);

		sqlstate = "insert into devupt values ("+DevEUI+","+1+") on DUPLICATE KEY UPDATE Upt=1";

		dbm.query(sqlstate);

		sqlstate = "select id from userdev where deveui="+DevEUI;

		try {
			ResultSet rets = dbm.query(sqlstate);

			while(rets.next()) {
				userupt.UptUserDevices(rets.getString("id"), true);
			}
		} catch (Exception e) {
			Main.logger.error("", e);
		}
	}

	private void handGwState(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("GWDemoStat");

		Main.logger.debug("处理上行网关数据:"+data.toString());

		long   GwID = data.get("GwID").getAsLong();
		long   Alti = data.get("Alti").getAsLong();
		long   RxNb = data.get("RxNb").getAsLong();
		long   RxOK = data.get("RxOK").getAsLong();
		long   RxFw = data.get("RxFw").getAsLong();
		long   AckR = data.get("AckR").getAsLong();
		long   DwNb = data.get("DwNb").getAsLong();
		long   TxNb = data.get("TxNb").getAsLong();

		float  Lati = data.get("Lati").getAsFloat();
		float  Long = data.get("Long").getAsFloat();

		String Time = data.get("Time").getAsString();
		String UpdateTime = "0";

		try {
			Date t = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(Time);
			UpdateTime = new SimpleDateFormat("yyyyMMddHHmmss").format(t);
		} catch (Exception e) {
			Main.logger.error("转换时间失败", e);
		}

		int    count = 0;
		String sqlstate = "";

		if (!gwdatacount.containsKey(GwID)) {
			sqlstate = "select count(*) as RowCount from gws where GwID="+GwID;

			ResultSet ret   = dbm.query(sqlstate);

			if (ret==null)
				return;

			try {
				ret.next();
				count = ret.getInt("RowCount");

				gwdatacount.put(GwID, count);
			} catch (SQLException e) {
				Main.logger.error("", e);
			}
		}
		else {
			count = gwdatacount.get(GwID);
		}

		Main.logger.debug("网关"+GwID+"有"+count+"个数据");

		if (count >= maxdevdatanum) {
			sqlstate = "delete from gws where GwID="+GwID+" and UpdateTime in (select time from "
					+ "(select min(d.UpdateTime) as time FROM gws d where GwID="+GwID+") t)";
			dbm.query(sqlstate);
		}
		else {
			count++;
			gwdatacount.put(GwID, count);
		}

		sqlstate = String.format("insert into gws values (%d, \"%s\", %f, %f, %d, %d, %d, %d, "
				                 + "%d, %d, %d, %s);", GwID, Time, Lati, Long, Alti, RxNb, RxOK, RxFw, AckR, DwNb,
				                 TxNb, UpdateTime
				                );
		dbm.query(sqlstate);
		sqlstate = String.format("insert into gwupt values (%d, 1)", GwID)+" on DUPLICATE KEY UPDATE Upt=1";

		dbm.query(sqlstate);

		sqlstate = "select id from usergw where GwID="+GwID;

		try {
			ResultSet rets = dbm.query(sqlstate);

			while(rets.next()) {
				//WebComm.userdevdataupt.put(rets.getString("id"), true);
				userupt.UptUserGws(rets.getString("id"), true);
			}
		} catch (Exception e) {
			Main.logger.error("", e);
		}
	}

	private void handLinkUpt(NsUlMsg msg) {
		if (msg.jmsg.has("LinkUpdate")) {
			nsconn_list.put(msg.id, msg.sock);
			NsDlMsg dlmsg = new NsDlMsg();
			dlmsg.id      = msg.id;
			dlmsg.type    = 5;
			dlmsg.version = msg.version;

			JsonObject jmsg   = new JsonObject();
			JsonObject jdata  = new JsonObject();

			jdata.addProperty("ACK"          , 0    );
			jmsg.add         ("LinkUpdateAck", jdata);

			dlmsg.jmsg = jmsg;

			dlqueue.put(dlmsg);

			Main.logger.debug("收到ns"+msg.id+"连接");
		}
	}

	private void handDevReq(NsUlMsg msg) {
		JsonObject data   = msg.jmsg.getAsJsonObject("MoteInfoReq");
		long   DevEUI     = data.get("DevEUI").getAsLong();
		NsDlMsg dlmsg     = new NsDlMsg();

		dlmsg.id          = msg.id;
		dlmsg.version     = msg.version;
		dlmsg.type        = 7;

		JsonObject jmsg   = new JsonObject();
		JsonObject jdata  = new JsonObject();

		String sqlstate   = String.format("select * from devcfgs where DevEUI=%d", DevEUI);
		ResultSet ret     = dbm.query(sqlstate);

		if (ret==null) {
			Main.logger.error("没有设备"+DevEUI+"配置信息");

			return;
		}

		try {
			if (!ret.next())
				return;

			long AppEUI       = ret.getLong("AppEUI"      );
			long Class        = ret.getLong("Class"       );
			long Version      = ret.getLong("Version"     );
			long RX2Freq      = ret.getLong("RX2Freq"     );
			long RX1Delay     = ret.getLong("RX1Delay"    );
			long FreqType     = ret.getLong("FreqType"    );
			long ActiveMode   = ret.getLong("ActiveMode"  );
			long RX1DROffset  = ret.getLong("RX1DROffset" );
			long RX2DataRate  = ret.getLong("RX2DataRate" );
			long MaxDutyCycle = ret.getLong("MaxDutyCycle");

			String AppKey     = ret.getString("AppKey"    );
			String FreqPair   = ret.getString("FreqPair"  );

			JsonArray  jfs1;
			JsonArray  jfs2 = new JsonArray();

			try {
				jfs1 = new JsonParser().parse(FreqPair).getAsJsonArray();
			}
			catch(Exception e) {
				Main.logger.error("频率列表json解析失败", e);

				return;
			}

			if (FreqType!=3) {
				jfs1.forEach(j->{
					String tmpstr = j.getAsString();
					int    tmpint = Integer.parseInt(tmpstr, 16);
					jfs2.add(new JsonPrimitive(tmpint));
				});
			}

			jdata.addProperty("DevEUI"         , DevEUI      );
			jdata.addProperty("AppEUI"         , AppEUI      );
			jdata.addProperty("AppKey"         , AppKey      );
			jdata.addProperty("RXFreq2"        , RX2Freq     );
			jdata.addProperty("FreqType"       , FreqType    );
			jdata.addProperty("LoRaMode"       , Class       );
			jdata.addProperty("RXDelay1"       , RX1Delay    );
			jdata.addProperty("RXDROffset1"    , RX1DROffset );
			jdata.addProperty("RXDataRate2"    , RX2DataRate );
			jdata.addProperty("MaxDutyCycle"   , MaxDutyCycle);
			jdata.addProperty("ActivationMode" , ActiveMode  );
			jdata.addProperty("MacMajorVersion", Version     );

			if (FreqType!=3) {
				jdata.addProperty("FreqPair"   , jfs2.toString());
			}
			else {
				jdata.addProperty("FreqPair"   , FreqPair);
			}

			jmsg.add("MoteInfoResp", jdata);

			dlmsg.jmsg = jmsg;

			Main.logger.debug("回复设备"+DevEUI+"信息请求的响应:"+jmsg);

			dlqueue.put(dlmsg);
		} catch (Exception e) {
			Main.logger.error("", e);
		}
	}

	private void handGwReq(NsUlMsg msg) {
		JsonObject data   = msg.jmsg.getAsJsonObject("GwInfoReq");
		long       GwID   = data.get("GwID").getAsLong();
		NsDlMsg    dlmsg  = new NsDlMsg();

		dlmsg.id          = msg.id;
		dlmsg.version     = msg.version;
		dlmsg.type        = 9;

		JsonObject jmsg   = new JsonObject();
		JsonObject jdata  = new JsonObject();

		String    sqlstate = String.format("select * from gwcfgs where GwID=%d", GwID);
		ResultSet ret      = dbm.query(sqlstate);

		if (ret==null) {
			Main.logger.error("没有网关"+GwID+"配置信息");

			return;
		}

		try {
			ret.next();

			long TxPower = ret.getLong("TxPower");

			jdata.addProperty("TxPower"   , TxPower);
			jmsg.add         ("GwInfoResp", jdata  );

			dlmsg.jmsg   = jmsg;

			Main.logger.debug("回复网关"+GwID+"信息请求的响应:"+jmsg);

			dlqueue.put(dlmsg);
		} catch (Exception e) {
			Main.logger.error("", e);
		}
	}

	private void handDevPush(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("MoteInfoPushAck");

		long   DevEUI   = data.get("DevEUI").getAsLong();
		long   ACK      = data.get("ACK"   ).getAsLong();

		List<WebNsReq> list = (List<WebNsReq>)webnsreqlist.getWebNsReq();
		Iterator<WebNsReq>  itor = list.iterator();

		while(itor.hasNext()) {
			WebNsReq req = itor.next();

			if (req.dlmsg.id != msg.id
			 ||!req.dlmsg.jmsg.has("MoteInfoPush")
			 || req.state!=WebNsReq.STATE_SEND) {
				continue;
			}

			JsonObject obj  = (JsonObject) req.dlmsg.jmsg.get("MoteInfoPush");
			long       id   = obj.get("DevEUI").getAsLong();

			if (id != DevEUI) {
				continue;
			}

			if (ACK != 0) {
				req.state = WebNsReq.STATE_FAIL;
				Main.logger.error("推送设备"+DevEUI+"配置信息失败");

				continue;
			}

			req.state = WebNsReq.STATE_ACK;
			Main.logger.error("推送设备"+DevEUI+"配置信息成功");
		}
	}

	private void handGwPush(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("GwInfoPushAck");

		long   GwID     = data.get("GwID").getAsLong();
		long   ACK      = data.get("ACK" ).getAsLong();

		List<WebNsReq> list = webnsreqlist.getWebNsReq();
		Iterator<WebNsReq>  itor = list.iterator();

		while(itor.hasNext()) {
			WebNsReq req = itor.next();

			if (req.dlmsg.id!=msg.id
			 ||!req.dlmsg.jmsg.has("GwInfoPush")
			 || req.state!=WebNsReq.STATE_SEND) {
				continue;
			}

			JsonObject obj  = (JsonObject) req.dlmsg.jmsg.get("GwInfoPush");
			long       id   = obj.get("GwID").getAsLong();

			if (id != GwID) {
				continue;
			}

			if (ACK!=0) {
				req.state = WebNsReq.STATE_FAIL;
				Main.logger.error("推送网关"+GwID+"配置信息失败");

				continue;
			}

			req.state = WebNsReq.STATE_ACK;
			Main.logger.debug("推送网关"+GwID+"配置信息成功");
		}
	}

	public void run() {

		while(true) {
			if (ulqueue.isEmpty()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Main.logger.error("", e);
				}

				continue;
			}

			NsUlMsg msg = (NsUlMsg) ulqueue.poll();

			Main.logger.debug("NS上行消息类型"+msg.type);

			switch (msg.type) {
			case 1: {
				if (msg.jmsg.has("MoteULData")) {
					handleUlData(msg);
				}
				break;
			}
			case 3: {
				if (msg.jmsg.has("GWDemoStat")) {
					handGwState(msg);
				}

				break;
			}
			case 4: {
				handLinkUpt(msg);

				break;
			}
			case 6: {
				if (msg.jmsg.has("MoteInfoReq")) {
					handDevReq(msg);
				}

				break;
			}
			case 8: {
				if (msg.jmsg.has("GwInfoReq")) {
					handGwReq(msg);
				}

				break;
			}
			case 11: {
				if (msg.jmsg.has("MoteInfoPushAck")) {
					handDevPush(msg);
				}

				break;
			}
			case 13: {
				if (msg.jmsg.has("GwInfoPushAck")) {
					handGwPush(msg);
				}

				break;
			}
			default:
				Main.logger.error("无效的NS上行消息类型:"+msg.type);

				break;
			}
		}
	}
}
