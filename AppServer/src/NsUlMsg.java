import java.net.Socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class NsUlMsg {
	public long       id;
	public int        version;
	public int        type;
	public Socket     sock;
	public JsonObject jmsg;
	
	public NsUlMsg(Socket s) {
		// TODO Auto-generated constructor stub
		sock = s;
	}

    boolean Load(byte[] bmsg) {
    	if (bmsg.length<=9)
    		return false;

		String start = new String(bmsg).substring(0, 5);
	    if (!start.equals("start")) {
	    	return false;
	    }
	    
	    version = bmsg[5];
	    id      = Common.b2int64(Common.subbytes(bmsg, 6, 14));
	    type    = Common.b2int16(Common.subbytes(bmsg, 14, 16));
	    int len = Common.b2int16(Common.subbytes(bmsg, 16, 18));
	    
	    if (len+18>bmsg.length)
	    	return false;

	    String str = new String(Common.subbytes(bmsg, 18, 18+len));
	    JsonParser parser = new JsonParser();

	    jmsg = (JsonObject) parser.parse(str);

	    return true;
	}
}
