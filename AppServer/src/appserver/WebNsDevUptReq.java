package appserver;
import java.net.Socket;
import java.util.*;

import common.Common;

public class WebNsDevUptReq {
	public Socket           sock    = null;
	public String           SessId  = null; // 用户号
	public String           UserId  = "";
	public long             time    = 0;
	public long             timeout = 0;
	public boolean          update  = false;
	public long             type    = 0;
	public int              count   = 0;
	
	private static List<WebNsDevUptReq> list = new ArrayList<>();

	public synchronized boolean isEmpty() {
		return list.isEmpty();
	}
	
	public synchronized void add(WebNsDevUptReq req) {
		list.add(req);
	}
	
	public synchronized List<WebNsDevUptReq> get() {
		return new ArrayList<>(list);
	}
	
	public synchronized void remove(WebNsDevUptReq req) {
		list.remove(req);
	}

	public WebNsDevUptReq() {
		time    = Common.getTime();
	}
}
