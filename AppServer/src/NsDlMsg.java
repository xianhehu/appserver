import com.google.gson.JsonObject;

public class NsDlMsg {
	public long       id      = 0;
	public int        version = 1;
	public int        type    = 0;
	public JsonObject jmsg    = null;

	public byte[] getBytes() {
		int i = 0;
		byte[] buf = new byte[jmsg.toString().length()+18];

		buf[i++] = 's';
		buf[i++] = 't';
		buf[i++] = 'a';
		buf[i++] = 'r';
		buf[i++] = 't';
		buf[i++] = (byte) version;
		
		buf[i++] = (byte) (id>>56);
		buf[i++] = (byte) (id>>48);
		buf[i++] = (byte) (id>>40);
		buf[i++] = (byte) (id>>32);
		buf[i++] = (byte) (id>>24);
		buf[i++] = (byte) (id>>16);
		buf[i++] = (byte) (id>> 8);
		buf[i++] = (byte) (id>> 0);

		buf[i++] = (byte) (type>>8);
		buf[i++] = (byte) (type>>0);

		int len = jmsg.toString().length();
		buf[i++] = (byte) (len>>8);
		buf[i++] = (byte) (len>>0);

		System.arraycopy(jmsg.toString().getBytes(), 0, buf, i, len);

		return buf;
	}
}

