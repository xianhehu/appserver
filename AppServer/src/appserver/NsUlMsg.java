package appserver;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import common.Common;


public class NsUlMsg {
	public long       id;
	public int        version;
	public int        type;
	public Socket     sock;
	public JsonObject jmsg;
	static private BlockingQueue<NsUlMsg> ulqueue = new LinkedBlockingQueue<>();
	
	public NsUlMsg() {
		// TODO Auto-generated constructor stub
	}
	
	public synchronized void put(NsUlMsg m) {
		ulqueue.add(m);
	}
	
	public synchronized boolean isEmpty() {
		return ulqueue.isEmpty();
	}
	
	public synchronized NsUlMsg poll() {
		return ulqueue.poll();
	}
	
	public NsUlMsg(Socket s) {
		// TODO Auto-generated constructor stub
		sock = s;
	}

	public static List<NsUlMsg> Load(byte[] bmsg, int length, Socket s) {
		int len = 0;
		List<NsUlMsg> ulmsgs = new ArrayList<NsUlMsg>();
		
		while(len < length) {
	    	if (length <= 9 + len) {
	    		Main.logger.error("上行消息长度错误:"+(length-len)+"\n");
	    		break;
	    	}
	
			String start = new String(bmsg).substring(len, 5+len);
		    if (!start.equals("start")) {
		    	Main.logger.error("上行消息头错误:"+start+"\n");
		    	break;
		    }
		    
		    NsUlMsg ulmsg = new NsUlMsg();
		    ulmsg.sock    = s;
		    ulmsg.version = bmsg[len+5];
		    ulmsg.id      = Common.b2int64(Common.subbytes(bmsg, 6+len , 14+len));
		    ulmsg.type    = Common.b2int16(Common.subbytes(bmsg, 14+len, 16+len));
		    int l         = Common.b2int16(Common.subbytes(bmsg, 16+len, 18+len));
		    
		    if (l+18+len > length) {
		    	Main.logger.error("上行消息长度错误:"+l+","+(length-len)+"\n");
		    	break;
		    }
	
		    String str = new String(Common.subbytes(bmsg, 18+len, 18+l+len));
		    JsonParser parser = new JsonParser();
		    
		    ulmsg.jmsg = (JsonObject) parser.parse(str);
		    Main.logger.debug("收到NS上行:"+str);
		    
		    len += l + 18;
		    ulmsgs.add(ulmsg);
		}
	    
	    return ulmsgs;
	}
}
