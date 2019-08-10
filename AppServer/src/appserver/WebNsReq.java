package appserver;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import common.Common;

public class WebNsReq {
	public long    time    = 0;
	public long    timeout = 0;
	public int     state   = 0;
	public NsDlMsg dlmsg   = null;
	public Socket  sock    = null;
	public boolean confirm = false;
	private static List<WebNsReq> reqlist = new ArrayList<>();

	public WebNsReq() {
		time = Common.getTime();
	}

	public synchronized void addWebNsReq(WebNsReq req) {
		reqlist.add(req);
	}

	public synchronized void delWebNsReq(WebNsReq req) {
		reqlist.remove(req);
	}

	public synchronized List<WebNsReq> getWebNsReq() {
		return new ArrayList<>(reqlist);
	}

	public synchronized boolean isEmpty() {
		return reqlist.isEmpty();
	}

	static final public int STATE_WAIT = 0;
	static final public int STATE_SEND = 1;
	static final public int STATE_ACK  = 2;
	static final public int STATE_FAIL = 3;
	static final public int STATE_TMOT = 4;
}

