import java.net.Socket;

public class WebNsReq {
	public long    time    = 0;
	public long    timeout = 0;
	public int     state   = 0;
	public NsDlMsg dlmsg   = null;
	public Socket  sock    = null;
	public boolean confirm = false;
	
	public WebNsReq() {
		// TODO Auto-generated constructor stub
		time = Common.getTime();
	}

	static final public int STATE_WAIT = 0;
	static final public int STATE_SEND = 1;
	static final public int STATE_ACK  = 2;
	static final public int STATE_FAIL = 3;
	static final public int STATE_TMOT = 4;
}