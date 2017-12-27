import java.net.Socket;
import java.util.ArrayList;

public class WebNsDevUptReq {
	public Socket           sock    = null;
	public String           SessId  = null; // ”√ªß∫≈
	public long             time    = 0;
	public long             timeout = 0;
	public boolean          update  = false;
	public long             type    = 0;

	public WebNsDevUptReq() {
		// TODO Auto-generated constructor stub
		time    = Common.getTime();
	}
}
