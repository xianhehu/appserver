import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class NsUlDecoder implements Runnable {
	public  LinkedBlockingQueue<NsUlMsg> ulqueue;
	public  LinkedBlockingQueue<NsDlMsg> dlqueue;
	private ArrayList<WebNsReq>          webnsreqlist;
	public  HashMap<Long, Socket>        nsconn_list;
	private DBManager                    dbm;

	public NsUlDecoder(LinkedBlockingQueue<NsDlMsg> queue,
			ArrayList<WebNsReq> wlist, 
			DBManager db) {
		// TODO Auto-generated constructor stub
		dbm          = db;
		ulqueue      = new LinkedBlockingQueue<NsUlMsg>();
		dlqueue      = queue;
		webnsreqlist = wlist;
		nsconn_list  = new HashMap<Long, Socket>();
	}

	public void put(NsUlMsg m) {
		try {
			ulqueue.put(m);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void handleUlData(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("MoteULData");
		
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
		
		String sqlstate = String.format("select count(*) as RowCount from devs where DevEUI=%d", DevEUI);
		
		ResultSet ret   = dbm.query(sqlstate);
		
		if (ret==null)
			return;
		
		int count = 0;
		try {
			ret.next();
			count = ret.getInt("RowCount");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (count>=50) {
			count = 49;
			sqlstate = String.format("delete from devs where Count=0 and DevEUI=%d", DevEUI);
			dbm.query(sqlstate);
			sqlstate = String.format("update devs set Count=Count-1 where Count>0 and DevEUI=%d", DevEUI);
			dbm.query(sqlstate);
		}

		sqlstate = String.format("insert into devs values (%d, %d, %d, %d, %d, "
				+ "%d, \"%s\", \"%s\", %d, \"%s\", %d, %d, %f, %d, \"%s\", %d, %d);", DevEUI, 
				GwID, DevAddr, FPort, FcntDown, FcntUp, DataRate, CodeRate, ULFreq, RecvTime,
			    Tmst, RSSI, SNR, DataLen, AppData, msg.id, count);
		dbm.query(sqlstate);
		
		sqlstate = String.format("select count(*) as RowCount from devupt where DevEUI=%d", DevEUI);
		ret = dbm.query(sqlstate);
		if (ret==null)
			return;
		
		count = 0;
		try {
			ret.next();
			count = ret.getInt("RowCount");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (count>0) {
			sqlstate = String.format("update devupt set Upt=1 where DevEUI=%d", DevEUI);
		}
		else {
			sqlstate = String.format("insert into devupt values (%d, 1)", DevEUI);
		}
		
		dbm.query(sqlstate);
	}
	
	private void handGwState(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("GWDemoStat");
		
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

		String sqlstate = String.format("select count(*) as RowCount from gws where GwID=%d", GwID);
		ResultSet ret   = dbm.query(sqlstate);
		
		if (ret==null)
			return;
		
		int count = 0;
		try {
			ret.next();
			count = ret.getInt("RowCount");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (count>0) {
			sqlstate = String.format("delete from gws where GwID=%d", GwID);
			dbm.query(sqlstate);
		}

		sqlstate = String.format("insert into gws values (%d, \"%s\", %f, %f, %d, %d, %d, %d, "
				                 + "%d, %d, %d);", GwID, Time, Lati, Long, Alti, RxNb, RxOK, RxFw, AckR, DwNb, 
				                 TxNb
				                );
		dbm.query(sqlstate);
		
		sqlstate = String.format("select count(*) as RowCount from gwupt where GwID=%d", GwID);
		ret      = dbm.query(sqlstate);
		
		if (ret==null)
			return;
		
		count = 0;
		try {
			ret.next();
			count = ret.getInt("RowCount");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (count>0) {
			sqlstate = String.format("update gwupt set Upt=1 where GwID=%d", GwID);
		}
		else {
			sqlstate = String.format("insert into gwupt values (%d, 1)", GwID);
		}
		
		dbm.query(sqlstate);
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
			
			try {
				dlqueue.put(dlmsg);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			System.out.println(String.format("ns %d connect", msg.id));
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
		
		if (ret==null)
			return;

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
			
			dlqueue.put(dlmsg);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void handGwReq(NsUlMsg msg) {
		JsonObject data   = msg.jmsg.getAsJsonObject("GwInfoReq");
		long   GwID       = data.get("GwID").getAsLong();
		NsDlMsg dlmsg     = new NsDlMsg();
		
		dlmsg.id          = msg.id;
		dlmsg.version     = msg.version;
		dlmsg.type        = 9;
		
		JsonObject jmsg   = new JsonObject();
		JsonObject jdata  = new JsonObject();
		
		String sqlstate   = String.format("select * from gwcfgs where GwID=%d", GwID);
		ResultSet ret     = dbm.query(sqlstate);
		if (ret==null)
			return;

		try {
			ret.next();
			
			long TxPower = ret.getLong("TxPower");
			
			jdata.addProperty("TxPower"   , TxPower);
			jmsg.add         ("GwInfoResp", jdata  );
			
			dlmsg.jmsg   = jmsg;
			dlqueue.put(dlmsg);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void handDevPush(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("MoteInfoPushAck");
	
		long   DevEUI   = data.get("DevEUI").getAsLong();
		long   ACK      = data.get("ACK"   ).getAsLong();
	
		ArrayList<WebNsReq> list = (ArrayList<WebNsReq>) webnsreqlist.clone();
		Iterator<WebNsReq>  itor = list.iterator();
	
		while(itor.hasNext()) {
			WebNsReq req = itor.next();
	
			if (req.dlmsg.id!=msg.id 
			 ||!req.dlmsg.jmsg.has("MoteInfoPush")
			 || req.state!=WebNsReq.STATE_SEND) {
				continue;
			}
	
			JsonObject obj  = (JsonObject) req.dlmsg.jmsg.get("MoteInfoPush");
			long       id   = obj.get("DevEUI").getAsLong();
	
			if (id!=DevEUI) {
				continue;
			}
	
			if (ACK!=0) {
				req.state = WebNsReq.STATE_FAIL;
				continue;
			}
	
			req.state = WebNsReq.STATE_ACK;
		}
	}
	
	private void handGwPush(NsUlMsg msg) {
		JsonObject data = msg.jmsg.getAsJsonObject("GwInfoPushAck");
		
		long   GwID     = data.get("GwID").getAsLong();
		long   ACK      = data.get("ACK" ).getAsLong();

		ArrayList<WebNsReq> list = (ArrayList<WebNsReq>) webnsreqlist.clone();
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
			
			if (id!=GwID) {
				continue;
			}
			
			if (ACK!=0) {
				req.state = WebNsReq.STATE_FAIL;
				continue;
			}
			
			req.state = WebNsReq.STATE_ACK;
		}
	}

	public void run() {
		// TODO Auto-generated method stub
		while(true) {
			if (ulqueue.isEmpty()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}

			NsUlMsg msg = (NsUlMsg) ulqueue.poll();

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
			}
		}
	}
}
